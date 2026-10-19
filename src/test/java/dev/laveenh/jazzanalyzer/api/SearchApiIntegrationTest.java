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

import java.io.IOException;
import java.io.InputStream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SearchApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;

    @BeforeEach
    void clean() {
        clearDatabase();
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = SearchApiIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
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
}
