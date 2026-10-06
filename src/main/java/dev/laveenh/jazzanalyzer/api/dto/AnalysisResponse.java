package dev.laveenh.jazzanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.laveenh.jazzanalyzer.persistence.AnalysisEntity;
import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;
import dev.laveenh.jazzanalyzer.service.AnalysisDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A full analysis. While it is not COMPLETE, the key regions and chords are left out of the JSON. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnalysisResponse(
        UUID analysisId,
        UUID songId,
        AnalysisStatus status,
        String title,
        String detectedKey,
        Instant createdAt,
        Instant completedAt,
        String errorMessage,
        List<KeyRegionDto> keyRegions,
        List<ChordDto> chords) {

    public static AnalysisResponse from(AnalysisDetails d) {
        AnalysisEntity a = d.analysis();
        boolean complete = a.getStatus() == AnalysisStatus.COMPLETE;
        return new AnalysisResponse(a.getId(), a.getSongId(), a.getStatus(), d.song().getTitle(), a.getDetectedKey(),
                a.getCreatedAt(), a.getCompletedAt(), a.getErrorMessage(),
                complete ? d.keyRegions().stream().map(KeyRegionDto::from).toList() : null,
                complete ? d.chords().stream().map(c -> ChordDto.from(c, d.scalesByChordId())).toList() : null);
    }
}
