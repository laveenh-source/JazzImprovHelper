package dev.laveenh.jazzanalyzer.storage;

import dev.laveenh.jazzanalyzer.config.JazzProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The real S3FileStorage against a fake S3 (needs Docker). */
class S3FileStorageTest {

    private static S3Client s3;
    private static S3FileStorage storage;

    @BeforeAll
    static void connect() {
        s3 = S3Mock.client();
        storage = new S3FileStorage(s3, new JazzProperties(new JazzProperties.Upload(1),
                new JazzProperties.Storage(Path.of("unused"), new JazzProperties.S3(S3Mock.BUCKET, "us-east-1", S3Mock.endpoint(), true))));
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(S3Mock.BUCKET).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    @Test
    void storesUnderAGeneratedKeyAndReadsItBack() {
        String key = storage.store(bytes("<score-partwise/>"));

        assertThat(key).matches("[0-9a-f-]{36}\\.xml");
        assertThat(exists(key)).isTrue();
        assertThat(storage.read(key)).isEqualTo(bytes("<score-partwise/>"));
    }

    @Test
    void everyStoreGetsAFreshKey() {
        assertThat(storage.store(bytes("a"))).isNotEqualTo(storage.store(bytes("a")));
    }

    @Test
    void objectsAreStoredAsXmlWithServerSideEncryptionRequested() {
        String key = storage.store(bytes("<a/>"));
        var head = s3.headObject(HeadObjectRequest.builder().bucket(S3Mock.BUCKET).key(key).build());
        assertThat(head.contentType()).isEqualTo("application/xml");
        assertThat(head.serverSideEncryptionAsString()).isEqualTo("AES256");
    }

    @Test
    void readingAMissingKeyFailsWithAStorageException() {
        assertThatThrownBy(() -> storage.read("nope.xml")).isInstanceOf(StorageException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void deleteRemovesTheObjectAndIsHarmlessTwice() {
        String key = storage.store(bytes("x"));
        storage.delete(key);
        storage.delete(key);
        assertThat(exists(key)).isFalse();
    }

    @Test
    void aBrokenConnectionBecomesAStorageException() {
        S3FileStorage unreachable = new S3FileStorage(
                S3Client.builder().region(software.amazon.awssdk.regions.Region.US_EAST_1)
                        .endpointOverride(java.net.URI.create("http://127.0.0.1:1"))
                        .credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                                software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("a", "b")))
                        .overrideConfiguration(c -> c.retryStrategy(software.amazon.awssdk.awscore.retry.AwsRetryStrategy.doNotRetry()).apiCallTimeout(java.time.Duration.ofSeconds(3)))
                        .build(),
                new JazzProperties(new JazzProperties.Upload(1),
                        new JazzProperties.Storage(Path.of("unused"), new JazzProperties.S3(S3Mock.BUCKET, "us-east-1", "", false))));

        assertThatThrownBy(() -> unreachable.store(bytes("x"))).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> unreachable.read("k")).isInstanceOf(StorageException.class);
        assertThatThrownBy(() -> unreachable.delete("k")).isInstanceOf(StorageException.class);
    }
}
