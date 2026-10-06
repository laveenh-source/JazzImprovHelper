package dev.laveenh.jazzanalyzer.api;

import com.jayway.jsonpath.JsonPath;
import dev.laveenh.jazzanalyzer.PostgresIntegrationTest;
import dev.laveenh.jazzanalyzer.persistence.SongRepository;
import dev.laveenh.jazzanalyzer.storage.FileStorage;
import dev.laveenh.jazzanalyzer.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole REST API against a real PostgreSQL, through MockMvc. */
@AutoConfigureMockMvc
class SongApiIntegrationTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired SongRepository songs;
    @Autowired FileStorage storage;

    @BeforeEach
    void clean() {
        clearDatabase();
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = SongApiIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
            return in.readAllBytes();
        }
    }

    private ResultActions upload(String filename, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/songs").file(new MockMultipartFile("file", filename, "application/xml", content)));
    }

    private String uploadFixture(String fixtureName) throws Exception {
        MvcResult result = upload(fixtureName, fixture(fixtureName)).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.songId");
    }

    // ------------------------------------------------------------ POST /songs

    @Test
    void uploadReturns202WithTheAnalysisToPoll() throws Exception {
        upload("ii-v-i.xml", fixture("ii-v-i-c-major.xml"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.songId", notNullValue()))
                .andExpect(jsonPath("$.analysisId", notNullValue()))
                .andExpect(jsonPath("$.status").value("COMPLETE"))   // analysis runs inline until Phase 4
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(header().string("Location", containsString("/api/v1/analyses/")));
    }

    @Test
    void theAnalysisJsonHasTheDocumentedShape() throws Exception {
        String body = upload("ii-v-i.xml", fixture("ii-v-i-c-major.xml")).andReturn().getResponse().getContentAsString();
        String analysisId = JsonPath.read(body, "$.analysisId");

        mvc.perform(get("/api/v1/analyses/" + analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisId").value(analysisId))
                .andExpect(jsonPath("$.status").value("COMPLETE"))
                .andExpect(jsonPath("$.title").value("ii-V-I in C"))
                .andExpect(jsonPath("$.detectedKey").value("C major"))
                .andExpect(jsonPath("$.keyRegions", hasSize(1)))
                .andExpect(jsonPath("$.keyRegions[0].key").value("C major"))
                .andExpect(jsonPath("$.keyRegions[0].startMeasure").value(1))
                .andExpect(jsonPath("$.keyRegions[0].endMeasure").value(3))
                .andExpect(jsonPath("$.keyRegions[0].reason").value("ii-V-I in measures 1-3"))
                .andExpect(jsonPath("$.chords", hasSize(3)))
                .andExpect(jsonPath("$.chords[0].measure").value(1))
                .andExpect(jsonPath("$.chords[0].beat").value(1.0))
                .andExpect(jsonPath("$.chords[0].symbol").value("Dm7"))
                .andExpect(jsonPath("$.chords[0].quality").value("MINOR7"))
                .andExpect(jsonPath("$.chords[0].roman").value("ii7"))
                .andExpect(jsonPath("$.chords[0].function").value("subdominant"))
                .andExpect(jsonPath("$.chords[0].localKey").value("C major"))
                .andExpect(jsonPath("$.chords[0].scales[0].rank").value(1))
                .andExpect(jsonPath("$.chords[0].scales[0].name").value("D Dorian"))
                .andExpect(jsonPath("$.chords[0].scales[0].notes[1]").value("E"))
                .andExpect(jsonPath("$.chords[1].scales[1].name").value("G Altered"))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());
    }

    @Test
    void uploadingTheSameFileAgainReturnsTheExistingAnalysis() throws Exception {
        byte[] content = fixture("ii-v-i-c-major.xml");
        String first = upload("first.xml", content).andReturn().getResponse().getContentAsString();

        // same bytes under a different name: still the same song
        upload("renamed.xml", content)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true))
                .andExpect(jsonPath("$.analysisId").value((String) JsonPath.read(first, "$.analysisId")))
                .andExpect(jsonPath("$.songId").value((String) JsonPath.read(first, "$.songId")));

        assertThat(songs.count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analyses", Long.class)).isEqualTo(1);
    }

    @Test
    void aDifferentFileIsADifferentSong() throws Exception {
        uploadFixture("ii-v-i-c-major.xml");
        uploadFixture("minor-ii-v-i.xml");
        assertThat(songs.count()).isEqualTo(2);
    }

    @Test
    void aFileWithoutChordsIsAcceptedThenFailsWithAMessage() throws Exception {
        String body = upload("melody.xml", fixture("no-harmony.xml")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED")).andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/v1/analyses/" + JsonPath.<String>read(body, "$.analysisId")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.errorMessage", containsString("no chord symbols")))
                .andExpect(jsonPath("$.chords").doesNotExist())
                .andExpect(jsonPath("$.keyRegions").doesNotExist());
    }

    @Test
    void uploadingAFailedFileAgainRetriesWithANewAnalysis() throws Exception {
        byte[] content = fixture("no-harmony.xml");
        String first = upload("melody.xml", content).andReturn().getResponse().getContentAsString();
        String second = upload("melody.xml", content).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(second, "$.songId")).isEqualTo(JsonPath.<String>read(first, "$.songId"));
        assertThat(JsonPath.<String>read(second, "$.analysisId")).isNotEqualTo(JsonPath.<String>read(first, "$.analysisId"));
        assertThat(songs.count()).isEqualTo(1);
    }

    // ------------------------------------------------------------ upload validation

    @Test
    void rejectsAnEmptyFile() throws Exception {
        upload("tune.xml", new byte[0]).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("empty")));
    }

    @Test
    void rejectsAWrongExtensionWith415() throws Exception {
        upload("tune.pdf", fixture("ii-v-i-c-major.xml")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        upload("tune.mxl", fixture("ii-v-i-c-major.xml")).andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.message", containsString(".mxl")));
    }

    @Test
    void rejectsAFileOverTwoMegabytesWith413() throws Exception {
        byte[] big = ("<score-partwise>" + "x".repeat(2 * 1024 * 1024) + "</score-partwise>").getBytes(StandardCharsets.UTF_8);
        upload("big.xml", big).andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413));
        assertThat(songs.count()).isZero();
    }

    @Test
    void rejectsXmlThatIsNotWellFormedAndStoresNothing() throws Exception {
        upload("broken.xml", fixture("malformed.xml")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not well-formed")));
        assertThat(songs.count()).isZero();
    }

    @Test
    void rejectsARequestWithoutAFilePart() throws Exception {
        mvc.perform(multipart("/api/v1/songs")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsANonMultipartPost() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/songs")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void anAttackerFileNameNeverReachesTheDiskOrTheResponseAsAPath() throws Exception {
        String songId = JsonPath.read(upload("../../evil.xml", fixture("ii-v-i-c-major.xml")).andReturn()
                .getResponse().getContentAsString(), "$.songId");

        mvc.perform(get("/api/v1/songs/" + songId)).andExpect(jsonPath("$.originalFilename").value("evil.xml"));
        String key = jdbc.queryForObject("SELECT storage_key FROM songs", String.class);
        assertThat(key).matches("[0-9a-f-]{36}\\.xml");
        assertThat(Files.exists(STORAGE_DIR.resolve(key))).isTrue();
    }

    @Test
    void anXxePayloadCannotReadLocalFiles() throws Exception {
        Path secret = Files.createTempFile("secret", ".txt");
        Files.writeString(secret, "TOP-SECRET-CONTENTS");
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE score-partwise [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>"
                + "<score-partwise><work><work-title>&xxe;</work-title></work><part id=\"P1\"><measure number=\"1\">"
                + "<harmony><root><root-step>C</root-step></root><kind>major</kind></harmony></measure></part></score-partwise>";

        MvcResult result = upload("xxe.xml", xxe.getBytes(StandardCharsets.UTF_8)).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("TOP-SECRET-CONTENTS");
        if (result.getResponse().getStatus() == 202) {
            String analysisId = JsonPath.read(result.getResponse().getContentAsString(), "$.analysisId");
            mvc.perform(get("/api/v1/analyses/" + analysisId))
                    .andExpect(jsonPath("$.title", not(containsString("TOP-SECRET-CONTENTS"))));
        }
        Files.deleteIfExists(secret);
    }

    // ------------------------------------------------------------ GET /analyses and /songs/{id}

    @Test
    void getSongShowsMetadataAndTheLatestAnalysisSummary() throws Exception {
        String songId = uploadFixture("modulation.xml");

        mvc.perform(get("/api/v1/songs/" + songId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(songId))
                .andExpect(jsonPath("$.title").value("Modulating tune"))
                .andExpect(jsonPath("$.originalFilename").value("modulation.xml"))
                .andExpect(jsonPath("$.latestAnalysis.status").value("COMPLETE"))
                .andExpect(jsonPath("$.latestAnalysis.detectedKey").value("C major"))
                .andExpect(jsonPath("$.latestAnalysis.chordCount").value(8))
                .andExpect(jsonPath("$.latestAnalysis.keyRegions", hasSize(2)));
    }

    @Test
    void getSongOfAFailedAnalysisShowsTheErrorButNoChordCount() throws Exception {
        String songId = uploadFixture("no-harmony.xml");
        mvc.perform(get("/api/v1/songs/" + songId))
                .andExpect(jsonPath("$.latestAnalysis.status").value("FAILED"))
                .andExpect(jsonPath("$.latestAnalysis.errorMessage", containsString("no chord symbols")))
                .andExpect(jsonPath("$.latestAnalysis.chordCount").doesNotExist());
    }

    @Test
    void chordsEndpointListsEveryChordWithItsScales() throws Exception {
        String songId = uploadFixture("minor-ii-v-i.xml");

        mvc.perform(get("/api/v1/songs/" + songId + "/chords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.songId").value(songId))
                .andExpect(jsonPath("$.detectedKey").value("A minor"))
                .andExpect(jsonPath("$.chords", hasSize(3)))
                .andExpect(jsonPath("$.chords[0].roman").value("iiø7"))
                .andExpect(jsonPath("$.chords[0].scales[0].name").value("B Locrian #2"))
                .andExpect(jsonPath("$.chords[1].roman").value("V7alt"))
                .andExpect(jsonPath("$.chords[1].scales[0].name").value("E Altered"))
                .andExpect(jsonPath("$.chords[2].roman").value("i7"));
    }

    @Test
    void chordsEndpointReturns409WhenThereIsNothingToShow() throws Exception {
        String songId = uploadFixture("no-harmony.xml");
        mvc.perform(get("/api/v1/songs/" + songId + "/chords"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("FAILED")));
    }

    // ------------------------------------------------------------ GET /songs

    @Test
    void listsSongsWithPagination() throws Exception {
        uploadFixture("ii-v-i-c-major.xml");
        uploadFixture("minor-ii-v-i.xml");
        uploadFixture("modulation.xml");

        mvc.perform(get("/api/v1/songs?size=2&page=0&sort=title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("Minor ii-V-i"))
                .andExpect(jsonPath("$.content[1].title").value("Modulating tune"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mvc.perform(get("/api/v1/songs?size=2&page=1&sort=title,asc"))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("ii-V-I in C"));
    }

    @Test
    void listFiltersByKey() throws Exception {
        uploadFixture("ii-v-i-c-major.xml");
        uploadFixture("minor-ii-v-i.xml");
        uploadFixture("modulation.xml");

        mvc.perform(get("/api/v1/songs?key=C major"))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/v1/songs?key=a minor"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Minor ii-V-i"))
                .andExpect(jsonPath("$.content[0].detectedKey").value("A minor"))
                .andExpect(jsonPath("$.content[0].latestStatus").value("COMPLETE"));
        mvc.perform(get("/api/v1/songs?key=A"))
                .andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/songs?key=F# major"))
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void listDefaultsToNewestFirst() throws Exception {
        uploadFixture("ii-v-i-c-major.xml");
        Thread.sleep(5);
        uploadFixture("minor-ii-v-i.xml");
        mvc.perform(get("/api/v1/songs"))
                .andExpect(jsonPath("$.content[0].title").value("Minor ii-V-i"))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void listRejectsBadParameters() throws Exception {
        mvc.perform(get("/api/v1/songs?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/songs?size=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/songs?size=101")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/songs?size=abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/songs?sort=password")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot sort by")));
        mvc.perform(get("/api/v1/songs?sort=title,sideways")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/songs?sort=title;DROP TABLE songs")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ DELETE

    @Test
    void deleteRemovesTheSongItsAnalysesAndTheStoredFile() throws Exception {
        String songId = uploadFixture("ii-v-i-c-major.xml");
        String storageKey = jdbc.queryForObject("SELECT storage_key FROM songs", String.class);
        assertThat(storage.read(storageKey)).isNotEmpty();

        mvc.perform(delete("/api/v1/songs/" + songId)).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/songs/" + songId)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chords", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analyses", Long.class)).isZero();
        assertThatThrownBy(() -> storage.read(storageKey)).isInstanceOf(StorageException.class);
    }

    @Test
    void deletedSongCanBeUploadedAgain() throws Exception {
        String songId = uploadFixture("ii-v-i-c-major.xml");
        mvc.perform(delete("/api/v1/songs/" + songId)).andExpect(status().isNoContent());
        upload("again.xml", fixture("ii-v-i-c-major.xml")).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    void deletingAnUnknownSongIs404() throws Exception {
        mvc.perform(delete("/api/v1/songs/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ errors use one format

    @Test
    void unknownIdsAre404InTheErrorFormat() throws Exception {
        String id = UUID.randomUUID().toString();
        mvc.perform(get("/api/v1/analyses/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message", containsString(id)))
                .andExpect(jsonPath("$.path").value("/api/v1/analyses/" + id));
        mvc.perform(get("/api/v1/songs/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/songs/" + id + "/chords")).andExpect(status().isNotFound());
    }

    @Test
    void aMalformedIdIs400InTheErrorFormat() throws Exception {
        mvc.perform(get("/api/v1/analyses/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("not-a-uuid")))
                .andExpect(jsonPath("$.path").value("/api/v1/analyses/not-a-uuid"));
    }

    @Test
    void wrongMethodsAndUnknownUrlsUseTheSameFormat() throws Exception {
        mvc.perform(put("/api/v1/songs/" + UUID.randomUUID()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.path", notNullValue()));
        mvc.perform(get("/api/v1/nothing-here"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    // ------------------------------------------------------------ platform endpoints

    @Test
    void healthEndpointReportsUp() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void openApiDocsDescribeTheApi() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Jazz Chart Analyzer API"))
                .andExpect(jsonPath("$.paths['/api/v1/songs']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/songs/{id}/chords']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/analyses/{id}']").exists());
    }
}
