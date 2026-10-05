package dev.laveenh.jazzanalyzer.domain;

import java.util.List;

/**
 * One ranked scale suggestion for a chord.
 *
 * @param rank      1 = first choice
 * @param name      display name including the root, e.g. "D Dorian"
 * @param scaleType the scale without the root, e.g. "Dorian"
 * @param notes     the scale spelled from the chord's root
 * @param reason    short plain-English explanation
 */
public record ScaleSuggestion(int rank, String name, String scaleType, List<Note> notes, String reason) {
}
