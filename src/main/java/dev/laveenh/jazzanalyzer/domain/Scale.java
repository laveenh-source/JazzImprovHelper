package dev.laveenh.jazzanalyzer.domain;

import dev.laveenh.jazzanalyzer.domain.Note.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * A scale with correctly spelled notes, e.g. D Dorian = D E F G A B C.
 *
 * <p>Scales are described by a formula of scale degrees relative to a major scale, such as
 * Dorian = "1 2 b3 4 5 6 b7". Each degree uses its own letter name (1=D, 2=E, b3=F...), which
 * is what keeps the spelling musically correct (F# Lydian has an E#, not an F).
 */
public record Scale(String name, Note root, List<Note> notes) {

    private static final int[] MAJOR_SCALE_SEMITONES = {0, 2, 4, 5, 7, 9, 11};

    public Scale {
        notes = List.copyOf(notes);
    }

    /** Builds a scale from a formula such as "1 2 b3 4 5 6 b7". */
    public static Scale build(String name, Note root, String formula) {
        List<Note> notes = new ArrayList<>();
        for (String token : formula.trim().split("\\s+")) {
            notes.add(degreeNote(root, token));
        }
        return new Scale(name, root, notes);
    }

    /** The note at one scale degree token such as "5", "b3" or "#4" above {@code root}. */
    public static Note degreeNote(Note root, String token) {
        int i = 0;
        int shift = 0;
        while (i < token.length() && (token.charAt(i) == 'b' || token.charAt(i) == '#')) {
            shift += token.charAt(i) == '#' ? 1 : -1;
            i++;
        }
        int degree;
        try {
            degree = Integer.parseInt(token.substring(i));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad scale degree: " + token);
        }
        if (degree < 1 || degree > 7) {
            throw new IllegalArgumentException("Scale degree must be 1-7: " + token);
        }
        Step letter = Step.values()[(root.step().ordinal() + degree - 1) % 7];
        int targetPitchClass = Math.floorMod(root.pitchClass() + MAJOR_SCALE_SEMITONES[degree - 1] + shift, 12);
        int alter = Math.floorMod(targetPitchClass - letter.semitone() + 6, 12) - 6;
        return new Note(letter, alter);
    }

    @Override
    public String toString() {
        return root + " " + name;
    }
}
