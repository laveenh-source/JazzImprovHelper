package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Alteration;
import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.domain.ChordQuality;
import dev.laveenh.jazzanalyzer.domain.ChordSpec;
import dev.laveenh.jazzanalyzer.domain.ChordSpec.Degree;
import dev.laveenh.jazzanalyzer.domain.ChordSpec.DegreeType;
import dev.laveenh.jazzanalyzer.domain.Note;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static dev.laveenh.jazzanalyzer.domain.ChordQuality.*;
import static org.assertj.core.api.Assertions.assertThat;

class ChordClassifierTest {

    private final ChordClassifier classifier = new ChordClassifier();

    private static ChordSpec spec(String kind, String text, Degree... degrees) {
        return new ChordSpec(Note.parse("C"), kind, text, List.of(degrees), null);
    }

    private static Degree add(int value, int alter) {
        return new Degree(value, alter, DegreeType.ADD);
    }

    private static Degree alter(int value, int alter) {
        return new Degree(value, alter, DegreeType.ALTER);
    }

    @ParameterizedTest(name = "{0} -> {1}, extension {2}")
    @CsvSource({
            "major,MAJOR,0", "minor,MINOR,0", "augmented,AUGMENTED,0", "diminished,DIMINISHED,0",
            "suspended-second,SUS2,0", "suspended-fourth,SUS4,0",
            "major-sixth,MAJOR6,0", "minor-sixth,MINOR6,0",
            "major-seventh,MAJOR7,0", "minor-seventh,MINOR7,0", "dominant,DOMINANT7,0",
            "major-minor,MINOR_MAJOR7,0", "half-diminished,HALF_DIMINISHED7,0",
            "diminished-seventh,DIMINISHED7,0", "augmented-seventh,AUGMENTED7,0",
            "dominant-ninth,DOMINANT7,9", "major-ninth,MAJOR7,9", "minor-ninth,MINOR7,9",
            "dominant-11th,DOMINANT7,11", "major-11th,MAJOR7,11", "minor-11th,MINOR7,11",
            "dominant-13th,DOMINANT7,13", "major-13th,MAJOR7,13", "minor-13th,MINOR7,13"})
    void mapsEveryMusicXmlKind(String kind, ChordQuality quality, int extension) {
        Chord chord = classifier.classify(spec(kind, null));
        assertThat(chord.quality()).isEqualTo(quality);
        assertThat(chord.extension()).isEqualTo(extension);
        assertThat(chord.alt()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"neapolitan", "italian", "french", "german", "pedal", "power", "tristan", "other", "garbage", "''"})
    void unmodelledKindsBecomeUnknownInsteadOfFailing(String kind) {
        assertThat(classifier.classify(spec(kind, null)).quality()).isEqualTo(UNKNOWN);
    }

    @Test
    void nullKindIsUnknown() {
        assertThat(classifier.classify(spec(null, null)).quality()).isEqualTo(UNKNOWN);
    }

    @Test
    void singleAlterationsStayAsAlterations() {
        Chord b9 = classifier.classify(spec("dominant", null, alter(9, -1)));
        assertThat(b9.quality()).isEqualTo(DOMINANT7);
        assertThat(b9.alterations()).containsExactly(new Alteration(9, -1));
        assertThat(b9.alt()).isFalse();
        assertThat(b9.symbol()).isEqualTo("C7b9");

        Chord sharp11 = classifier.classify(spec("dominant", null, add(11, 1)));
        assertThat(sharp11.alterations()).containsExactly(new Alteration(11, 1));
        assertThat(sharp11.alt()).isFalse();

        Chord flat13 = classifier.classify(spec("dominant", null, alter(13, -1)));
        assertThat(flat13.alt()).isFalse();
        assertThat(flat13.symbol()).isEqualTo("C7b13");
    }

    @Test
    void twoAlteredTensionsMakeADominantAlt() {
        Chord chord = classifier.classify(spec("dominant", null, alter(9, -1), alter(9, 1)));
        assertThat(chord.alt()).isTrue();
        assertThat(chord.symbol()).isEqualTo("C7alt");
    }

    @Test
    void sharp11AloneDoesNotCountTowardsAlt() {
        Chord chord = classifier.classify(spec("dominant", null, alter(9, -1), add(11, 1)));
        assertThat(chord.alt()).isFalse();
        assertThat(chord.symbol()).isEqualTo("C7b9#11");
    }

    @ParameterizedTest
    @CsvSource({"7alt", "7ALT", "alt", "C7alt"})
    void altInPrintedTextMakesADominantAlt(String text) {
        assertThat(classifier.classify(spec("dominant", text)).alt()).isTrue();
    }

    @Test
    void altTextOnNonDominantIsIgnored() {
        assertThat(classifier.classify(spec("minor-seventh", "alt")).alt()).isFalse();
    }

    @Test
    void foldsFlat5IntoMinor7AsHalfDiminished() {
        Chord chord = classifier.classify(spec("minor-seventh", null, alter(5, -1)));
        assertThat(chord.quality()).isEqualTo(HALF_DIMINISHED7);
        assertThat(chord.alterations()).isEmpty();
    }

    @Test
    void foldsSharp5IntoMajor7() {
        Chord chord = classifier.classify(spec("major-seventh", null, alter(5, 1)));
        assertThat(chord.quality()).isEqualTo(MAJOR7_SHARP5);
        assertThat(chord.alterations()).isEmpty();
        assertThat(chord.symbol()).isEqualTo("Cmaj7#5");
    }

    @Test
    void loneSharp5OnDominantIsAugmentedSeventh() {
        Chord chord = classifier.classify(spec("dominant", null, alter(5, 1)));
        assertThat(chord.quality()).isEqualTo(AUGMENTED7);
        assertThat(chord.alterations()).isEmpty();
    }

    @Test
    void naturalHighDegreesBecomeExtensions() {
        assertThat(classifier.classify(spec("dominant", null, add(9, 0))).extension()).isEqualTo(9);
        assertThat(classifier.classify(spec("dominant-ninth", null, add(13, 0))).extension()).isEqualTo(13);
        // a degree never lowers an existing extension
        assertThat(classifier.classify(spec("dominant-13th", null, add(9, 0))).extension()).isEqualTo(13);
    }

    @Test
    void addSixMakesSixthChords() {
        assertThat(classifier.classify(spec("major", null, add(6, 0))).quality()).isEqualTo(MAJOR6);
        assertThat(classifier.classify(spec("minor", null, add(6, 0))).quality()).isEqualTo(MINOR6);
        Chord sixNine = classifier.classify(spec("major-sixth", null, add(9, 0)));
        assertThat(sixNine.symbol()).isEqualTo("C6/9");
    }

    @Test
    void addSevenOnSus4MakesDominantSus() {
        assertThat(classifier.classify(spec("suspended-fourth", null, add(7, 0))).quality())
                .isEqualTo(DOMINANT7_SUS4);
    }

    @Test
    void otherPlainDegreesAreIgnored() {
        assertThat(classifier.classify(spec("diminished", null, add(6, 0))).quality()).isEqualTo(DIMINISHED);
        assertThat(classifier.classify(spec("dominant", null, add(7, 0))).quality()).isEqualTo(DOMINANT7);
    }

    @Test
    void subtractedDegreesAreIgnored() {
        Chord chord = classifier.classify(spec("dominant-13th", null, new Degree(11, 0, DegreeType.SUBTRACT)));
        assertThat(chord.quality()).isEqualTo(DOMINANT7);
        assertThat(chord.extension()).isEqualTo(13);
        assertThat(chord.alterations()).isEmpty();
    }

    @Test
    void keepsRootAndBass() {
        ChordSpec slash = new ChordSpec(Note.parse("Bb"), "major", null, List.of(), Note.parse("D"));
        Chord chord = classifier.classify(slash);
        assertThat(chord.root()).isEqualTo(Note.parse("Bb"));
        assertThat(chord.bass()).isEqualTo(Note.parse("D"));
        assertThat(chord.symbol()).isEqualTo("Bb/D");
    }

    @Test
    void tableLoadsAllKindsFromTheDataFile() {
        ChordKindTable table = ChordKindTable.loadDefault();
        assertThat(table.lookup("dominant-13th"))
                .isEqualTo(new ChordKindTable.Entry(DOMINANT7, 13));
        assertThat(table.lookup(" major-seventh ").quality()).isEqualTo(MAJOR7);
        assertThat(Set.of(table.lookup("nope").quality())).containsExactly(UNKNOWN);
    }

    @Test
    void tensionsOnAPlainMajorTriadImplyAFlatSeventh() {
        assertThat(classifier.classify(spec("major", null, alter(9, -1))).quality()).isEqualTo(DOMINANT7);
        assertThat(classifier.classify(spec("major", null, add(11, 1))).symbol()).isEqualTo("C7#11");
        assertThat(classifier.classify(spec("major", null, add(13, 0))))
                .satisfies(c -> {
                    assertThat(c.quality()).isEqualTo(DOMINANT7);
                    assertThat(c.symbol()).isEqualTo("C13");
                });
        // a lone #5 becomes C7#5 (augmented seventh), not a plain C#5 triad
        assertThat(classifier.classify(spec("major", null, alter(5, 1))).quality()).isEqualTo(AUGMENTED7);
    }

    @Test
    void tensionsOnAMinorTriadImplyAMinorSeventh() {
        assertThat(classifier.classify(spec("minor", null, add(9, 0))).symbol()).isEqualTo("Cm9");
        assertThat(classifier.classify(spec("minor", null, alter(5, -1))).quality()).isEqualTo(HALF_DIMINISHED7);
    }

    @Test
    void aMajorSeventhIsNeverImplied() {
        assertThat(classifier.classify(spec("major-seventh", null, add(9, 0))).symbol()).isEqualTo("Cmaj9");
        assertThat(classifier.classify(spec("major-seventh", null, add(11, 1))).quality()).isEqualTo(MAJOR7);
        assertThat(classifier.classify(spec("major-seventh", null, alter(5, 1))).quality()).isEqualTo(MAJOR7_SHARP5);
    }

    @Test
    void sixthChordsAndOtherTriadsAreNotTurnedIntoSevenths() {
        assertThat(classifier.classify(spec("major-sixth", null, add(9, 0))).symbol()).isEqualTo("C6/9");
        assertThat(classifier.classify(spec("diminished", null, alter(5, -1))).quality()).isEqualTo(DIMINISHED);
        assertThat(classifier.classify(spec("major", null)).quality()).isEqualTo(MAJOR);
    }
}
