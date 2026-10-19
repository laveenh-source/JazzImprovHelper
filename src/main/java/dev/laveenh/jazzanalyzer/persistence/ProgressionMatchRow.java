package dev.laveenh.jazzanalyzer.persistence;

import java.util.UUID;

/** One place where a progression occurs: which song, in which key, and where. */
public record ProgressionMatchRow(UUID songId, String title, UUID analysisId, int tonicPitchClass,
                                  int startMeasure, int endMeasure, String chords) {
}
