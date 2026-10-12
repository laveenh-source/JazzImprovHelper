package dev.laveenh.jazzanalyzer.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * Per-client-IP rate limiting with the token-bucket algorithm (Bucket4j). Each IP has two buckets:
 * a small one for uploads (the expensive requests) and a larger one for all other API calls.
 * Over the limit the answer is 429 with a Retry-After header, in the same JSON shape as every other error.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    private record Buckets(Bucket uploads, Bucket requests) {
    }

    private final JazzProperties.RateLimit limits;
    private final Clock clock;
    /** Idle clients are forgotten, so the map cannot grow forever. */
    private final Cache<String, Buckets> clients = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public RateLimitFilter(JazzProperties properties, Clock clock) {
        this.limits = properties.rateLimit();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // only the API is limited: health checks and the docs must keep working for the load balancer
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Buckets buckets = clients.get(request.getRemoteAddr(), ip -> new Buckets(
                newBucket(limits.uploadsPerMinute()), newBucket(limits.requestsPerMinute())));

        boolean isUpload = HttpMethod.POST.matches(request.getMethod());
        ConsumptionProbe probe = (isUpload ? buckets.uploads() : buckets.requests()).tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"timestamp\":\"" + clock.instant() + "\",\"status\":429,"
                + "\"error\":\"Too Many Requests\","
                + "\"message\":\"Too many requests from your address. Try again in " + retryAfterSeconds + " seconds.\","
                + "\"path\":\"" + escape(request.getRequestURI()) + "\"}");
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
