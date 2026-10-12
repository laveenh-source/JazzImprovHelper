package dev.laveenh.jazzanalyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * The Amazon S3 client for the "s3" profile.
 *
 * <p>No credentials appear here on purpose. The SDK looks them up itself (the "default credentials chain"):
 * on ECS it finds the task's IAM role, on a developer machine the usual AWS environment variables or profile.
 */
@Configuration
@Profile("s3")
public class S3Config {

    @Bean(destroyMethod = "close")
    S3Client s3Client(JazzProperties properties) {
        JazzProperties.S3 s3 = properties.storage().s3();
        if (s3 == null || s3.bucket() == null || s3.bucket().isBlank()) {
            throw new IllegalStateException("The s3 profile needs a bucket: set S3_BUCKET (jazz.storage.s3.bucket).");
        }
        var builder = S3Client.builder().region(Region.of(s3.region()));
        if (s3.endpoint() != null && !s3.endpoint().isBlank()) {
            // a local S3 stand-in (docker compose, tests); on AWS this stays blank
            builder.endpointOverride(URI.create(s3.endpoint()));
        }
        if (s3.pathStyle()) {
            builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }
}
