package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.ChordFunction;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.Mode;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.domain.PlacedChord;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static dev.laveenh.jazzanalyzer.TestCharts.chart;
import static dev.laveenh.jazzanalyzer.domain.ChordFunction.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionAnalyzerTest {

    private final FunctionAnalyzer analyzer = new FunctionAnalyzer();

    /** Analyses the progression with every chord forced into the given key; returns results in order. */
    private List<FunctionResult> analyze(String key, String progression) {
        String[] parts = key.split(" ");
        Key k = new Key(Note.parse(parts[0]), Mode.valueOf(parts[1].toUpperCase()));
        List<PlacedChord> chords = chart(null, progression).chords();
        return analyzer.analyze(chords, Collections.nCopies(chords.size(), k));
    }

    private FunctionResult only(String key, String progression, int index) {
        return analyze(key, progression).get(index);
    }

    @ParameterizedTest(name = "{0} in {1}: {2} / {3}")
    @CsvSource({
            // the diatonic chords of C major
            "Cmaj7,'C major',Imaj7,TONIC", "C6,'C major',I6,TONIC", "C,'C major',I,TONIC",
            "Dm7,'C major',ii7,SUBDOMINANT", "Em7,'C major',iii7,TONIC", "Fmaj7,'C major',IVmaj7,SUBDOMINANT",
            "Am7,'C major',vi7,TONIC", "Bm7b5,'C major',viiø7,DOMINANT", "G,'C major',V,DOMINANT",
            "Cmaj7#5,'C major',Imaj7#5,TONIC", "Gsus4,'C major',Vsus4,DOMINANT", "G7sus4,'C major',V7sus4,DOMINANT",
            // minor key
            "Am7,'A minor',i7,TONIC", "AmMaj7,'A minor',i(maj7),TONIC", "Bm7b5,'A minor',iiø7,SUBDOMINANT",
            "Cmaj7,'A minor',bIIImaj7,TONIC", "Dm7,'A minor',iv7,SUBDOMINANT", "E7,'A minor',V7,DOMINANT",
            "Fmaj7,'A minor',bVImaj7,SUBDOMINANT", "Gmaj7,'A minor',bVIImaj7,SUBDOMINANT",
            "G#dim7,'A minor',viio7,DOMINANT"
    })
    void classifiesDiatonicChords(String symbol, String key, String roman, ChordFunction function) {
        // the lone chord has no next chord, so nothing can be a secondary dominant
        FunctionResult r = only(key, symbol, 0);
        assertThat(r.roman()).isEqualTo(roman.replace("o", "°"));
        assertThat(r.function()).isEqualTo(function);
    }

    @Test
    void diatonicDominantIsVEvenWhenItDoesNotResolveHome() {
        assertThat(only("C major", "G7 | Em7", 0).roman()).isEqualTo("V7");
        assertThat(only("C major", "G7 | Em7", 0).function()).isEqualTo(DOMINANT);
    }

    @Test
    void secondaryDominantsNameTheirTarget() {
        assertThat(only("C major", "A7 | Dm7", 0))
                .satisfies(r -> {
                    assertThat(r.roman()).isEqualTo("V7/ii");
                    assertThat(r.function()).isEqualTo(SECONDARY_DOMINANT);
                    assertThat(r.resolution()).isEqualTo(Resolution.MINOR);
                });
        assertThat(only("C major", "D7 | G7", 0).roman()).isEqualTo("V7/V");
        assertThat(only("C major", "E7 | Am7", 0).roman()).isEqualTo("V7/vi");
        assertThat(only("C major", "E7 | Am7", 0).resolution()).isEqualTo(Resolution.MINOR);
        assertThat(only("C major", "C7 | Fmaj7", 0).roman()).isEqualTo("V7/IV");
        assertThat(only("C major", "B7 | Em7", 0).roman()).isEqualTo("V7/iii");
        assertThat(only("C major", "D7 | Gmaj7", 0).resolution()).isEqualTo(Resolution.MAJOR);
    }

    @Test
    void alteredAndExtendedSecondaryDominantsKeepTheirTensions() {
        assertThat(only("C major", "A7b9 | Dm7", 0).roman()).isEqualTo("V7b9/ii");
        assertThat(only("C major", "A7alt | Dm7", 0).roman()).isEqualTo("V7alt/ii");
        assertThat(only("C major", "A13 | Dm7", 0).roman()).isEqualTo("V13/ii");
    }

    @Test
    void aDominantWithNoNextChordCannotBeASecondaryDominant() {
        FunctionResult r = only("C major", "Dm7 | A7", 1);
        assertThat(r.function()).isEqualTo(UNCLASSIFIED);
        assertThat(r.roman()).isEqualTo("VI7");
    }

    @Test
    void tritoneSubstitutionOfTheFiveIsBII7() {
        FunctionResult r = only("C major", "Dm7 | Db7 | Cmaj7", 1);
        assertThat(r.roman()).isEqualTo("bII7");
        assertThat(r.function()).isEqualTo(TRITONE_SUBSTITUTION);
        assertThat(r.resolution()).isEqualTo(Resolution.MAJOR);
    }

    @Test
    void tritoneSubstitutionOfASecondaryDominantNamesItsTarget() {
        // Eb7 -> Dm7: a tritone sub of A7 (V7/ii)
        FunctionResult r = only("C major", "Eb7 | Dm7", 0);
        assertThat(r.function()).isEqualTo(TRITONE_SUBSTITUTION);
        assertThat(r.roman()).isEqualTo("subV7/ii");
    }

    @Test
    void diminishedPassingChordsMoveByHalfStep() {
        FunctionResult up = only("C major", "Cmaj7 | C#dim7 | Dm7", 1);
        assertThat(up.function()).isEqualTo(DIMINISHED_PASSING);
        assertThat(up.roman()).isEqualTo("#i°7");

        FunctionResult down = only("C major", "Em7 | Ebdim7 | Dm7", 1);
        assertThat(down.function()).isEqualTo(DIMINISHED_PASSING);
        assertThat(down.roman()).isEqualTo("#ii°7");
    }

    @Test
    void aDiminishedChordThatGoesNowhereIsUnclassified() {
        assertThat(only("C major", "Cmaj7 | C#dim7 | Fmaj7", 1).function()).isEqualTo(UNCLASSIFIED);
    }

    @ParameterizedTest(name = "{0} in {1} is borrowed: {2}")
    @CsvSource({
            "Cm7,'C major',i7", "Fm7,'C major',iv7", "Fm,'C major',iv", "Ebmaj7,'C major',bIIImaj7",
            "Abmaj7,'C major',bVImaj7", "Bbmaj7,'C major',bVIImaj7", "Bb7,'C major',bVII7",
            "Dm7b5,'C major',iiø7", "Gm7,'C major',v7", "Dbmaj7,'C major',bIImaj7",
            "Cmaj7,'C minor',Imaj7", "Fmaj7,'C minor',IVmaj7", "F7,'C minor',IV7", "Dbmaj7,'C minor',bIImaj7",
            "Am7,'C minor',vi7", "Em7,'C minor',iii7"
    })
    void modalInterchange(String symbol, String key, String roman) {
        FunctionResult r = only(key, symbol, 0);
        assertThat(r.function()).isEqualTo(MODAL_INTERCHANGE);
        assertThat(r.roman()).isEqualTo(roman);
    }

    @Test
    void aChordNothingExplainsIsUnclassifiedButStillGetsANumeral() {
        FunctionResult r = only("C major", "F#maj7 | Cmaj7", 0);
        assertThat(r.function()).isEqualTo(UNCLASSIFIED);
        assertThat(r.roman()).isEqualTo("#IVmaj7");
        assertThat(r.reason()).contains("Not explained");
    }

    @Test
    void chordsOfAnUnmodelledKindAreUnclassified() {
        List<PlacedChord> chords = List.of(new PlacedChord(1, 1.0,
                new dev.laveenh.jazzanalyzer.domain.Chord(Note.parse("C"),
                        dev.laveenh.jazzanalyzer.domain.ChordQuality.UNKNOWN, 0, null, null, false)));
        FunctionResult r = analyzer.analyze(chords, List.of(new Key(Note.parse("C"), Mode.MAJOR))).get(0);
        assertThat(r.function()).isEqualTo(UNCLASSIFIED);
        assertThat(r.roman()).isEqualTo("?");
    }

    @Test
    void sameRootChangesAreNotMovesWhenLookingForTheResolution() {
        // G7 | G7sus4 | Cmaj7 : the dominant still resolves to the C chord
        assertThat(only("C major", "G7 | G7sus4 | Cmaj7", 0).resolution()).isEqualTo(Resolution.MAJOR);
    }

    @Test
    void extensionsShowInTheNumeral() {
        assertThat(only("C major", "G9 | Cmaj7", 0).roman()).isEqualTo("V9");
        assertThat(only("C major", "G7b9 | Cmaj7", 0).roman()).isEqualTo("V7b9");
        assertThat(only("C major", "G7alt | Cmaj7", 0).roman()).isEqualTo("V7alt");
        assertThat(only("C major", "G7#5 | Cmaj7", 0).roman()).isEqualTo("V+7");
    }

    @Test
    void thereMustBeOneKeyPerChord() {
        assertThatThrownBy(() -> analyzer.analyze(chart(null, "C | F").chords(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void useTheLocalKeyOfEachChord() {
        List<PlacedChord> chords = chart(null, "Dm7 | G7 | Cmaj7 | Fm7 | Bb7 | Ebmaj7").chords();
        Key c = new Key(Note.parse("C"), Mode.MAJOR);
        Key eb = new Key(Note.parse("Eb"), Mode.MAJOR);
        List<FunctionResult> r = analyzer.analyze(chords, List.of(c, c, c, eb, eb, eb));
        assertThat(r).extracting(FunctionResult::roman).containsExactly("ii7", "V7", "Imaj7", "ii7", "V7", "Imaj7");
    }
}
