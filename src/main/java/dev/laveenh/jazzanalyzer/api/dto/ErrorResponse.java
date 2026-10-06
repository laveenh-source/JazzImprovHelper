package dev.laveenh.jazzanalyzer.api.dto;

import java.time.Instant;

/** The one JSON shape every error uses. */
public record ErrorResponse(Instant timestamp, int status, String error, String message, String path) {
}
