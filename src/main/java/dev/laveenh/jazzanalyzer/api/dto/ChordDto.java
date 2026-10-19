package dev.laveenh.jazzanalyzer.api.dto;

import dev.laveenh.jazzanalyzer.domain.ChordFunction;
import dev.laveenh.jazzanalyzer.persistence.ChordEntity;
import dev.laveenh.jazzanalyzer.persistence.ScaleSuggestionEntity;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

public record ChordDto(
        int measure,
        double beat,
        String symbol,
        String quality,
        @Schema(example = "ii7") String roman,
        @Schema(example = "subdominant") String function,
        String functionReason,
        @Schema(example = "C major") String localKey,
        List<ScaleDto> scales) {

    public static ChordDto from(ChordEntity c, Map<Long, List<ScaleSuggestionEntity>> scalesByChordId) {
        List<ScaleDto> scales = scalesByChordId.getOrDefault(c.getId(), List.of()).stream().map(ScaleDto::from).toList();
        return new ChordDto(c.getMeasure(), c.getBeat(), c.getSymbol(), c.getQuality(), c.getRomanNumeral(),
                ChordFunction.valueOf(c.getChordFunction()).label(), c.getFunctionReason(), c.getLocalKey(), scales);
    }
}
