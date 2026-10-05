package com.jazzanalyzer.domain;

import java.util.Locale;

/**
 * A spelled note name such as C, F# or Bb. Spelling matters in music (F# is not Gb),
 * so we keep the letter and the accidental, and derive the pitch class (0-11) on demand.
 */
public record Note(Step step, int alter) {

    public enum Step {
        C(0), D(2), E(4), F(5), G(7), A(9), B(11);

        private final int semitone;

        Step(int semitone) {
            this.semitone = semitone;
        }

        public int semitone() {
            return semitone;
        }
    }

    public Note {
        if (step == null) {
            throw new IllegalArgumentException("step must not be null");
        }
    }

    /** Pitch class: 0 = C, 1 = C#/Db, ... 11 = B. */
    public int pitchClass() {
        return Math.floorMod(step.semitone() + alter, 12);
    }

    /** Parses text like "C", "F#", "Bb", "Ebb". */
    public static Note parse(String text) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("note text must not be empty");
        }
        Step step;
        try {
            step = Step.valueOf(text.substring(0, 1).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a note name: " + text);
        }
        int alter = 0;
        for (char c : text.substring(1).toCharArray()) {
            switch (c) {
                case '#' -> alter++;
                case 'b' -> alter--;
                default -> throw new IllegalArgumentException("Not a note name: " + text);
            }
        }
        return new Note(step, alter);
    }

    @Override
    public String toString() {
        String accidental = alter >= 0 ? "#".repeat(alter) : "b".repeat(-alter);
        return step.name() + accidental;
    }
}
