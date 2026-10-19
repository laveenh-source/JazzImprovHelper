package dev.laveenh.jazzanalyzer.domain;

import java.util.List;

/** Everything we know about one chord of the chart after analysis. */
public record ChordAnalysis(int measure, double beat, Chord chord, Key localKey, String roman,
                            ChordFunction function, String functionReason, List<ScaleSuggestion> scales) {
}
