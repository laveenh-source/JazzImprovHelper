package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Alteration;
import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.domain.ChordQuality;
import dev.laveenh.jazzanalyzer.domain.ChordSpec;
import dev.laveenh.jazzanalyzer.domain.ChordSpec.Degree;
import dev.laveenh.jazzanalyzer.domain.ChordSpec.DegreeType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Normalizes the raw pieces of a chord symbol (kind + degrees) into a {@link Chord}.
 *
 * <p>The same chord can be written many ways in MusicXML; for example Cmaj7#5 may arrive as
 * kind=major-seventh plus a "#5" degree. This class folds such cases into one canonical form
 * so the rest of the analysis only deals with one representation.
 */
public final class ChordClassifier {

    /** Dominants with at least this many of the altered tensions (b9 #9 b5 #5 b13) count as "alt". */
    private static final int ALT_THRESHOLD = 2;

    private final ChordKindTable kinds;

    public ChordClassifier() {
        this(ChordKindTable.loadDefault());
    }

    public ChordClassifier(ChordKindTable kinds) {
        this.kinds = kinds;
    }

    public Chord classify(ChordSpec spec) {
        ChordKindTable.Entry entry = kinds.lookup(spec.kind());
        ChordQuality quality = entry.quality();
        int extension = entry.extension();
        Set<Alteration> alterations = new HashSet<>();

        for (Degree degree : spec.degrees()) {
            if (degree.type() == DegreeType.SUBTRACT) {
                continue; // omitted tones don't change the harmonic function we analyse
            }
            if (degree.alter() != 0) {
                alterations.add(new Alteration(degree.value(), degree.alter()));
            } else if (degree.value() >= 9) {
                extension = Math.max(extension, degree.value());
            } else {
                quality = applyPlainDegree(quality, degree.value());
            }
        }

        quality = impliedSeventh(quality, extension, alterations);

        boolean alt = quality == ChordQuality.DOMINANT7 && isAlt(spec.kindText(), alterations);
        if (!alt) {
            quality = foldAlterationsIntoQuality(quality, alterations);
        }
        return new Chord(spec.root(), quality, extension, alterations, spec.bass(), alt);
    }

    /**
     * Lead-sheet convention: a plain triad that carries an extension or alteration (C9, C13, C#11, C+7#5...)
     * implies a flat 7th, so a major triad becomes a dominant 7 and a minor triad a minor 7.
     * A major 7th is never implied; it has to be written (kind major-seventh), and then this does not apply.
     */
    private static ChordQuality impliedSeventh(ChordQuality quality, int extension, Set<Alteration> alterations) {
        boolean hasTension = extension > 0 || !alterations.isEmpty();
        if (!hasTension) {
            return quality;
        }
        return switch (quality) {
            case MAJOR -> ChordQuality.DOMINANT7;
            case MINOR -> ChordQuality.MINOR7;
            default -> quality;
        };
    }

    /** "add 6" turns a triad into a 6th chord; "add 7" on a sus4 makes it 7sus4. */
    private static ChordQuality applyPlainDegree(ChordQuality quality, int degreeValue) {
        return switch (quality) {
            case MAJOR -> degreeValue == 6 ? ChordQuality.MAJOR6 : quality;
            case MINOR -> degreeValue == 6 ? ChordQuality.MINOR6 : quality;
            case SUS4 -> degreeValue == 7 ? ChordQuality.DOMINANT7_SUS4 : quality;
            default -> quality;
        };
    }

    private static boolean isAlt(String kindText, Set<Alteration> alterations) {
        if (kindText != null && kindText.toLowerCase(Locale.ROOT).contains("alt")) {
            return true;
        }
        long alteredTensions = alterations.stream().filter(ChordClassifier::isAltTension).count();
        return alteredTensions >= ALT_THRESHOLD;
    }

    private static boolean isAltTension(Alteration a) {
        return (a.degree() == 9)                          // b9, #9
                || (a.degree() == 5)                      // b5, #5
                || (a.degree() == 13 && a.shift() < 0);   // b13
    }

    /** m7 + b5 is m7b5, maj7 + #5 is maj7#5, and a lone #5 on a dominant is an augmented 7th. */
    private static ChordQuality foldAlterationsIntoQuality(ChordQuality quality, Set<Alteration> alterations) {
        Alteration flat5 = new Alteration(5, -1);
        Alteration sharp5 = new Alteration(5, 1);
        if (quality == ChordQuality.MINOR7 && alterations.remove(flat5)) {
            return ChordQuality.HALF_DIMINISHED7;
        }
        if (quality == ChordQuality.MAJOR7 && alterations.remove(sharp5)) {
            return ChordQuality.MAJOR7_SHARP5;
        }
        if (quality == ChordQuality.DOMINANT7 && alterations.remove(sharp5)) {
            return ChordQuality.AUGMENTED7;
        }
        return quality;
    }
}
