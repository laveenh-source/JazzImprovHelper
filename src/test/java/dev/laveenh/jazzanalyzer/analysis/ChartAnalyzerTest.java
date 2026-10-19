package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.ChartAnalysis;
import dev.laveenh.jazzanalyzer.domain.ChordAnalysis;
import dev.laveenh.jazzanalyzer.domain.ChordFunction;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.KeyRegion;
import dev.laveenh.jazzanalyzer.domain.Mode;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.domain.ScaleSuggestion;
import dev.laveenh.jazzanalyzer.parser.MusicXmlParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static dev.laveenh.jazzanalyzer.TestCharts.chart;
import static org.assertj.core.api.Assertions.assertThat;

/** The worked examples from the project description, run through the whole pipeline. */
class ChartAnalyzerTest {

    private static final Key C_MAJOR = new Key(Note.parse("C"), Mode.MAJOR);
    private static final Key A_MINOR = new Key(Note.parse("A"), Mode.MINOR);

    private final ChartAnalyzer analyzer = new ChartAnalyzer();

    private static List<String> scales(ChordAnalysis chord) {
        return chord.scales().stream().map(ScaleSuggestion::scaleType).toList();
    }

    @Test
    void example1_iiVI_inCMajor() {
        ChartAnalysis a = analyzer.analyze(chart(C_MAJOR, "Dm7 | G7 | Cmaj7"));

        assertThat(a.mainKey()).hasToString("C major");
        assertThat(a.keyRegions()).hasSize(1);
        assertThat(a.keyRegions().get(0).reason()).isEqualTo("ii-V-I in measures 1-3");

        ChordAnalysis dm7 = a.chords().get(0);
        assertThat(dm7.roman()).isEqualTo("ii7");
        assertThat(dm7.function()).isEqualTo(ChordFunction.SUBDOMINANT);
        assertThat(dm7.localKey()).hasToString("C major");
        assertThat(scales(dm7)).containsExactly("Dorian");
        assertThat(dm7.scales().get(0).name()).isEqualTo("D Dorian");

        ChordAnalysis g7 = a.chords().get(1);
        assertThat(g7.roman()).isEqualTo("V7");
        assertThat(g7.function()).isEqualTo(ChordFunction.DOMINANT);
        assertThat(scales(g7)).containsExactly("Mixolydian", "Altered");

        ChordAnalysis cmaj7 = a.chords().get(2);
        assertThat(cmaj7.roman()).isEqualTo("Imaj7");
        assertThat(cmaj7.function()).isEqualTo(ChordFunction.TONIC);
        assertThat(scales(cmaj7)).containsExactly("Ionian", "Lydian");
    }

    @Test
    void example2_secondaryDominantResolvingToAMinorChord() {
        ChartAnalysis a = analyzer.analyze(chart(C_MAJOR, "Dm7 | A7 | Dm7"));

        assertThat(a.mainKey()).hasToString("C major");
        ChordAnalysis a7 = a.chords().get(1);
        assertThat(a7.roman()).isEqualTo("V7/ii");
        assertThat(a7.function()).isEqualTo(ChordFunction.SECONDARY_DOMINANT);
        assertThat(scales(a7)).startsWith("Phrygian dominant", "Altered");
        assertThat(a7.scales().get(0).name()).isEqualTo("A Phrygian dominant");
        assertThat(a7.scales().get(0).notes()).extracting(Note::toString)
                .containsExactly("A", "Bb", "C#", "D", "E", "F", "G");
    }

    @Test
    void example3_minorIiVi_inAMinor() {
        ChartAnalysis a = analyzer.analyze(chart(A_MINOR, "Bm7b5 | E7alt | Am7"));

        assertThat(a.mainKey()).hasToString("A minor");
        assertThat(a.keyRegions().get(0).reason()).isEqualTo("ii-V-i in measures 1-3");

        ChordAnalysis bm7b5 = a.chords().get(0);
        assertThat(bm7b5.roman()).isEqualTo("iiø7");
        assertThat(scales(bm7b5)).containsExactly("Locrian #2", "Locrian");

        ChordAnalysis e7alt = a.chords().get(1);
        assertThat(e7alt.roman()).isEqualTo("V7alt");
        assertThat(e7alt.function()).isEqualTo(ChordFunction.DOMINANT);
        assertThat(scales(e7alt).get(0)).isEqualTo("Altered");
        assertThat(e7alt.scales().get(0).notes()).extracting(Note::toString)
                .containsExactly("E", "F", "G", "G#", "Bb", "C", "D");

        assertThat(a.chords().get(2).roman()).isEqualTo("i7");
        assertThat(a.chords().get(2).function()).isEqualTo(ChordFunction.TONIC);
    }

    @Test
    void example4_tritoneSubstitution() {
        ChartAnalysis a = analyzer.analyze(chart(C_MAJOR, "Dm7 | Db7 | Cmaj7"));

        ChordAnalysis db7 = a.chords().get(1);
        assertThat(db7.roman()).isEqualTo("bII7");
        assertThat(db7.function()).isEqualTo(ChordFunction.TRITONE_SUBSTITUTION);
        assertThat(scales(db7)).containsExactly("Lydian dominant");
        assertThat(db7.scales().get(0).notes()).extracting(Note::toString)
                .containsExactly("Db", "Eb", "F", "G", "Ab", "Bb", "Cb");
        assertThat(a.keyRegions()).hasSize(1);
    }

    @Test
    void example5_aModulatingTuneHasSeveralKeyRegions() {
        ChartAnalysis a = analyzer.analyze(chart(C_MAJOR,
                "Dm7 | G7 | Cmaj7 | Cmaj7 | Fm7 | Bb7 | Ebmaj7 | Ebmaj7"));

        assertThat(a.keyRegions()).extracting(KeyRegion::key).extracting(Key::toString)
                .containsExactly("C major", "Eb major");
        // chords are labelled against the key of their own region
        assertThat(a.chords().get(5).roman()).isEqualTo("V7");
        assertThat(a.chords().get(5).localKey()).hasToString("Eb major");
        assertThat(a.chords().get(4).roman()).isEqualTo("ii7");
        assertThat(a.chords().get(6).roman()).isEqualTo("Imaj7");
    }

    @Test
    void example5_aParsedFileEndToEnd() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/modulation.xml")) {
            ChartAnalysis a = analyzer.analyze(new MusicXmlParser().parse(in));
            assertThat(a.title()).isEqualTo("Modulating tune");
            assertThat(a.keyRegions()).hasSize(2);
            assertThat(a.chords()).hasSize(8);
            assertThat(a.chords()).allSatisfy(c -> assertThat(c.scales()).isNotEmpty());
        }
    }

    @Test
    void mainKeyIsTheKeyCoveringTheMostChords() {
        ChartAnalysis a = analyzer.analyze(chart(C_MAJOR,
                "Dm7 | G7 | Cmaj7 | Cmaj7 | Am7 | Dm7 | G7 | Cmaj7 | Fm7 | Bb7 | Ebmaj7 | Ebmaj7"));
        assertThat(a.mainKey()).hasToString("C major");
    }

    @Test
    void everyChordIsAccountedFor() {
        ChartAnalysis a = analyzer.analyze(chart(C_MAJOR, "Cmaj7 | A7 Dm7 | G7 | Cmaj7"));
        assertThat(a.chords()).extracting(c -> c.chord().symbol()).containsExactly("Cmaj7", "A7", "Dm7", "G7", "Cmaj7");
        assertThat(a.chords()).extracting(ChordAnalysis::measure).containsExactly(1, 2, 2, 3, 4);
        assertThat(a.chords()).extracting(ChordAnalysis::beat).containsExactly(1.0, 1.0, 3.0, 1.0, 1.0);
    }
}
