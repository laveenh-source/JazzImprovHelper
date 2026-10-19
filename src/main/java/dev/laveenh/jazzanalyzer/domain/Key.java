package dev.laveenh.jazzanalyzer.domain;

import dev.laveenh.jazzanalyzer.domain.Note.Step;

import java.util.Locale;

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

    /**
     * Builds a key from a key signature plus the MusicXML {@code <mode>} text. Church modes are
     * read by their tonal center: fifths=0 with "dorian" is D (the 2nd degree of C major), and
     * dorian/phrygian/aeolian/locrian count as minor, lydian/mixolydian/ionian as major.
     */
    public static Key fromSignature(int fifths, String modeText) {
        String text = modeText == null ? "" : modeText.trim().toLowerCase(Locale.ROOT);
        int degree = switch (text) {
            case "dorian" -> 2;
            case "phrygian" -> 3;
            case "lydian" -> 4;
            case "mixolydian" -> 5;
            case "aeolian", "minor" -> 6;
            case "locrian" -> 7;
            default -> 1; // major, ionian, missing or unrecognised
        };
        if (degree == 1) {
            return fromFifths(fifths, Mode.MAJOR);
        }
        if (degree == 6) {
            return fromFifths(fifths, Mode.MINOR);
        }
        Note tonic = Scale.degreeNote(fromFifths(fifths, Mode.MAJOR).tonic(), String.valueOf(degree));
        Mode mode = (degree == 4 || degree == 5) ? Mode.MAJOR : Mode.MINOR;
        return new Key(tonic, mode);
    }

    @Override
    public String toString() {
        return tonic + " " + mode.label();
    }
}
