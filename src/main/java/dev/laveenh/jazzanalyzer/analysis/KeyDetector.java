package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Chart;
import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.domain.ChordQuality;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.KeyRegion;
import dev.laveenh.jazzanalyzer.domain.Mode;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.domain.PlacedChord;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the key of every chord, and groups chords into key regions (modulations).
 *
 * <p>The approach is a small scoring model, not machine learning:
 * <ol>
 *   <li>Each chord gets a score for each of the 24 keys: how many of its chord tones are in the
 *       key's scale, plus bonuses for being a tonic chord and for matching the key signature.</li>
 *       <li>Every ii-V-I / ii-V-i found in the chord list adds a big bonus to its target key for the
 *       chords involved. This is how a modulation is recognised.</li>
 *   <li>A dynamic-programming pass picks one key per chord that maximises the total score, where
 *       changing key costs a fixed penalty. The penalty stops the answer flickering between keys
 *       for a single chromatic chord; it also means a key region needs enough evidence to exist.</li>
 * </ol>
 * The first chord of the tune gets no real weight: tunes often open on IV or ii. The key signature
 * and the resolving chords decide, and the first chord is only a last-resort tie-break.
 */
public final class KeyDetector {

    private static final double MAJOR_PATTERN_BONUS = 1.5;
    /** Lower: a ii-V-i inside a major tune is usually a tonicised ii/iii/vi, not a modulation. */
    private static final double MINOR_PATTERN_BONUS = 1.0;
    private static final double SWITCH_PENALTY = 2.0;
    private static final double SIGNATURE_BONUS = 0.6;
    private static final double RELATIVE_SIGNATURE_BONUS = 0.25;
    private static final double TONIC_CHORD_BONUS = 0.25;
    private static final double FINAL_TONIC_BONUS = 0.5;
    private static final double FIRST_CHORD_TIE_BREAK = 0.01;

    private static final int KEY_COUNT = 24; // 0-11 major keys, 12-23 minor keys, by tonic pitch class

    private static final String[] MAJOR_NAMES = {"C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};
    private static final String[] MINOR_NAMES = {"C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B"};

    private static final int[] MAJOR_SCALE = {0, 2, 4, 5, 7, 9, 11};
    /** Natural minor plus the raised 7th (harmonic minor), since V7 in minor needs the leading tone. */
    private static final int[] MINOR_SCALE = {0, 2, 3, 5, 7, 8, 10, 11};

    /** A ii-V-I (or ii-V-i, or ii-bII7-I) found in the chords. */
    record Pattern(int startChord, int endChord, int keyIndex, String name) {
    }

    public KeyDetection detect(Chart chart) {
        List<PlacedChord> chords = chart.chords();
        int n = chords.size();
        List<Pattern> patterns = findPatterns(chords);

        double[][] score = localScores(chart, patterns);
        int[] path = bestPath(score);

        List<KeyRegion> regions = buildRegions(chart, path, patterns);
        List<Key> chordKeys = new ArrayList<>(n);
        int region = 0;
        int regionEnd = lastChordOfRun(path, 0);
        for (int i = 0; i < n; i++) {
            if (i > regionEnd) {
                region++;
                regionEnd = lastChordOfRun(path, i);
            }
            chordKeys.add(regions.get(region).key());
        }
        return new KeyDetection(regions, chordKeys);
    }

    // ------------------------------------------------------------ pattern finding

    List<Pattern> findPatterns(List<PlacedChord> chords) {
        // Work on "harmonic events": repeated identical chords (Dm7 | Dm7) count once.
        List<Integer> events = new ArrayList<>();
        for (int i = 0; i < chords.size(); i++) {
            if (i == 0 || !sameChord(chords.get(i).chord(), chords.get(i - 1).chord())) {
                events.add(i);
            }
        }
        List<Pattern> patterns = new ArrayList<>();
        for (int e = 0; e + 2 < events.size(); e++) {
            Chord two = chords.get(events.get(e)).chord();
            Chord five = chords.get(events.get(e + 1)).chord();
            Chord one = chords.get(events.get(e + 2)).chord();

            boolean twoOk = two.quality().isMinorish() || two.quality() == ChordQuality.HALF_DIMINISHED7;
            boolean fiveOk = five.quality().isDominantFamily();
            if (!twoOk || !fiveOk) {
                continue;
            }
            int twoRoot = two.root().pitchClass();
            int fiveInterval = Math.floorMod(five.root().pitchClass() - twoRoot, 12);
            int oneInterval = Math.floorMod(one.root().pitchClass() - twoRoot, 12);
            boolean regularV = fiveInterval == 5 && oneInterval == 10;      // up a fourth, then up a fourth
            boolean tritoneSub = fiveInterval == 11 && oneInterval == 10;   // down a half step, then down a half step
            if (!regularV && !tritoneSub) {
                continue;
            }
            int oneRoot = one.root().pitchClass();
            String name = tritoneSub ? "ii-bII7-" : "ii-V-";
            if (one.quality().isMajorish()) {
                patterns.add(new Pattern(events.get(e), events.get(e + 2), oneRoot, name + "I"));
            } else if (one.quality().isMinorish()) {
                patterns.add(new Pattern(events.get(e), events.get(e + 2), 12 + oneRoot, name + "i"));
            }
        }
        return patterns;
    }

    private static boolean sameChord(Chord a, Chord b) {
        return a.root().pitchClass() == b.root().pitchClass() && a.quality() == b.quality();
    }

    // ------------------------------------------------------------ scoring

    private double[][] localScores(Chart chart, List<Pattern> patterns) {
        List<PlacedChord> chords = chart.chords();
        int n = chords.size();
        double[][] score = new double[n][KEY_COUNT];

        int signatureKey = chart.keySignature() == null ? -1 : keyIndex(chart.keySignature());
        int relativeKey = signatureKey < 0 ? -1 : relativeOf(signatureKey);

        for (int i = 0; i < n; i++) {
            Chord chord = chords.get(i).chord();
            for (int k = 0; k < KEY_COUNT; k++) {
                double s = fit(chord, k);
                if (isTonicChord(chord, k)) {
                    s += TONIC_CHORD_BONUS;
                    if (i == n - 1) {
                        s += FINAL_TONIC_BONUS;
                    }
                }
                if (i == 0 && chord.root().pitchClass() == k % 12) {
                    s += FIRST_CHORD_TIE_BREAK;
                }
                if (k == signatureKey) {
                    s += SIGNATURE_BONUS;
                } else if (k == relativeKey) {
                    s += RELATIVE_SIGNATURE_BONUS;
                }
                score[i][k] = s;
            }
        }
        for (Pattern p : patterns) {
            double bonus = p.keyIndex() >= 12 ? MINOR_PATTERN_BONUS : MAJOR_PATTERN_BONUS;
            for (int i = p.startChord(); i <= p.endChord(); i++) {
                score[i][p.keyIndex()] += bonus;
            }
        }
        return score;
    }

    /** Fraction of the chord's basic tones that belong to the key's scale (0-1). */
    static double fit(Chord chord, int keyIndex) {
        int[] scale = keyIndex < 12 ? MAJOR_SCALE : MINOR_SCALE;
        int tonic = keyIndex % 12;
        int[] intervals = chord.quality().intervals();
        int inKey = 0;
        for (int interval : intervals) {
            int degree = Math.floorMod(chord.root().pitchClass() + interval - tonic, 12);
            for (int s : scale) {
                if (s == degree) {
                    inKey++;
                    break;
                }
            }
        }
        return (double) inKey / intervals.length;
    }

    private static boolean isTonicChord(Chord chord, int keyIndex) {
        if (chord.root().pitchClass() != keyIndex % 12) {
            return false;
        }
        return keyIndex < 12 ? chord.quality().isMajorish() : chord.quality().isMinorish();
    }

    private static int keyIndex(Key key) {
        return key.tonic().pitchClass() + (key.mode() == Mode.MINOR ? 12 : 0);
    }

    private static int relativeOf(int keyIndex) {
        int tonic = keyIndex % 12;
        return keyIndex < 12 ? 12 + (tonic + 9) % 12 : (tonic + 3) % 12;
    }

    // ------------------------------------------------------------ best key sequence

    /** Viterbi-style dynamic programming: best key per chord with a penalty for changing key. */
    private static int[] bestPath(double[][] score) {
        int n = score.length;
        double[][] best = new double[n][KEY_COUNT];
        int[][] from = new int[n][KEY_COUNT];
        best[0] = score[0].clone();
        for (int i = 1; i < n; i++) {
            int bestPrev = argMax(best[i - 1]);
            for (int k = 0; k < KEY_COUNT; k++) {
                double stay = best[i - 1][k];
                double jump = best[i - 1][bestPrev] - SWITCH_PENALTY;
                if (stay >= jump) {
                    best[i][k] = stay + score[i][k];
                    from[i][k] = k;
                } else {
                    best[i][k] = jump + score[i][k];
                    from[i][k] = bestPrev;
                }
            }
        }
        int[] path = new int[n];
        path[n - 1] = argMax(best[n - 1]);
        for (int i = n - 1; i > 0; i--) {
            path[i - 1] = from[i][path[i]];
        }
        return path;
    }

    private static int argMax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ regions

    private static int lastChordOfRun(int[] path, int start) {
        int end = start;
        while (end + 1 < path.length && path[end + 1] == path[start]) {
            end++;
        }
        return end;
    }

    private List<KeyRegion> buildRegions(Chart chart, int[] path, List<Pattern> patterns) {
        List<PlacedChord> chords = chart.chords();
        List<KeyRegion> regions = new ArrayList<>();
        int start = 0;
        while (start < chords.size()) {
            int end = lastChordOfRun(path, start);
            int keyIndex = path[start];
            Key key = spell(keyIndex, chords, start, end, chart.keySignature());

            int startMeasure = chords.get(start).measure();
            int endMeasure;
            if (end + 1 < chords.size()) {
                endMeasure = Math.max(chords.get(end).measure(), chords.get(end + 1).measure() - 1);
            } else {
                endMeasure = Math.max(chords.get(end).measure(), chart.lastMeasure());
            }
            regions.add(new KeyRegion(key, startMeasure, endMeasure,
                    confidence(chords, start, end, keyIndex, patterns),
                    reason(chords, start, end, keyIndex, patterns, chart.keySignature())));
            start = end + 1;
        }
        return regions;
    }

    /** Spell the tonic the way the chart does: a tonic chord's own root, else the key signature, else a default. */
    private static Key spell(int keyIndex, List<PlacedChord> chords, int start, int end, Key signature) {
        Mode mode = keyIndex < 12 ? Mode.MAJOR : Mode.MINOR;
        for (int i = start; i <= end; i++) {
            Chord chord = chords.get(i).chord();
            if (isTonicChord(chord, keyIndex)) {
                return new Key(chord.root(), mode);
            }
        }
        if (signature != null && keyIndex(signature) == keyIndex) {
            return signature;
        }
        String name = (mode == Mode.MAJOR ? MAJOR_NAMES : MINOR_NAMES)[keyIndex % 12];
        return new Key(Note.parse(name), mode);
    }

    private static List<Pattern> patternsInside(List<Pattern> patterns, int start, int end, int keyIndex) {
        return patterns.stream()
                .filter(p -> p.keyIndex() == keyIndex && p.startChord() >= start && p.endChord() <= end)
                .toList();
    }

    /** Heuristic 0-1 score: 60% diatonic fit, 25% a cadence was found, 15% a tonic chord is present. */
    private static double confidence(List<PlacedChord> chords, int start, int end, int keyIndex, List<Pattern> patterns) {
        double fitSum = 0;
        boolean tonicPresent = false;
        for (int i = start; i <= end; i++) {
            Chord chord = chords.get(i).chord();
            fitSum += fit(chord, keyIndex);
            tonicPresent |= isTonicChord(chord, keyIndex);
        }
        double averageFit = fitSum / (end - start + 1);
        boolean cadence = !patternsInside(patterns, start, end, keyIndex).isEmpty();
        double value = 0.6 * averageFit + 0.25 * (cadence ? 1 : 0) + 0.15 * (tonicPresent ? 1 : 0);
        return Math.round(value * 100) / 100.0;
    }

    private static String reason(List<PlacedChord> chords, int start, int end, int keyIndex,
                                List<Pattern> patterns, Key signature) {
        List<Pattern> inside = patternsInside(patterns, start, end, keyIndex);
        if (!inside.isEmpty()) {
            Pattern p = inside.get(0);
            String text = p.name() + " in measures " + chords.get(p.startChord()).measure()
                    + "-" + chords.get(p.endChord()).measure();
            return inside.size() > 1 ? text + " (and " + (inside.size() - 1) + " more)" : text;
        }
        int diatonic = 0;
        for (int i = start; i <= end; i++) {
            if (fit(chords.get(i).chord(), keyIndex) == 1.0) {
                diatonic++;
            }
        }
        String text = "No ii-V-I found; best diatonic fit (" + diatonic + " of " + (end - start + 1) + " chords)";
        if (signature != null && keyIndex(signature) == keyIndex) {
            text += ", matches the key signature";
        }
        return text;
    }
}
