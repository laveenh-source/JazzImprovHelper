package com.jazzanalyzer.parser;

import com.jazzanalyzer.domain.Chart;
import com.jazzanalyzer.domain.ChordQuality;
import com.jazzanalyzer.domain.Key;
import com.jazzanalyzer.domain.Mode;
import com.jazzanalyzer.domain.Note;
import com.jazzanalyzer.domain.PlacedChord;
import com.jazzanalyzer.domain.TimeSignature;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class MusicXmlParserTest {

    private final MusicXmlParser parser = new MusicXmlParser();

    private Chart parseFixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/" + name)) {
            assertThat(in).as("fixture " + name).isNotNull();
            return parser.parse(in);
        }
    }

    private static List<String> symbols(Chart chart) {
        return chart.chords().stream().map(c -> c.chord().symbol()).toList();
    }

    @Test
    void readsMetadataAndChordsOfAnIiVI() throws IOException {
        Chart chart = parseFixture("ii-v-i-c-major.xml");

        assertThat(chart.title()).isEqualTo("ii-V-I in C");
        assertThat(chart.keySignature()).isEqualTo(new Key(Note.parse("C"), Mode.MAJOR));
        assertThat(chart.timeSignature()).isEqualTo(new TimeSignature(4, 4));
        assertThat(chart.measureCount()).isEqualTo(3);
        assertThat(symbols(chart)).containsExactly("Dm7", "G7", "Cmaj7");
        assertThat(chart.chords()).extracting(PlacedChord::measure).containsExactly(1, 2, 3);
        assertThat(chart.chords()).extracting(PlacedChord::beat).containsOnly(1.0);
    }

    @Test
    void readsMinorKeySignature() throws IOException {
        Chart chart = parseFixture("minor-ii-v-i.xml");
        assertThat(chart.keySignature()).isEqualTo(new Key(Note.parse("A"), Mode.MINOR));
        assertThat(symbols(chart)).containsExactly("Bm7b5", "E7alt", "Am7");
        assertThat(chart.chords().get(0).chord().quality()).isEqualTo(ChordQuality.HALF_DIMINISHED7);
        assertThat(chart.chords().get(1).chord().alt()).isTrue();
    }

    @Test
    void readsAlterationsSlashChordsAndAccidentals() throws IOException {
        Chart chart = parseFixture("alterations-and-slash.xml");

        assertThat(chart.keySignature()).isEqualTo(new Key(Note.parse("Bb"), Mode.MAJOR));
        assertThat(symbols(chart)).containsExactly(
                "G7b9#11", "C/E", "Bb7alt", "Cmaj7#5", "F#9/A#", "Db7sus4");
        assertThat(chart.chords().get(1).chord().bass()).isEqualTo(Note.parse("E"));
        assertThat(chart.chords().get(3).chord().quality()).isEqualTo(ChordQuality.MAJOR7_SHARP5);
        assertThat(chart.chords().get(5).chord().quality()).isEqualTo(ChordQuality.DOMINANT7_SUS4);
    }

    @Test
    void computesBeatsFromNotesAndOffsets() throws IOException {
        Chart chart = parseFixture("two-chords-per-measure.xml");

        assertThat(symbols(chart)).containsExactly("Dm7", "G7", "Cmaj7", "Am7", "F", "G7");
        assertThat(chart.chords()).extracting(PlacedChord::measure).containsExactly(1, 1, 2, 2, 3, 3);
        // measure 1: two half notes -> beats 1 and 3
        assertThat(chart.chords().get(0).beat()).isCloseTo(1.0, within(1e-9));
        assertThat(chart.chords().get(1).beat()).isCloseTo(3.0, within(1e-9));
        // measure 2: second chord uses <offset> of 3 quarter notes -> beat 4
        assertThat(chart.chords().get(2).beat()).isCloseTo(1.0, within(1e-9));
        assertThat(chart.chords().get(3).beat()).isCloseTo(4.0, within(1e-9));
        // measure 3 (3/4): chord after two quarter notes -> beat 3
        assertThat(chart.chords().get(4).beat()).isCloseTo(1.0, within(1e-9));
        assertThat(chart.chords().get(5).beat()).isCloseTo(3.0, within(1e-9));
        assertThat(chart.timeSignature()).isEqualTo(new TimeSignature(4, 4)); // first one wins
    }

    @Test
    void readsAModulatingTune() throws IOException {
        Chart chart = parseFixture("modulation.xml");
        assertThat(symbols(chart)).containsExactly(
                "Dm7", "G7", "Cmaj7", "Cmaj7", "Fm7", "Bb7", "Ebmaj7", "Ebmaj7");
        assertThat(chart.measureCount()).isEqualTo(8);
    }

    @Test
    void acceptsTheHarmlessDoctypeThatExportersWrite() throws IOException {
        Chart chart = parseFixture("with-doctype.xml");
        assertThat(symbols(chart)).containsExactly("Cmaj7");
    }

    @Test
    void rejectsFilesWithoutChordSymbols() {
        assertThatThrownBy(() -> parseFixture("no-harmony.xml"))
                .isInstanceOf(NoChordSymbolsException.class)
                .hasMessageContaining("no chord symbols");
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> parseFixture("malformed.xml"))
                .isInstanceOf(ChartParseException.class)
                .hasMessageContaining("not well-formed");
    }

    @Test
    void rejectsTimewiseScores() {
        assertThatThrownBy(() -> parseFixture("timewise.xml"))
                .isInstanceOf(ChartParseException.class)
                .hasMessageContaining("score-partwise");
    }

    @Test
    void rejectsEmptyInput() {
        assertThatThrownBy(() -> parse(""))
                .isInstanceOf(ChartParseException.class);
    }

    // ---- small inline documents for edge cases that don't deserve a fixture file

    private Chart parse(String xml) {
        return parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static String partwise(String measures) {
        return "<score-partwise><part-list/><part id=\"P1\">" + measures + "</part></score-partwise>";
    }

    @Test
    void titleFallsBackToMovementTitleThenUntitled() {
        String measure = "<measure number=\"1\"><harmony><root><root-step>C</root-step></root><kind>major</kind></harmony></measure>";
        assertThat(parse("<score-partwise><movement-title>Mvt</movement-title><part id=\"P1\">" + measure + "</part></score-partwise>").title())
                .isEqualTo("Mvt");
        assertThat(parse(partwise(measure)).title()).isEqualTo("Untitled");
        assertThat(parse(partwise(measure)).keySignature()).isNull();
        assertThat(parse(partwise(measure)).timeSignature()).isNull();
    }

    @Test
    void noChordMarkersAreSkippedButOtherChordsKept() {
        Chart chart = parse(partwise(
                "<measure number=\"1\"><harmony><root><root-step>C</root-step></root><kind>none</kind></harmony>"
                        + "<harmony><root><root-step>G</root-step></root><kind>dominant</kind></harmony></measure>"));
        assertThat(symbols(chart)).containsExactly("G7");
    }

    @Test
    void onlyNoChordMarkersCountsAsNoChords() {
        assertThatThrownBy(() -> parse(partwise(
                "<measure number=\"1\"><harmony><root><root-step>C</root-step></root><kind>none</kind></harmony></measure>")))
                .isInstanceOf(NoChordSymbolsException.class);
    }

    @Test
    void nonNumericMeasureNumbersFallBackToPosition() {
        Chart chart = parse(partwise(
                "<measure number=\"X1\"><harmony><root><root-step>C</root-step></root><kind>major</kind></harmony></measure>"
                        + "<measure number=\"X2\"><harmony><root><root-step>F</root-step></root><kind>major</kind></harmony></measure>"));
        assertThat(chart.chords()).extracting(PlacedChord::measure).containsExactly(1, 2);
    }

    @Test
    void usesTheFirstPartThatCarriesChords() {
        String xml = "<score-partwise><part id=\"P1\"><measure number=\"1\"/></part>"
                + "<part id=\"P2\"><measure number=\"1\"><harmony><root><root-step>E</root-step><root-alter>-1</root-alter></root>"
                + "<kind>major-seventh</kind></harmony></measure></part></score-partwise>";
        assertThat(symbols(parse(xml))).containsExactly("Ebmaj7");
    }

    @Test
    void gracenotesAndChordNotesTakeNoTime() {
        String measure = "<measure number=\"1\"><attributes><divisions>2</divisions></attributes>"
                + "<note><pitch/><duration>2</duration></note>"            // quarter: advances 2
                + "<note><grace/><pitch/></note>"                         // grace: no time
                + "<note><chord/><pitch/><duration>2</duration></note>"   // stacked note: no time
                + "<forward><duration>2</duration></forward>"             // skip another quarter
                + "<harmony><root><root-step>G</root-step></root><kind>dominant</kind></harmony></measure>";
        assertThat(parse(partwise(measure)).chords().get(0).beat()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void backupMovesTimeBackwards() {
        String measure = "<measure number=\"1\"><attributes><divisions>1</divisions></attributes>"
                + "<note><pitch/><duration>4</duration></note><backup><duration>2</duration></backup>"
                + "<harmony><root><root-step>G</root-step></root><kind>dominant</kind></harmony></measure>";
        assertThat(parse(partwise(measure)).chords().get(0).beat()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void beatsAreCountedInTheTimeSignatureUnit() {
        // 6/8 with divisions=2 (per quarter): a dotted quarter = 3 divisions = beat 2 (eighth-note beats)
        String measure = "<measure number=\"1\"><attributes><divisions>2</divisions>"
                + "<time><beats>6</beats><beat-type>8</beat-type></time></attributes>"
                + "<note><pitch/><duration>3</duration></note>"
                + "<harmony><root><root-step>G</root-step></root><kind>dominant</kind></harmony></measure>";
        Chart chart = parse(partwise(measure));
        assertThat(chart.timeSignature()).isEqualTo(new TimeSignature(6, 8));
        assertThat(chart.chords().get(0).beat()).isCloseTo(4.0, within(1e-9));
    }

    @Test
    void reportsHarmonyWithoutRoot() {
        assertThatThrownBy(() -> parse(partwise(
                "<measure number=\"3\"><harmony><kind>major</kind></harmony></measure>")))
                .isInstanceOf(ChartParseException.class)
                .hasMessageContaining("measure 3");
    }

    @Test
    void reportsBadRootStepAndMissingRootStep() {
        assertThatThrownBy(() -> parse(partwise(
                "<measure number=\"1\"><harmony><root><root-step>H</root-step></root><kind>major</kind></harmony></measure>")))
                .isInstanceOf(ChartParseException.class).hasMessageContaining("Invalid note name");
        assertThatThrownBy(() -> parse(partwise(
                "<measure number=\"1\"><harmony><root/><kind>major</kind></harmony></measure>")))
                .isInstanceOf(ChartParseException.class).hasMessageContaining("root-step");
    }

    @Test
    void reportsDegreeWithoutValue() {
        assertThatThrownBy(() -> parse(partwise(
                "<measure number=\"1\"><harmony><root><root-step>C</root-step></root><kind>dominant</kind>"
                        + "<degree><degree-type>add</degree-type></degree></harmony></measure>")))
                .isInstanceOf(ChartParseException.class).hasMessageContaining("degree-value");
    }

    @Test
    void parsesFromAPath(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws IOException {
        java.nio.file.Path file = dir.resolve("tune.xml");
        try (InputStream in = getClass().getResourceAsStream("/fixtures/ii-v-i-c-major.xml")) {
            java.nio.file.Files.copy(in, file);
        }
        assertThat(parser.parse(file).title()).isEqualTo("ii-V-I in C");
    }
}
