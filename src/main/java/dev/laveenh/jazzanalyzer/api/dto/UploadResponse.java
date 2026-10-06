package dev.laveenh.jazzanalyzer.api.dto;

import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record UploadResponse(
        UUID songId,
        UUID analysisId,
        AnalysisStatus status,
        @Schema(description = "True when this exact file was uploaded before and its existing analysis is returned")
        boolean duplicate,
        @Schema(description = "Where to poll for the result", example = "/api/v1/analyses/3f2b1c0e-0000-0000-0000-000000000000")
        String analysisUrl) {
}
