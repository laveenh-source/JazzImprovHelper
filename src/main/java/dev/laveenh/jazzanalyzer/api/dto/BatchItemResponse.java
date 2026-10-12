package dev.laveenh.jazzanalyzer.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;

import java.util.UUID;

/** The outcome for one file of a batch upload: either an analysis to poll, or the reason it was refused. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BatchItemResponse(String filename, UUID songId, UUID analysisId, AnalysisStatus status,
                                Boolean duplicate, String error) {

    public static BatchItemResponse accepted(String filename, UUID songId, UUID analysisId,
                                             AnalysisStatus status, boolean duplicate) {
        return new BatchItemResponse(filename, songId, analysisId, status, duplicate, null);
    }

    public static BatchItemResponse rejected(String filename, String error) {
        return new BatchItemResponse(filename, null, null, null, null, error);
    }
}
