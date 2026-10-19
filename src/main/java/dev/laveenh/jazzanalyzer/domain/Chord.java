package dev.laveenh.jazzanalyzer.domain;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * A normalized chord.
 *
 * @param root        root note
 * @param quality     base quality (maj7, m7, 7, ...)
 * @param extension   highest natural extension: 0 (none), 9, 11 or 13
 * @param alterations altered tones such as b9 or #11 (never contains tones folded into the quality)
 * @param bass        bass note for slash chords, or null when the bass is the root
 * @param alt         true for "altered" dominants (7alt): several altered tensions are implied
 */
public record Chord(Note root, ChordQuality quality, int extension, Set<Alteration> alterations,
                    Note bass, boolean alt) {

    public Chord {
        if (root == null || quality == null) {
            throw new IllegalArgumentException("root and quality must not be null");
        }
        if (extension != 0 && extension != 9 && extension != 11 && extension != 13) {
            throw new IllegalArgumentException("extension must be 0, 9, 11 or 13 but was " + extension);
        }
        alterations = alterations == null
                ? Set.of()
                : Collections.unmodifiableSet(new TreeSet<>(alterations));
        if (bass != null && bass.equals(root)) {
            bass = null;
        }
    }

    public static Chord of(Note root, ChordQuality quality) {
        return new Chord(root, quality, 0, Set.of(), null, false);
    }

    /** Conventional lead-sheet symbol, e.g. "Dm7", "G7b9#11", "C/E", "Bb7alt". */
    public String symbol() {
        StringBuilder sb = new StringBuilder(root.toString()).append(qualityText());
        if (bass != null) {
            sb.append('/').append(bass);
        }
        return sb.toString();
    }

    private String qualityText() {
        String suffix = quality.suffix();
        if (extension > 0) {
            if (quality.canStackExtension()) {
                // 7 becomes 9/11/13: m7 -> m9, maj7 -> maj9, 7 -> 9
                suffix = suffix.substring(0, suffix.length() - 1) + extension;
            } else if (quality == ChordQuality.MAJOR6 || quality == ChordQuality.MINOR6) {
                suffix = suffix + "/" + extension;
            } else {
                suffix = suffix + "add" + extension;
            }
        }
        if (alt) {
            return suffix + "alt";
        }
        StringBuilder sb = new StringBuilder(suffix);
        alterations.forEach(sb::append);
        return sb.toString();
    }

    @Override
    public String toString() {
        return symbol();
    }
}
