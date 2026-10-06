package dev.laveenh.jazzanalyzer.domain;

/** The harmonic role of a chord within its local key. */
public enum ChordFunction {
    TONIC("tonic"),
    SUBDOMINANT("subdominant"),
    DOMINANT("dominant"),
    SECONDARY_DOMINANT("secondary dominant"),
    TRITONE_SUBSTITUTION("tritone substitution"),
    DIMINISHED_PASSING("diminished passing chord"),
    MODAL_INTERCHANGE("modal interchange"),
    /** The chord could not be explained by any rule; we say so instead of guessing. */
    UNCLASSIFIED("unclassified");

    private final String label;

    ChordFunction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
