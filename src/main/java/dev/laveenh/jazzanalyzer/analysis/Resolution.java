package dev.laveenh.jazzanalyzer.analysis;

/**
 * Where a chord is heading, judged from the next chord with a different root. Used by the scale rules:
 * a dominant resolving to a minor chord gets different scales than one resolving to a major chord.
 */
public enum Resolution {
    /** Resolves down a fifth (or, for dominants, down a half step) to a chord that is not minor. */
    MAJOR,
    /** Resolves down a fifth (or half step) to a minor chord. */
    MINOR,
    /** No such resolution (last chord, or the chord moves some other way). */
    NONE
}
