package com.jazzanalyzer.domain;

import static com.jazzanalyzer.domain.Note.Step;

/** A tonal center: a tonic note plus major or minor mode. */
public record Key(Note tonic, Mode mode) {

    // Letters in order along the circle of fifths, starting from F (the flattest natural note).
    private static final Step[] LINE_OF_FIFTHS = {Step.F, Step.C, Step.G, Step.D, Step.A, Step.E, Step.B};

    public Key {
        if (tonic == null || mode == null) {
            throw new IllegalArgumentException("tonic and mode must not be null");
        }
    }

    /**
     * Builds a key from a key signature. {@code fifths} is the MusicXML convention:
     * negative = flats, positive = sharps (-2 = two flats = Bb major / G minor).
     */
    public static Key fromFifths(int fifths, Mode mode) {
        // Position on the line of fifths where F = -1, C = 0, G = 1 ... The relative minor's
        // tonic sits 3 steps further along (A minor has the same signature as C major).
        int position = mode == Mode.MAJOR ? fifths : fifths + 3;
        int index = position + 1;
        Step step = LINE_OF_FIFTHS[Math.floorMod(index, 7)];
        int alter = Math.floorDiv(index, 7);
        return new Key(new Note(step, alter), mode);
    }

    @Override
    public String toString() {
        return tonic + " " + mode.label();
    }
}
