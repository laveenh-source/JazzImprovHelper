package dev.laveenh.jazzanalyzer.storage;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * A throw-away fake of Amazon S3 (Adobe S3Mock) running in Docker, shared by the S3 tests. It speaks the real
 * S3 protocol, so the production {@link S3FileStorage} code runs unchanged against it.
 */
public final class S3Mock {

    public static final String BUCKET = "test-bucket";

    private static final GenericContainer<?> CONTAINER = new GenericContainer<>("adobe/s3mock:latest")
            .withExposedPorts(9090)
            .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", BUCKET)
            .waitingFor(Wait.forLogMessage(".*Started S3MockApplication.*", 1));

    static {
        CONTAINER.start();
    }

    private S3Mock() {
    }

    public static String endpoint() {
        return "http://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(9090);
    }

    /** A client for the fake, with throw-away credentials. */
    public static S3Client client() {
        return S3Client.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(URI.create(endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }
}
