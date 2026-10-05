package com.jazzanalyzer.domain;

/**
 * The normalized "type" of a chord, independent of its root. The suffix is the conventional
 * lead-sheet spelling and is used to render chord symbols.
 */
public enum ChordQuality {
    MAJOR(""),
    MINOR("m"),
    DIMINISHED("dim"),
    AUGMENTED("aug"),
    SUS2("sus2"),
    SUS4("sus4"),
    MAJOR6("6"),
    MINOR6("m6"),
    MAJOR7("maj7"),
    MINOR7("m7"),
    DOMINANT7("7"),
    MINOR_MAJOR7("mMaj7"),
    HALF_DIMINISHED7("m7b5"),
    DIMINISHED7("dim7"),
    AUGMENTED7("7#5"),
    MAJOR7_SHARP5("maj7#5"),
    DOMINANT7_SUS4("7sus4"),
    /** A MusicXML kind we do not model (Neapolitan, Tristan, power chords...). Analysis reports it honestly. */
    UNKNOWN("?");

    private final String suffix;

    ChordQuality(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }

    /** True for the seventh chords whose suffix ends in "7", which can be replaced by 9, 11 or 13. */
    public boolean canStackExtension() {
        return this == MAJOR7 || this == MINOR7 || this == DOMINANT7;
    }
}
