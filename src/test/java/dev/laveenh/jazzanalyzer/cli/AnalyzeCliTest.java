package dev.laveenh.jazzanalyzer.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyzeCliTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();

    private int run(String... args) {
        return AnalyzeCli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    private static Path fixture(String name) {
        try {
            return Path.of(AnalyzeCliTest.class.getResource("/fixtures/" + name).toURI());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void printsKeyRegionsChordsAndScales() {
        int code = run(fixture("ii-v-i-c-major.xml").toString());

        assertThat(code).isZero();
        String text = out.toString(StandardCharsets.UTF_8);
        assertThat(text).contains("ii-V-I in C", "Main key: C major", "Key regions",
                "ii-V-I in measures 1-3", "Dm7", "ii7", "subdominant",
                "1. D Dorian  (D E F G A B C)", "G Mixolydian", "2. G Altered", "C Ionian");
    }

    @Test
    void showsFractionalBeats() {
        run(fixture("two-chords-per-measure.xml").toString());
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("beat 3 ", "beat 4 ");
    }

    @Test
    void sayswhenAChordHasNoScaleSuggestion(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sus2.xml");
        Files.writeString(file, "<score-partwise><part id=\"P1\"><measure number=\"1\"><harmony><root><root-step>C</root-step></root>"
                + "<kind>suspended-second</kind></harmony></measure></part></score-partwise>");
        assertThat(run(file.toString())).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).contains("no scale suggestion");
    }

    @Test
    void reportsAFileWithoutChords() {
        assertThat(run(fixture("no-harmony.xml").toString())).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("no chord symbols");
    }

    @Test
    void reportsAMissingFile() {
        assertThat(run("/no/such/file.xml")).isEqualTo(1);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Cannot read");
    }

    @Test
    void explainsUsageWithoutArguments() {
        assertThat(run()).isEqualTo(2);
        assertThat(err.toString(StandardCharsets.UTF_8)).contains("Usage");
    }
}
