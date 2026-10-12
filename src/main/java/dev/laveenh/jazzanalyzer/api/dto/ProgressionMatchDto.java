package dev.laveenh.jazzanalyzer.api.dto;

import dev.laveenh.jazzanalyzer.persistence.ProgressionMatchRow;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record ProgressionMatchDto(
        UUID songId,
        String title,
        UUID analysisId,
        @Schema(description = "The tonic the progression was found above", example = "Bb") String key,
        int startMeasure,
        int endMeasure,
        @Schema(example = "Cm7 F7 Bbmaj7") String chords) {

    private static final String[] NAMES = {"C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};

    public static ProgressionMatchDto from(ProgressionMatchRow r) {
        return new ProgressionMatchDto(r.songId(), r.title(), r.analysisId(), NAMES[r.tonicPitchClass()],
                r.startMeasure(), r.endMeasure(), r.chords());
    }
}
