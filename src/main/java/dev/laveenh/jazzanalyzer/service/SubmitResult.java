package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;

import java.util.UUID;

/**
 * Outcome of an upload.
 *
 * @param duplicate    true when this exact file had been uploaded before
 * @param newAnalysis  true when a new analysis was started for it (a first upload, or a retry after a failure);
 *                     false when an existing analysis is simply returned. This decides 202 versus 200.
 */
public record SubmitResult(UUID songId, UUID analysisId, AnalysisStatus status, boolean duplicate, boolean newAnalysis) {
}
