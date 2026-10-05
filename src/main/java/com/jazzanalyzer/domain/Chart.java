package com.jazzanalyzer.domain;

import java.util.List;

/**
 * A parsed lead sheet: just the information the analysis needs.
 *
 * @param keySignature the key signature printed in the file, or null if none was given.
 *                     It is only a hint; the analysis decides the real tonal center from the chords.
 * @param timeSignature time signature, or null if none was given
 * @param chords       all chord symbols in score order
 * @param measureCount number of measures in the part that carried the chords
 */
public record Chart(String title, Key keySignature, TimeSignature timeSignature,
                    List<PlacedChord> chords, int measureCount) {

    public Chart {
        chords = List.copyOf(chords);
    }
}
