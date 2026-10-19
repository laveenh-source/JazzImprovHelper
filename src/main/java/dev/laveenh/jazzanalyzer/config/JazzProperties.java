package dev.laveenh.jazzanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.nio.file.Path;

/** Settings under the {@code jazz.*} prefix in application.yml. A missing analysis section falls back to safe defaults. */
@ConfigurationProperties(prefix = "jazz")
public record JazzProperties(Upload upload, Storage storage, Analysis analysis) {

    @ConstructorBinding
    public JazzProperties {
        analysis = analysis == null ? new Analysis(null, 2, 20) : analysis;
    }

    public JazzProperties(Upload upload, Storage storage) {
        this(upload, storage, null);
    }

    public record Upload(long maxBytes) {
    }

    /** @param localDir the directory where uploaded charts are kept */
    public record Storage(Path localDir) {
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
}
