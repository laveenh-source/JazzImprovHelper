package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.ChordFunction;

/**
 * The harmonic analysis of one chord.
 *
 * @param roman      Roman numeral such as "ii7", "V7/ii" or "bII7"
 * @param function   the chord's role
 * @param reason     short plain-English explanation of the label
 * @param degree     scale degree of the root above the local tonic as an upper-case name ("II", "bVII"),
 *                   which is what the scale rules match on
 * @param resolution where the chord resolves to
 */
public record FunctionResult(String roman, ChordFunction function, String reason, String degree,
                             Resolution resolution) {
}
