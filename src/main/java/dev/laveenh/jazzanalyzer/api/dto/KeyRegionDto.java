package dev.laveenh.jazzanalyzer.api.dto;

import dev.laveenh.jazzanalyzer.persistence.KeyRegionEntity;

public record KeyRegionDto(String key, int startMeasure, int endMeasure, double confidence, String reason) {

    public static KeyRegionDto from(KeyRegionEntity r) {
        return new KeyRegionDto(r.getKeyName(), r.getStartMeasure(), r.getEndMeasure(), r.getConfidence(), r.getReason());
    }
}
