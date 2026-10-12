package dev.laveenh.jazzanalyzer.api;

import com.jayway.jsonpath.JsonPath;
import dev.laveenh.jazzanalyzer.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SearchAndBatchApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void clean() {
        clearDatabase();
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = SearchAndBatchApiIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
            return in.readAllBytes();
        }
    }

    private ResultActions upload(String filename, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/songs").file(new MockMultipartFile("file", filename, "application/xml", content)));
    }

    private String uploadFixture(String fixtureName) throws Exception {
        return JsonPath.read(upload(fixtureName, fixture(fixtureName)).andReturn().getResponse().getContentAsString(), "$.songId");
    }

    // ------------------------------------------------------------ search

    @Test
    void findsAProgressionInAGivenKeyThroughTheApi() throws Exception {
        String songId = uploadFixture("modulation.xml");

        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-I&key=Eb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].songId").value(songId))
                .andExpect(jsonPath("$.content[0].title").value("Modulating tune"))
                .andExpect(jsonPath("$.content[0].key").value("Eb"))
                .andExpect(jsonPath("$.content[0].startMeasure").value(5))
                .andExpect(jsonPath("$.content[0].endMeasure").value(7))
                .andExpect(jsonPath("$.content[0].chords").value("Fm7 Bb7 Ebmaj7"));
    }

    @Test
    void withoutAKeyItFindsEveryOccurrence() throws Exception {
        uploadFixture("modulation.xml");
        uploadFixture("ii-v-i-c-major.xml");

        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-I"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].key").value(org.hamcrest.Matchers.containsInAnyOrder("C", "Eb", "C")));
        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-I&key={k}", "C major"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-I&key={k}", "F#"))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    void theMinorPatternFindsTheMinorTune() throws Exception {
        uploadFixture("minor-ii-v-i.xml");
        uploadFixture("ii-v-i-c-major.xml");
        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-i&key=A"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Minor ii-V-i"))
                .andExpect(jsonPath("$.content[0].chords").value("Bm7b5 E7alt Am7"));
    }

    @Test
    void searchPagesLikeTheOtherLists() throws Exception {
        uploadFixture("modulation.xml");
        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-I&size=1&page=1"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void searchRejectsBadInput() throws Exception {
        mvc.perform(get("/api/v1/search/progressions")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search/progressions?pattern=ii-v-i")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Supported patterns")));
        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-I&key=H")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unknown key")));
        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-I&size=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search/progressions?pattern=ii-V-I&page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search/progressions?pattern={p}", "ii-V-I' OR '1'='1")).andExpect(status().isBadRequest());
    }

    @Test
    void statisticsSummariseTheLibrary() throws Exception {
        uploadFixture("ii-v-i-c-major.xml");
        uploadFixture("minor-ii-v-i.xml");

        mvc.perform(get("/api/v1/stats?limit=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chordQualities[0].chords").isNumber())
                .andExpect(jsonPath("$.chordQualities[0].percent").isNumber())
                .andExpect(jsonPath("$.commonTransitions[0].occurrences").isNumber());
        mvc.perform(get("/api/v1/stats?limit=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/stats?limit=101")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ batch upload

    private ResultActions batch(MockMultipartFile... files) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/songs/batch");
        for (MockMultipartFile f : files) {
            request.file(f);
        }
        return mvc.perform(request);
    }

    private static MockMultipartFile part(String filename, byte[] content) {
        return new MockMultipartFile("files", filename, "application/xml", content);
    }

    @Test
    void batchUploadHandlesEachFileOnItsOwn() throws Exception {
        batch(part("good.xml", fixture("ii-v-i-c-major.xml")),
              part("also-good.xml", fixture("minor-ii-v-i.xml")),
              part("notes.pdf", fixture("ii-v-i-c-major.xml")),
              part("broken.xml", fixture("malformed.xml")),
              part("copy.xml", fixture("ii-v-i-c-major.xml")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].filename").value("good.xml"))
                .andExpect(jsonPath("$[0].analysisId").exists())
                .andExpect(jsonPath("$[0].status").value("COMPLETE"))
                .andExpect(jsonPath("$[0].duplicate").value(false))
                .andExpect(jsonPath("$[1].analysisId").exists())
                .andExpect(jsonPath("$[2].error", containsString(".xml and .musicxml")))
                .andExpect(jsonPath("$[2].analysisId").doesNotExist())
                .andExpect(jsonPath("$[3].error", containsString("not well-formed")))
                .andExpect(jsonPath("$[4].duplicate").value(true));   // same bytes as the first file

        assertThat(jdbc.queryForObject("SELECT count(*) FROM songs", Long.class)).isEqualTo(2);
    }

    @Test
    void batchRejectsTooManyOrNoFiles() throws Exception {
        MockMultipartFile[] eleven = new MockMultipartFile[11];
        for (int i = 0; i < 11; i++) {
            eleven[i] = part("f" + i + ".xml", fixture("ii-v-i-c-major.xml"));
        }
        batch(eleven).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("between 1 and 10")));
        mvc.perform(multipart("/api/v1/songs/batch")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ caching and headers

    @Test
    void aFinishedAnalysisIsServedFromTheCacheUntilItsSongIsDeleted() throws Exception {
        String body = upload("tune.xml", fixture("ii-v-i-c-major.xml")).andReturn().getResponse().getContentAsString();
        String analysisId = JsonPath.read(body, "$.analysisId");
        String songId = JsonPath.read(body, "$.songId");

        mvc.perform(get("/api/v1/analyses/" + analysisId)).andExpect(status().isOk());   // fills the cache

        // wipe the rows behind the application's back: only the cache can still answer
        jdbc.execute("TRUNCATE songs CASCADE");
        mvc.perform(get("/api/v1/analyses/" + analysisId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.chords", hasSize(3)));
    }

    @Test
    void deletingASongEvictsItsCachedAnalysis() throws Exception {
        String body = upload("tune.xml", fixture("ii-v-i-c-major.xml")).andReturn().getResponse().getContentAsString();
        String analysisId = JsonPath.read(body, "$.analysisId");
        String songId = JsonPath.read(body, "$.songId");
        mvc.perform(get("/api/v1/analyses/" + analysisId)).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/songs/" + songId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/analyses/" + analysisId)).andExpect(status().isNotFound());
    }

    @Test
    void failedAnalysesAreNotCached() throws Exception {
        String body = upload("melody.xml", fixture("no-harmony.xml")).andReturn().getResponse().getContentAsString();
        String analysisId = JsonPath.read(body, "$.analysisId");
        mvc.perform(get("/api/v1/analyses/" + analysisId)).andExpect(jsonPath("$.status").value("FAILED"));
        jdbc.update("UPDATE analyses SET error_message = 'changed' WHERE id = ?::uuid", analysisId);
        mvc.perform(get("/api/v1/analyses/" + analysisId)).andExpect(jsonPath("$.errorMessage").value("changed"));
    }

    @Test
    void everyResponseCarriesARequestIdAndTheRateLimitBalance() throws Exception {
        mvc.perform(get("/api/v1/songs"))
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")))
                .andExpect(header().exists("X-RateLimit-Remaining"));
        mvc.perform(get("/api/v1/songs").header("X-Request-Id", "my-trace-1"))
                .andExpect(header().string("X-Request-Id", "my-trace-1"));
        mvc.perform(get("/api/v1/songs/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"));
    }
}
