package dev.laveenh.jazzanalyzer;

import dev.laveenh.jazzanalyzer.analysis.ChordClassifier;
import dev.laveenh.jazzanalyzer.domain.Chart;
import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.domain.ChordSpec;
import dev.laveenh.jazzanalyzer.domain.ChordSpec.Degree;
import dev.laveenh.jazzanalyzer.domain.ChordSpec.DegreeType;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.domain.PlacedChord;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Test helper: builds charts from plain text like "Dm7 | G7 | Cmaj7" so tests read like lead sheets. */
public final class TestCharts {

    private static final Pattern SYMBOL = Pattern.compile("([A-G][b#]?)([^/]*)(?:/([A-G][b#]?))?");
    private static final ChordClassifier CLASSIFIER = new ChordClassifier();

    private TestCharts() {
    }

    /** Measures are separated by '|', chords inside a measure by spaces, e.g. "Dm7 G7 | Cmaj7". */
    public static Chart chart(Key signature, String progression) {
        List<PlacedChord> chords = new ArrayList<>();
        String[] measures = progression.split("\\|");
        for (int m = 0; m < measures.length; m++) {
            String[] symbols = measures[m].trim().split("\\s+");
            for (int i = 0; i < symbols.length; i++) {
                double beat = 1.0 + i * (4.0 / symbols.length);
                chords.add(new PlacedChord(m + 1, beat, chord(symbols[i])));
            }
        }
        return new Chart("Test tune", signature, null, chords, measures.length, measures.length);
    }

    public static Chord chord(String symbol) {
        Matcher m = SYMBOL.matcher(symbol);
        if (!m.matches()) {
            throw new IllegalArgumentException("Bad chord symbol: " + symbol);
        }
        Note root = Note.parse(m.group(1));
        Note bass = m.group(3) == null ? null : Note.parse(m.group(3));
        String suffix = m.group(2);
        List<Degree> d = new ArrayList<>();
        String kind;
        switch (suffix) {
            case "" -> kind = "major";
            case "m" -> kind = "minor";
            case "m7" -> kind = "minor-seventh";
            case "7" -> kind = "dominant";
            case "maj7" -> kind = "major-seventh";
            case "m7b5" -> kind = "half-diminished";
            case "dim7" -> kind = "diminished-seventh";
            case "dim" -> kind = "diminished";
            case "aug" -> kind = "augmented";
            case "6" -> kind = "major-sixth";
            case "m6" -> kind = "minor-sixth";
            case "mMaj7" -> kind = "major-minor";
            case "9" -> kind = "dominant-ninth";
            case "13" -> kind = "dominant-13th";
            case "sus4" -> kind = "suspended-fourth";
            case "7sus4" -> {
                kind = "suspended-fourth";
                d.add(new Degree(7, 0, DegreeType.ADD));
            }
            case "maj7#5" -> {
                kind = "major-seventh";
                d.add(new Degree(5, 1, DegreeType.ALTER));
            }
            case "7#5" -> {
                kind = "dominant";
                d.add(new Degree(5, 1, DegreeType.ALTER));
            }
            case "7#11" -> {
                kind = "dominant";
                d.add(new Degree(11, 1, DegreeType.ADD));
            }
            case "7b9" -> {
                kind = "dominant";
                d.add(new Degree(9, -1, DegreeType.ALTER));
            }
            case "7b13" -> {
                kind = "dominant";
                d.add(new Degree(13, -1, DegreeType.ALTER));
            }
            case "7b9#11" -> {
                kind = "dominant";
                d.add(new Degree(9, -1, DegreeType.ALTER));
                d.add(new Degree(11, 1, DegreeType.ADD));
            }
            case "7alt" -> {
                return CLASSIFIER.classify(new ChordSpec(root, "dominant", "7alt", d, bass));
            }
            default -> throw new IllegalArgumentException("Test helper does not know suffix '" + suffix + "'");
        }
        return CLASSIFIER.classify(new ChordSpec(root, kind, null, d, bass));
    }
}
