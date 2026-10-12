package dev.laveenh.jazzanalyzer.storage;

import com.jayway.jsonpath.JsonPath;
import dev.laveenh.jazzanalyzer.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The whole application with the "s3" profile switched on: uploads land in a (fake) S3 bucket, not on disk. */
@AutoConfigureMockMvc
@ActiveProfiles("s3")
class S3ProfileIntegrationTest extends PostgresIntegrationTest {

    @DynamicPropertySource
    static void s3Properties(DynamicPropertyRegistry registry) {
        registry.add("jazz.storage.s3.bucket", () -> S3Mock.BUCKET);
        registry.add("jazz.storage.s3.endpoint", S3Mock::endpoint);
        registry.add("jazz.storage.s3.path-style", () -> "true");
        registry.add("jazz.storage.s3.region", () -> "us-east-1");
        // the SDK's default credential chain finds these, exactly as it would find an ECS task role on AWS
        registry.add("aws.accessKeyId", () -> "test");
        registry.add("aws.secretAccessKey", () -> "test");
    }

    @Autowired MockMvc mvc;
    @Autowired FileStorage storage;
    S3Client s3 = S3Mock.client();

    @BeforeEach
    void clean() {
        clearDatabase();
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    private int objectCount() {
        return s3.listObjectsV2(ListObjectsV2Request.builder().bucket(S3Mock.BUCKET).build()).keyCount();
    }

    @Test
    void theApplicationUsesS3ForStorage() {
        assertThat(storage).isInstanceOf(S3FileStorage.class);
    }

    @Test
    void anUploadIsStoredInTheBucketAnalysedAndRemovedOnDelete() throws Exception {
        byte[] chart;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/ii-v-i-c-major.xml")) {
            chart = in.readAllBytes();
        }
        int before = objectCount();

        String body = mvc.perform(multipart("/api/v1/songs").file(new MockMultipartFile("file", "tune.xml", "application/xml", chart)))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.status").value("COMPLETE"))
                .andReturn().getResponse().getContentAsString();
        String songId = JsonPath.read(body, "$.songId");

        assertThat(objectCount()).isEqualTo(before + 1);
        String key = jdbc.queryForObject("SELECT storage_key FROM songs", String.class);
        assertThat(storage.read(key)).isEqualTo(chart);
        mvc.perform(get("/api/v1/songs/" + songId + "/chords")).andExpect(jsonPath("$.chords[0].symbol").value("Dm7"));

        mvc.perform(delete("/api/v1/songs/" + songId)).andExpect(status().isNoContent());
        assertThat(objectCount()).isEqualTo(before);
    }
}
