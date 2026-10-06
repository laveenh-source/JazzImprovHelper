package dev.laveenh.jazzanalyzer.api.dto;

import dev.laveenh.jazzanalyzer.persistence.ScaleSuggestionEntity;

import java.util.Arrays;
import java.util.List;

public record ScaleDto(int rank, String name, List<String> notes, String reason) {

    public static ScaleDto from(ScaleSuggestionEntity s) {
        return new ScaleDto(s.getRank(), s.getScaleName(), Arrays.asList(s.getScaleNotes().split(" ")), s.getReason());
    }
}
