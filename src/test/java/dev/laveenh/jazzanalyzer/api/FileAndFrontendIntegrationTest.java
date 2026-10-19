package dev.laveenh.jazzanalyzer.api;

import com.jayway.jsonpath.JsonPath;
import dev.laveenh.jazzanalyzer.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class FileAndFrontendIntegrationTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void clean() {
        clearDatabase();
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = FileAndFrontendIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void theOriginalFileCanBeDownloadedButOnlyAsALockedDownAttachment() throws Exception {
        byte[] original = fixture("ii-v-i-c-major.xml");
        String body = mvc.perform(multipart("/api/v1/songs").file(new MockMultipartFile("file", "my tune.xml", "application/xml", original)))
                .andReturn().getResponse().getContentAsString();
        String songId = JsonPath.read(body, "$.songId");

        var response = mvc.perform(get("/api/v1/songs/" + songId + "/file"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/xml"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("my%20tune.xml")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
                .andReturn().getResponse();
        assertThat(response.getContentAsByteArray()).isEqualTo(original);
    }

    @Test
    void anUnknownSongHasNoFile() throws Exception {
        mvc.perform(get("/api/v1/songs/" + UUID.randomUUID() + "/file")).andExpect(status().isNotFound());
    }

    @Test
    void theWebPageIsServedWithAStrictContentPolicy() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Jazz Chart Analyzer")))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self';")))
                .andExpect(header().string("Content-Security-Policy", not(containsString("cdn"))))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void thePageLoadsItsScriptsFromItself() throws Exception {
        mvc.perform(get("/index.html")).andExpect(content().string(containsString("src=\"app.js\"")))
                .andExpect(content().string(containsString("vendor/opensheetmusicdisplay/opensheetmusicdisplay.min.js")))
                .andExpect(content().string(not(containsString("http://"))))
                .andExpect(content().string(not(containsString("https://cdn"))));
        mvc.perform(get("/app.js")).andExpect(status().isOk());
        mvc.perform(get("/app.css")).andExpect(status().isOk());
        mvc.perform(get("/vendor/opensheetmusicdisplay/opensheetmusicdisplay.min.js")).andExpect(status().isOk());
        mvc.perform(get("/vendor/opensheetmusicdisplay/LICENSE")).andExpect(status().isOk());
    }

    @Test
    void theApiAndTheDocsKeepTheirOwnPolicyNeeds() throws Exception {
        // the API returns JSON and needs no page policy; the Swagger page needs inline scripts, so it gets none
        mvc.perform(get("/api/v1/songs")).andExpect(header().doesNotExist("Content-Security-Policy"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/v3/api-docs")).andExpect(header().doesNotExist("Content-Security-Policy"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(header().doesNotExist("Content-Security-Policy"));
    }

    @Test
    void theScriptNeverBuildsHtmlFromData() throws Exception {
        String script = mvc.perform(get("/app.js")).andReturn().getResponse().getContentAsString();
        assertThat(script).doesNotContain("innerHTML").doesNotContain("outerHTML").doesNotContain("insertAdjacentHTML")
                .doesNotContain("document.write").doesNotContain("eval(");
    }
}
