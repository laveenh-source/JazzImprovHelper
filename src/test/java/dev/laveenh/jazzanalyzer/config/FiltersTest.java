package dev.laveenh.jazzanalyzer.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FiltersTest {

    private static JazzProperties limits(int uploads, int requests) {
        return new JazzProperties(new JazzProperties.Upload(1), new JazzProperties.Storage(Path.of("x"), null), null,
                new JazzProperties.RateLimit(uploads, requests));
    }

    private static MockHttpServletRequest request(String method, String uri, String ip) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
        r.setRemoteAddr(ip);
        return r;
    }

    private static int send(RateLimitFilter filter, MockHttpServletRequest request, MockHttpServletResponse response)
            throws ServletException, IOException {
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    // ------------------------------------------------------------ rate limiting

    @Test
    void allowsRequestsUpToTheLimitThenAnswers429() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(limits(2, 100), Clock.systemUTC());

        assertThat(send(filter, request("POST", "/api/v1/songs", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(200);
        assertThat(send(filter, request("POST", "/api/v1/songs", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(200);

        MockHttpServletResponse refused = new MockHttpServletResponse();
        assertThat(send(filter, request("POST", "/api/v1/songs", "1.1.1.1"), refused)).isEqualTo(429);
        assertThat(refused.getHeader("Retry-After")).matches("\\d+");
        assertThat(Integer.parseInt(refused.getHeader("Retry-After"))).isBetween(1, 61);
        assertThat(refused.getContentType()).startsWith("application/json");
        assertThat(refused.getContentAsString())
                .contains("\"status\":429", "\"error\":\"Too Many Requests\"", "\"path\":\"/api/v1/songs\"", "\"timestamp\"", "\"message\"");
    }

    @Test
    void eachAddressHasItsOwnBucket() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(limits(1, 100), Clock.systemUTC());
        assertThat(send(filter, request("POST", "/api/v1/songs", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(200);
        assertThat(send(filter, request("POST", "/api/v1/songs", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(429);
        assertThat(send(filter, request("POST", "/api/v1/songs", "2.2.2.2"), new MockHttpServletResponse())).isEqualTo(200);
    }

    @Test
    void uploadsAndReadsHaveSeparateBuckets() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(limits(1, 3), Clock.systemUTC());
        assertThat(send(filter, request("POST", "/api/v1/songs", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(200);
        assertThat(send(filter, request("POST", "/api/v1/songs", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(429);
        // uploads are used up, reads are not
        for (int i = 0; i < 3; i++) {
            assertThat(send(filter, request("GET", "/api/v1/songs", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(200);
        }
        assertThat(send(filter, request("GET", "/api/v1/songs", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(429);
    }

    @Test
    void healthChecksAndDocsAreNeverLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(limits(1, 1), Clock.systemUTC());
        for (int i = 0; i < 5; i++) {
            assertThat(send(filter, request("GET", "/actuator/health", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(200);
            assertThat(send(filter, request("GET", "/swagger-ui/index.html", "1.1.1.1"), new MockHttpServletResponse())).isEqualTo(200);
        }
    }

    @Test
    void reportsHowManyRequestsRemain() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(limits(10, 5), Clock.systemUTC());
        MockHttpServletResponse response = new MockHttpServletResponse();
        send(filter, request("GET", "/api/v1/songs", "1.1.1.1"), response);
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("4");
    }

    @Test
    void theJsonErrorEscapesTheRequestPath() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(limits(1, 1), Clock.systemUTC());
        send(filter, request("GET", "/api/v1/x\"y", "1.1.1.1"), new MockHttpServletResponse());
        MockHttpServletResponse refused = new MockHttpServletResponse();
        send(filter, request("GET", "/api/v1/x\"y", "1.1.1.1"), refused);
        assertThat(refused.getContentAsString()).contains("\"path\":\"/api/v1/x\\\"y\"");
    }

    @Test
    void defaultsAreSensibleWhenNothingIsConfigured() {
        JazzProperties p = new JazzProperties(new JazzProperties.Upload(1), new JazzProperties.Storage(Path.of("x"), null));
        assertThat(p.rateLimit().uploadsPerMinute()).isEqualTo(10);
        assertThat(p.rateLimit().requestsPerMinute()).isEqualTo(120);
        assertThat(p.analysis().mode()).isEqualTo("async");
        assertThat(p.analysis().threads()).isEqualTo(2);
        assertThat(p.analysis().queueCapacity()).isEqualTo(20);
        assertThat(new JazzProperties.Analysis("  ", -1, 0).mode()).isEqualTo("async");
    }

    // ------------------------------------------------------------ request ids

    @Test
    void generatesARequestIdAndPutsItInTheLogContextOnlyDuringTheRequest() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInside = new AtomicReference<>();

        filter.doFilter(request("GET", "/api/v1/songs", "1.1.1.1"), response, (req, res) -> seenInside.set(MDC.get("requestId")));

        assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
        assertThat(seenInside.get()).isEqualTo(response.getHeader("X-Request-Id"));
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void keepsASafeSuppliedIdAndReplacesAnUnsafeOne() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();

        MockHttpServletRequest good = request("GET", "/x", "1.1.1.1");
        good.addHeader("X-Request-Id", "trace-123.abc_DEF");
        MockHttpServletResponse goodResponse = new MockHttpServletResponse();
        filter.doFilter(good, goodResponse, new MockFilterChain());
        assertThat(goodResponse.getHeader("X-Request-Id")).isEqualTo("trace-123.abc_DEF");

        for (String evil : new String[]{"a\nb", "x".repeat(65), "has space", "<script>", ""}) {
            MockHttpServletRequest bad = request("GET", "/x", "1.1.1.1");
            bad.addHeader("X-Request-Id", evil);
            MockHttpServletResponse badResponse = new MockHttpServletResponse();
            filter.doFilter(bad, badResponse, new MockFilterChain());
            assertThat(badResponse.getHeader("X-Request-Id")).as(evil).matches("[0-9a-f-]{36}");
        }
    }
}
