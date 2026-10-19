package dev.laveenh.jazzanalyzer.service;

import com.jayway.jsonpath.JsonPath;
import dev.laveenh.jazzanalyzer.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Analyses really run on the background pool. The pool is tiny (1 worker, 1 waiting slot) to fill it easily. */
@SpringBootTest(properties = {"jazz.analysis.mode=async", "jazz.analysis.threads=1", "jazz.analysis.queue-capacity=1"})
@AutoConfigureMockMvc
class AsyncAnalysisIntegrationTest extends PostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired StaleAnalysisRecovery recovery;
    @Autowired ThreadPoolTaskExecutor analysisTaskExecutor;
    @MockitoSpyBean AnalysisProcessor processor;

    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void clean() {
        clearDatabase();
    }

    @AfterEach
    void letEverythingFinish() {
        release.countDown();
        await().atMost(Duration.ofSeconds(20)).until(() ->
                analysisTaskExecutor.getActiveCount() == 0 && analysisTaskExecutor.getThreadPoolExecutor().getQueue().isEmpty());
        Mockito.reset(processor);
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = AsyncAnalysisIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
            return in.readAllBytes();
        }
    }

    private ResultActions upload(String filename, byte[] content) throws Exception {
        return mvc.perform(multipart("/api/v1/songs").file(new MockMultipartFile("file", filename, "application/xml", content)));
    }

    private String statusOf(String analysisId) throws Exception {
        String body = mvc.perform(get("/api/v1/analyses/" + analysisId)).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.status");
    }

    @Test
    void uploadReturnsAtOnceAndTheResultAppearsLater() throws Exception {
        String body = upload("tune.xml", fixture("ii-v-i-c-major.xml")).andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String analysisId = JsonPath.read(body, "$.analysisId");
        assertThat(JsonPath.<String>read(body, "$.status")).isIn("PENDING", "RUNNING", "COMPLETE");

        await().atMost(Duration.ofSeconds(20)).until(() -> statusOf(analysisId).equals("COMPLETE"));

        mvc.perform(get("/api/v1/analyses/" + analysisId))
                .andExpect(jsonPath("$.chords.length()").value(3))
                .andExpect(jsonPath("$.detectedKey").value("C major"));
    }

    @Test
    void aBadFileFailsInTheBackgroundWithoutCrashingTheWorker() throws Exception {
        String bad = upload("melody.xml", fixture("no-harmony.xml")).andReturn().getResponse().getContentAsString();
        String badId = JsonPath.read(bad, "$.analysisId");
        await().atMost(Duration.ofSeconds(20)).until(() -> statusOf(badId).equals("FAILED"));

        // the same worker thread keeps serving later uploads
        String good = upload("tune.xml", fixture("ii-v-i-c-major.xml")).andReturn().getResponse().getContentAsString();
        String goodId = JsonPath.read(good, "$.analysisId");
        await().atMost(Duration.ofSeconds(20)).until(() -> statusOf(goodId).equals("COMPLETE"));
    }

    @Test
    void whenTheQueueIsFullUploadsGet503AndCanBeRetried() throws Exception {
        Mockito.doAnswer(call -> {
            release.await(30, TimeUnit.SECONDS);
            return call.callRealMethod();
        }).when(processor).process(any(UUID.class));

        upload("one.xml", fixture("ii-v-i-c-major.xml")).andExpect(status().isAccepted());   // taken by the only worker
        upload("two.xml", fixture("minor-ii-v-i.xml")).andExpect(status().isAccepted());     // waits in the queue
        byte[] third = fixture("modulation.xml");
        upload("three.xml", third)                                                           // nowhere to go
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message", containsString("busy")));

        // the refused upload left a FAILED analysis that says why
        assertThat(jdbc.queryForObject("SELECT error_message FROM analyses WHERE status = 'FAILED'", String.class))
                .contains("busy");

        // once there is room, uploading the same file again retries it
        release.countDown();
        await().atMost(Duration.ofSeconds(20)).until(() -> analysisTaskExecutor.getActiveCount() == 0
                && analysisTaskExecutor.getThreadPoolExecutor().getQueue().isEmpty());
        String retry = upload("three.xml", third).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String retryId = JsonPath.read(retry, "$.analysisId");
        await().atMost(Duration.ofSeconds(20)).until(() -> statusOf(retryId).equals("COMPLETE"));
    }

    // ------------------------------------------------------------ recovery after a restart

    private UUID analysisWith(String status, int minutesOld) {
        UUID songId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        jdbc.update("INSERT INTO songs (id, title, original_filename, storage_key, checksum_sha256, uploaded_at) VALUES (?, 't', 't.xml', 'k', ?, now())",
                songId, UUID.randomUUID().toString().replace("-", "").repeat(2));
        jdbc.update("INSERT INTO analyses (id, song_id, status, created_at) VALUES (?, ?, ?, now() - make_interval(mins => ?))",
                analysisId, songId, status, minutesOld);
        return analysisId;
    }

    private String statusInDb(UUID id) {
        return jdbc.queryForObject("SELECT status FROM analyses WHERE id = ?", String.class, id);
    }

    @Test
    void stuckAnalysesAreFailedAtStartupButRecentOnesAreLeftAlone() {
        UUID stuckPending = analysisWith("PENDING", 30);
        UUID stuckRunning = analysisWith("RUNNING", 30);
        UUID recent = analysisWith("RUNNING", 1);
        UUID done = analysisWith("COMPLETE", 30);

        assertThat(recovery.recover()).isEqualTo(2);

        assertThat(statusInDb(stuckPending)).isEqualTo("FAILED");
        assertThat(statusInDb(stuckRunning)).isEqualTo("FAILED");
        assertThat(statusInDb(recent)).isEqualTo("RUNNING");
        assertThat(statusInDb(done)).isEqualTo("COMPLETE");
        assertThat(jdbc.queryForObject("SELECT error_message FROM analyses WHERE id = ?", String.class, stuckPending))
                .contains("restart");
        assertThat(recovery.recover()).isZero();
    }
}
