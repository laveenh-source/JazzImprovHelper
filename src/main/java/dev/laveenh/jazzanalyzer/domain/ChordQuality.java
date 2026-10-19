package dev.laveenh.jazzanalyzer.domain;

/**
 * The normalized "type" of a chord, independent of its root. The suffix is the conventional
 * lead-sheet spelling and is used to render chord symbols.
 */
public enum ChordQuality {
    MAJOR("", 0, 4, 7),
    MINOR("m", 0, 3, 7),
    DIMINISHED("dim", 0, 3, 6),
    AUGMENTED("aug", 0, 4, 8),
    SUS2("sus2", 0, 2, 7),
    SUS4("sus4", 0, 5, 7),
    MAJOR6("6", 0, 4, 7, 9),
    MINOR6("m6", 0, 3, 7, 9),
    MAJOR7("maj7", 0, 4, 7, 11),
    MINOR7("m7", 0, 3, 7, 10),
    DOMINANT7("7", 0, 4, 7, 10),
    MINOR_MAJOR7("mMaj7", 0, 3, 7, 11),
    HALF_DIMINISHED7("m7b5", 0, 3, 6, 10),
    DIMINISHED7("dim7", 0, 3, 6, 9),
    AUGMENTED7("7#5", 0, 4, 8, 10),
    MAJOR7_SHARP5("maj7#5", 0, 4, 8, 11),
    DOMINANT7_SUS4("7sus4", 0, 5, 7, 10),
    /** A MusicXML kind we do not model (Neapolitan, Tristan, power chords...). Analysis reports it honestly. */
    UNKNOWN("?", 0);

    private final String suffix;
    private final int[] intervals;

    ChordQuality(String suffix, int... intervals) {
        this.suffix = suffix;
        this.intervals = intervals;
    }

    /** Semitones above the root of the basic chord tones (extensions and alterations excluded). */
    public int[] intervals() {
        return intervals.clone();
    }

    public boolean isMajorish() {
        return this == MAJOR || this == MAJOR6 || this == MAJOR7 || this == MAJOR7_SHARP5;
    }

    public boolean isMinorish() {
        return this == MINOR || this == MINOR6 || this == MINOR7 || this == MINOR_MAJOR7;
    }

    /** Dominant-function sevenths: the chords that want to resolve down a fifth. */
    public boolean isDominantFamily() {
        return this == DOMINANT7 || this == AUGMENTED7 || this == DOMINANT7_SUS4;
    }

    public String suffix() {
        return suffix;
    }

    /** True for the seventh chords whose suffix ends in "7", which can be replaced by 9, 11 or 13. */
    public boolean canStackExtension() {
        return this == MAJOR7 || this == MINOR7 || this == DOMINANT7;
    }
}
