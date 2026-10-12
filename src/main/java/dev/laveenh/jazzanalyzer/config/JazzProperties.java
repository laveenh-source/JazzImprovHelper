package dev.laveenh.jazzanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.file.Path;

/** Settings under the {@code jazz.*} prefix in application.yml. Missing sections fall back to safe defaults. */
@ConfigurationProperties(prefix = "jazz")
public record JazzProperties(Upload upload, Storage storage, Analysis analysis, RateLimit rateLimit) {

    @ConstructorBinding
    public JazzProperties {
        analysis = analysis == null ? new Analysis(null, 2, 20) : analysis;
        rateLimit = rateLimit == null ? new RateLimit(10, 120) : rateLimit;
    }

    public JazzProperties(Upload upload, Storage storage) {
        this(upload, storage, null, null);
    }

    public record Upload(long maxBytes) {
    }

    /**
     * @param localDir directory for the local-disk storage (development)
     * @param s3       settings for the S3 storage (production, profile "s3"); null when unused
     */
    public record Storage(Path localDir, S3 s3) {
    }

    /**
     * @param bucket        bucket name
     * @param region        AWS region
     * @param endpoint      optional endpoint override, used for a local S3 stand-in; blank on AWS
     * @param pathStyle     path-style addressing, needed by most S3 stand-ins
     */
    public record S3(String bucket, String region, String endpoint, boolean pathStyle) {
    }

    /**
     * @param mode          "async" (default) runs analyses on a background pool; "sync" runs them inside the request
     * @param threads       size of the worker pool
     * @param queueCapacity how many analyses may wait for a free worker before uploads are refused with 503
     */
    public record Analysis(String mode, int threads, int queueCapacity) {
        public Analysis {
            mode = mode == null || mode.isBlank() ? "async" : mode;
            threads = threads <= 0 ? 2 : threads;
            queueCapacity = queueCapacity <= 0 ? 20 : queueCapacity;
        }
    }

    /** Per client IP and minute. */
    public record RateLimit(int uploadsPerMinute, int requestsPerMinute) {
        public RateLimit {
            uploadsPerMinute = uploadsPerMinute <= 0 ? 10 : uploadsPerMinute;
            requestsPerMinute = requestsPerMinute <= 0 ? 120 : requestsPerMinute;
        }
    }
}
