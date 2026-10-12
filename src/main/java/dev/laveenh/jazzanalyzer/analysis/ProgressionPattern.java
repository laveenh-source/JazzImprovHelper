package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.ChordQuality;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static dev.laveenh.jazzanalyzer.domain.ChordQuality.*;

/**
 * The chord progressions the library can be searched for. Each step is a chord defined by how many
 * semitones its root sits above the tonic and which chord qualities count, so "ii-V-I in Bb" means
 * Cm7 (2 above Bb) then F7 (7 above) then Bbmaj7 (the tonic itself).
 */
public enum ProgressionPattern {

    II_V_I("ii-V-I",
            step(2, MINOR7, MINOR),
            step(7, DOMINANT7, DOMINANT7_SUS4, AUGMENTED7),
            step(0, MAJOR7, MAJOR6, MAJOR, MAJOR7_SHARP5)),
    II_V_MINOR_I("ii-V-i",
            step(2, HALF_DIMINISHED7, MINOR7),
            step(7, DOMINANT7, DOMINANT7_SUS4, AUGMENTED7),
            step(0, MINOR7, MINOR, MINOR6, MINOR_MAJOR7)),
    II_V("ii-V",
            step(2, MINOR7, MINOR, HALF_DIMINISHED7),
            step(7, DOMINANT7, DOMINANT7_SUS4, AUGMENTED7)),
    V_I("V-I",
            step(7, DOMINANT7, DOMINANT7_SUS4, AUGMENTED7),
            step(0, MAJOR7, MAJOR6, MAJOR, MAJOR7_SHARP5)),
    I_VI_II_V("I-vi-ii-V",
            step(0, MAJOR7, MAJOR6, MAJOR),
            step(9, MINOR7, MINOR),
            step(2, MINOR7, MINOR),
            step(7, DOMINANT7, DOMINANT7_SUS4, AUGMENTED7)),
    III_VI_II_V("iii-vi-ii-V",
            step(4, MINOR7, MINOR),
            step(9, MINOR7, MINOR),
            step(2, MINOR7, MINOR),
            step(7, DOMINANT7, DOMINANT7_SUS4, AUGMENTED7)),
    II_TRITONE_I("ii-bII7-I",
            step(2, MINOR7, MINOR),
            step(1, DOMINANT7),
            step(0, MAJOR7, MAJOR6, MAJOR));

    /** One chord of a pattern. */
    public record Step(int semitonesAboveTonic, Set<ChordQuality> qualities) {
    }

    private final String text;
    private final List<Step> steps;

    ProgressionPattern(String text, Step... steps) {
        this.text = text;
        this.steps = List.of(steps);
    }

    private static Step step(int semitones, ChordQuality first, ChordQuality... more) {
        return new Step(semitones, EnumSet.of(first, more));
    }

    /** The name users type, e.g. "ii-V-I". Case matters: ii-V-I (major) and ii-V-i (minor) differ. */
    public String text() {
        return text;
    }

    public List<Step> steps() {
        return steps;
    }

    public static Optional<ProgressionPattern> parse(String text) {
        return Arrays.stream(values()).filter(p -> p.text.equals(text)).findFirst();
    }

    public static List<String> supportedNames() {
        return Arrays.stream(values()).map(ProgressionPattern::text).toList();
    }
}
