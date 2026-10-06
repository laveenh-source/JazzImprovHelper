package dev.laveenh.jazzanalyzer.persistence;

import java.time.Instant;
import java.util.UUID;

/** One row of the song list: the song plus the state of its most recent analysis (null columns if none). */
public record SongListRow(UUID id, String title, String originalFilename, Instant uploadedAt,
                          UUID latestAnalysisId, AnalysisStatus latestStatus, String detectedKey) {
}
