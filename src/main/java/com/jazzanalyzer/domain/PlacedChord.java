package com.jazzanalyzer.domain;

/**
 * A chord at a position in the chart.
 *
 * @param measure measure number as printed in the file
 * @param beat    1-based beat within the measure, counted in the time signature's beat unit
 *                (1.0 = the downbeat, 3.0 = beat three, 2.5 = the "and" of two in 4/4)
 */
public record PlacedChord(int measure, double beat, Chord chord) {
}
