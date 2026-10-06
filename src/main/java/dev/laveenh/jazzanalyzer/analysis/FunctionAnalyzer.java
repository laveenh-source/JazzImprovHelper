package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.domain.ChordFunction;
import dev.laveenh.jazzanalyzer.domain.ChordQuality;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.Mode;
import dev.laveenh.jazzanalyzer.domain.PlacedChord;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static dev.laveenh.jazzanalyzer.domain.ChordFunction.DOMINANT;
import static dev.laveenh.jazzanalyzer.domain.ChordFunction.SUBDOMINANT;
import static dev.laveenh.jazzanalyzer.domain.ChordFunction.TONIC;
import static dev.laveenh.jazzanalyzer.domain.ChordQuality.*;

/**
 * Gives every chord a Roman numeral and a function relative to its local key.
 *
 * <p>Rules are tried in this order, and the first that explains the chord wins:
 * secondary dominant, diatonic, tritone substitution, diminished passing chord, modal interchange.
 * If none applies the chord is labelled UNCLASSIFIED rather than forced into a category.
 *
 * <p>Numerals are written against the tonic's major scale in both modes, the usual jazz convention:
 * in C minor the chords are i, iiø7, bIII, iv, V, bVI, bVII.
 */
public final class FunctionAnalyzer {

    private static final String[] FLAT_DEGREES = {"I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII"};
    private static final String[] SHARP_DEGREES = {"I", "#I", "II", "#II", "III", "IV", "#IV", "V", "#V", "VI", "#VI", "VII"};

    /** A chord a given number of semitones above the tonic, with the qualities that count as that diatonic chord. */
    private record Slot(int offset, Set<ChordQuality> qualities, ChordFunction function) {
        boolean matches(int d, ChordQuality q) {
            return offset == d && qualities.contains(q);
        }
    }

    private static Slot slot(int offset, ChordFunction function, ChordQuality first, ChordQuality... rest) {
        return new Slot(offset, EnumSet.of(first, rest), function);
    }

    private static final List<Slot> MAJOR_KEY_DIATONIC = List.of(
            slot(0, TONIC, MAJOR, MAJOR6, MAJOR7, MAJOR7_SHARP5),
            slot(2, SUBDOMINANT, MINOR, MINOR6, MINOR7),
            slot(4, TONIC, MINOR, MINOR7),
            slot(5, SUBDOMINANT, MAJOR, MAJOR6, MAJOR7),
            slot(7, DOMINANT, MAJOR, DOMINANT7, DOMINANT7_SUS4, SUS4, AUGMENTED7),
            slot(9, TONIC, MINOR, MINOR6, MINOR7),
            slot(11, DOMINANT, HALF_DIMINISHED7, DIMINISHED, DIMINISHED7));

    private static final List<Slot> MINOR_KEY_DIATONIC = List.of(
            slot(0, TONIC, MINOR, MINOR6, MINOR7, MINOR_MAJOR7),
            slot(2, SUBDOMINANT, HALF_DIMINISHED7, DIMINISHED, MINOR, MINOR7),
            slot(3, TONIC, MAJOR, MAJOR6, MAJOR7, MAJOR7_SHARP5),
            slot(5, SUBDOMINANT, MINOR, MINOR6, MINOR7),
            slot(7, DOMINANT, MAJOR, DOMINANT7, DOMINANT7_SUS4, SUS4, AUGMENTED7, MINOR, MINOR7),
            slot(8, SUBDOMINANT, MAJOR, MAJOR7),
            slot(10, SUBDOMINANT, MAJOR, MAJOR7, DOMINANT7),
            slot(11, DOMINANT, DIMINISHED7, DIMINISHED, HALF_DIMINISHED7));

    /** Chords borrowed from the parallel minor while in a major key. */
    private static final List<Slot> MAJOR_KEY_BORROWED = List.of(
            slot(0, ChordFunction.MODAL_INTERCHANGE, MINOR, MINOR6, MINOR7, MINOR_MAJOR7),
            slot(1, ChordFunction.MODAL_INTERCHANGE, MAJOR, MAJOR7),
            slot(2, ChordFunction.MODAL_INTERCHANGE, HALF_DIMINISHED7, DIMINISHED),
            slot(3, ChordFunction.MODAL_INTERCHANGE, MAJOR, MAJOR6, MAJOR7, DOMINANT7),
            slot(5, ChordFunction.MODAL_INTERCHANGE, MINOR, MINOR6, MINOR7),
            slot(7, ChordFunction.MODAL_INTERCHANGE, MINOR, MINOR7),
            slot(8, ChordFunction.MODAL_INTERCHANGE, MAJOR, MAJOR7, DOMINANT7),
            slot(10, ChordFunction.MODAL_INTERCHANGE, MAJOR, MAJOR6, MAJOR7, DOMINANT7));

    /** Chords borrowed from the parallel major while in a minor key. */
    private static final List<Slot> MINOR_KEY_BORROWED = List.of(
            slot(0, ChordFunction.MODAL_INTERCHANGE, MAJOR, MAJOR6, MAJOR7),
            slot(1, ChordFunction.MODAL_INTERCHANGE, MAJOR, MAJOR7),
            slot(4, ChordFunction.MODAL_INTERCHANGE, MINOR, MINOR7),
            slot(5, ChordFunction.MODAL_INTERCHANGE, MAJOR, MAJOR6, MAJOR7, DOMINANT7),
            slot(9, ChordFunction.MODAL_INTERCHANGE, MINOR, MINOR7, HALF_DIMINISHED7));

    public List<FunctionResult> analyze(List<PlacedChord> chords, List<Key> keys) {
        if (chords.size() != keys.size()) {
            throw new IllegalArgumentException("Need exactly one key per chord");
        }
        List<FunctionResult> results = new ArrayList<>(chords.size());
        for (int i = 0; i < chords.size(); i++) {
            results.add(analyzeChord(chords.get(i).chord(), nextWithDifferentRoot(chords, i), keys.get(i)));
        }
        return results;
    }

    private FunctionResult analyzeChord(Chord chord, Chord next, Key key) {
        int d = offsetFromTonic(chord, key);
        String degree = FLAT_DEGREES[d];
        Resolution resolution = resolution(chord, next);
        ChordQuality quality = chord.quality();

        if (quality == UNKNOWN) {
            return new FunctionResult("?", ChordFunction.UNCLASSIFIED,
                    "Chord kind " + chord.symbol() + " is not one this analyzer models", degree, resolution);
        }

        int stepToNext = next == null ? -1 : Math.floorMod(next.root().pitchClass() - chord.root().pitchClass(), 12);
        boolean dominantFamily = quality.isDominantFamily();

        // 1. Secondary dominant: a dominant (not the V of this key) resolving down a fifth.
        if (dominantFamily && d != 7 && stepToNext == 5) {
            String target = targetNumeral(next, key);
            return new FunctionResult("V" + romanSuffix(chord) + "/" + target, ChordFunction.SECONDARY_DOMINANT,
                    "Dominant chord resolving down a fifth to " + next.symbol() + " (" + target + " of " + key + ")",
                    degree, resolution);
        }

        // 2. Diatonic chord of the key.
        List<Slot> diatonic = key.mode() == Mode.MAJOR ? MAJOR_KEY_DIATONIC : MINOR_KEY_DIATONIC;
        Slot slot = find(diatonic, d, quality);
        if (slot != null) {
            String roman = numeral(d, false, chord);
            return new FunctionResult(roman, slot.function(),
                    roman + " is diatonic in " + key + " (" + slot.function().label() + " function)", degree, resolution);
        }

        // 3. Tritone substitution: a dominant resolving down a half step.
        if (dominantFamily && stepToNext == 11) {
            String target = targetNumeral(next, key);
            int targetOffset = offsetFromTonic(next, key);
            String roman = targetOffset == 0
                    ? FLAT_DEGREES[d] + romanSuffix(chord)
                    : "subV" + romanSuffix(chord) + "/" + target;
            return new FunctionResult(roman, ChordFunction.TRITONE_SUBSTITUTION,
                    "Dominant a half step above " + next.symbol() + ": tritone substitute for the V7 of " + target,
                    degree, resolution);
        }

        // 4. Diminished chord moving by a half step, up or down.
        if ((quality == DIMINISHED7 || quality == DIMINISHED) && (stepToNext == 1 || stepToNext == 11)) {
            return new FunctionResult(numeral(d, true, chord), ChordFunction.DIMINISHED_PASSING,
                    "Diminished chord connecting by half step to " + next.symbol(), degree, resolution);
        }

        // 5. Borrowed from the parallel mode.
        List<Slot> borrowed = key.mode() == Mode.MAJOR ? MAJOR_KEY_BORROWED : MINOR_KEY_BORROWED;
        Slot borrowedSlot = find(borrowed, d, quality);
        if (borrowedSlot != null) {
            String parallel = key.mode() == Mode.MAJOR ? "minor" : "major";
            return new FunctionResult(numeral(d, false, chord), ChordFunction.MODAL_INTERCHANGE,
                    "Chord borrowed from the parallel " + parallel + " of " + key, degree, resolution);
        }

        // 6. Honest fallback.
        return new FunctionResult(numeral(d, false, chord), ChordFunction.UNCLASSIFIED,
                "Not explained by " + key + ", a secondary dominant, a tritone substitution or borrowing",
                degree, resolution);
    }

    private static Slot find(List<Slot> slots, int d, ChordQuality quality) {
        return slots.stream().filter(s -> s.matches(d, quality)).findFirst().orElse(null);
    }

    // ------------------------------------------------------------ neighbours and resolution

    /** The next chord whose root differs from this one: repeated or same-root changes are not a move. */
    private static Chord nextWithDifferentRoot(List<PlacedChord> chords, int i) {
        int root = chords.get(i).chord().root().pitchClass();
        for (int j = i + 1; j < chords.size(); j++) {
            if (chords.get(j).chord().root().pitchClass() != root) {
                return chords.get(j).chord();
            }
        }
        return null;
    }

    private static Resolution resolution(Chord chord, Chord next) {
        if (next == null) {
            return Resolution.NONE;
        }
        int step = Math.floorMod(next.root().pitchClass() - chord.root().pitchClass(), 12);
        boolean resolves = step == 5 || (step == 11 && chord.quality().isDominantFamily());
        if (!resolves) {
            return Resolution.NONE;
        }
        return next.quality().isMinorish() ? Resolution.MINOR : Resolution.MAJOR;
    }

    // ------------------------------------------------------------ numerals

    private static int offsetFromTonic(Chord chord, Key key) {
        return Math.floorMod(chord.root().pitchClass() - key.tonic().pitchClass(), 12);
    }

    /** Numeral of a chord's root alone, e.g. "ii" for Dm7 in C. Used as the target of V7/x. */
    private static String targetNumeral(Chord target, Key key) {
        String name = FLAT_DEGREES[offsetFromTonic(target, key)];
        return isMinorLike(target.quality()) ? name.toLowerCase() : name;
    }

    private static String numeral(int offset, boolean sharpSpelling, Chord chord) {
        String name = (sharpSpelling ? SHARP_DEGREES : FLAT_DEGREES)[offset];
        return (isMinorLike(chord.quality()) ? name.toLowerCase() : name) + romanSuffix(chord);
    }

    private static boolean isMinorLike(ChordQuality q) {
        return q.isMinorish() || q == HALF_DIMINISHED7 || q == DIMINISHED || q == DIMINISHED7;
    }

    /** Quality part of a numeral: "7", "maj7", "ø7", "°7", with extension and alterations appended. */
    private static String romanSuffix(Chord chord) {
        String base = switch (chord.quality()) {
            case MAJOR, MINOR -> "";
            case DIMINISHED -> "°";
            case AUGMENTED -> "+";
            case SUS2 -> "sus2";
            case SUS4 -> "sus4";
            case MAJOR6, MINOR6 -> "6";
            case MAJOR7 -> "maj7";
            case MINOR7, DOMINANT7 -> "7";
            case MINOR_MAJOR7 -> "(maj7)";
            case HALF_DIMINISHED7 -> "ø7";
            case DIMINISHED7 -> "°7";
            case AUGMENTED7 -> "+7";
            case MAJOR7_SHARP5 -> "maj7#5";
            case DOMINANT7_SUS4 -> "7sus4";
            case UNKNOWN -> "?";
        };
        if (chord.extension() > 0 && chord.quality().canStackExtension()) {
            base = base.substring(0, base.length() - 1) + chord.extension();
        }
        if (chord.alt()) {
            return base + "alt";
        }
        StringBuilder sb = new StringBuilder(base);
        chord.alterations().forEach(sb::append);
        return sb.toString();
    }
}
