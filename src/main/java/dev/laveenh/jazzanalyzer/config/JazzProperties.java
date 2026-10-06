package dev.laveenh.jazzanalyzer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** Settings under the {@code jazz.*} prefix in application.yml. */
@ConfigurationProperties(prefix = "jazz")
public record JazzProperties(Upload upload, Storage storage) {

    public record Upload(long maxBytes) {
    }

    public record Storage(Path localDir) {
    }
}
