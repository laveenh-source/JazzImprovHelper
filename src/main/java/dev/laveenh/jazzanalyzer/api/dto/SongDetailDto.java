package dev.laveenh.jazzanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.laveenh.jazzanalyzer.persistence.AnalysisEntity;
import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;
import dev.laveenh.jazzanalyzer.service.AnalysisDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Song metadata with a summary of its latest analysis. */
public record SongDetailDto(UUID id, String title, String originalFilename, Instant uploadedAt,
                            AnalysisSummary latestAnalysis) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnalysisSummary(UUID analysisId, AnalysisStatus status, String detectedKey, Instant createdAt,
                                  Instant completedAt, String errorMessage, Integer chordCount,
                                  List<KeyRegionDto> keyRegions) {
    }

    public static SongDetailDto from(AnalysisDetails d) {
        AnalysisEntity a = d.analysis();
        boolean complete = a.getStatus() == AnalysisStatus.COMPLETE;
        AnalysisSummary summary = new AnalysisSummary(a.getId(), a.getStatus(), a.getDetectedKey(), a.getCreatedAt(),
                a.getCompletedAt(), a.getErrorMessage(),
                complete ? d.chords().size() : null,
                complete ? d.keyRegions().stream().map(KeyRegionDto::from).toList() : null);
        return new SongDetailDto(d.song().getId(), d.song().getTitle(), d.song().getOriginalFilename(),
                d.song().getUploadedAt(), summary);
    }
}
