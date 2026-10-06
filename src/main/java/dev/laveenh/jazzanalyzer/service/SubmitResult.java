package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;

import java.util.UUID;

/**
 * Outcome of an upload.
 *
 * @param duplicate true when the same file had been uploaded before and its existing analysis is returned
 */
public record SubmitResult(UUID songId, UUID analysisId, AnalysisStatus status, boolean duplicate) {
}
