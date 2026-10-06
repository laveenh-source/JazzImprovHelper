package dev.laveenh.jazzanalyzer.api.dto;

import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;
import dev.laveenh.jazzanalyzer.persistence.SongListRow;

import java.time.Instant;
import java.util.UUID;

/** One entry of the song list. */
public record SongSummaryDto(UUID id, String title, String originalFilename, Instant uploadedAt,
                             String detectedKey, UUID latestAnalysisId, AnalysisStatus latestStatus) {

    public static SongSummaryDto from(SongListRow r) {
        return new SongSummaryDto(r.id(), r.title(), r.originalFilename(), r.uploadedAt(), r.detectedKey(),
                r.latestAnalysisId(), r.latestStatus());
    }
}
