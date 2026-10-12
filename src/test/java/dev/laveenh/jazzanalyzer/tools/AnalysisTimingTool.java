package dev.laveenh.jazzanalyzer.tools;

import dev.laveenh.jazzanalyzer.TestCharts;
import dev.laveenh.jazzanalyzer.analysis.ChartAnalyzer;
import dev.laveenh.jazzanalyzer.domain.Chart;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.parser.MusicXmlParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Developer tool: how long do parsing and analysing a chart take, once the JVM has warmed up?
 *
 * <pre>java -cp target/classes:target/test-classes dev.laveenh.jazzanalyzer.tools.AnalysisTimingTool</pre>
 *
 * Prints the median, 95th percentile and mean over many runs, per chart size. This measures the engine only:
 * no database, no HTTP. The charts are made-up progressions (a 32-bar minor-ii-V tune, repeated for the longer one).
 */
public final class AnalysisTimingTool {

    private static final int WARM_UP = 3_000;
    private static final int RUNS = 5_000;

    private static final String TUNE_32_BARS = String.join(" | ",
            "Cm7 | F7 | Bbmaj7 | Ebmaj7 | Am7b5 | D7 | Gm7 | Gm7",
            "Cm7 | F7 | Bbmaj7 | Ebmaj7 | Am7b5 | D7 | Gm7 | Gm7",
            "Am7b5 | D7 | Gm7 | Gm7 | Cm7 | F7 | Bbmaj7 | Ebmaj7",
            "Cm7 | F7 | Bbmaj7 | Ebmaj7 | Am7b5 | D7 | Gm7 | Gm7");

    private AnalysisTimingTool() {
    }

    public static void main(String[] args) {
        MusicXmlParser parser = new MusicXmlParser();
        ChartAnalyzer analyzer = new ChartAnalyzer();
        Key signature = Key.fromSignature(-2, "minor");

        String ii = "Dm7 | G7 | Cmaj7";
        String big = String.join(" | ", java.util.Collections.nCopies(8, TUNE_32_BARS));

        System.out.printf("%-28s %-9s %10s %10s %10s%n", "chart", "step", "median", "p95", "mean");
        for (var sample : new String[][]{{"3 bars (ii-V-I)", ii}, {"32 bars", TUNE_32_BARS}, {"256 bars", big}}) {
            Chart chart = TestCharts.chart(signature, sample[1]);
            byte[] xml = toMusicXml(sample[1]).getBytes(StandardCharsets.UTF_8);

            report(sample[0], "parse", measure(() -> parser.parse(new ByteArrayInputStream(xml))));
            report(sample[0], "analyse", measure(() -> analyzer.analyze(chart)));
            report(sample[0], "parse+analyse", measure(() -> analyzer.analyze(parser.parse(new ByteArrayInputStream(xml)))));
        }
        System.out.printf("%nwarm-up runs: %d, measured runs: %d, JVM: %s%n", WARM_UP, RUNS, System.getProperty("java.version"));
    }

    private static long[] measure(Runnable work) {
        for (int i = 0; i < WARM_UP; i++) {
            work.run();
        }
        long[] nanos = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            work.run();
            nanos[i] = System.nanoTime() - start;
        }
        Arrays.sort(nanos);
        return nanos;
    }

    private static void report(String chart, String step, long[] sorted) {
        double mean = Arrays.stream(sorted).average().orElse(0);
        System.out.printf("%-28s %-9s %9.3f ms %8.3f ms %8.3f ms%n", chart, step,
                sorted[sorted.length / 2] / 1e6, sorted[(int) (sorted.length * 0.95)] / 1e6, mean / 1e6);
    }

    /** A bare-bones partwise MusicXML document: one whole note and the chord symbols of each bar. */
    private static String toMusicXml(String progression) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?><score-partwise><work><work-title>Timing</work-title></work>"
                + "<part-list><score-part id=\"P1\"/></part-list><part id=\"P1\">");
        String[] bars = progression.split("\\|");
        for (int bar = 0; bar < bars.length; bar++) {
            xml.append("<measure number=\"").append(bar + 1).append("\">");
            if (bar == 0) {
                xml.append("<attributes><divisions>1</divisions><key><fifths>-2</fifths><mode>minor</mode></key>"
                        + "<time><beats>4</beats><beat-type>4</beat-type></time></attributes>");
            }
            xml.append(harmony(bars[bar].trim()));
            xml.append("<note><pitch><step>C</step><octave>5</octave></pitch><duration>4</duration></note></measure>");
        }
        return xml.append("</part></score-partwise>").toString();
    }

    private static String harmony(String symbol) {
        var chord = TestCharts.chord(symbol);
        String kind = switch (chord.quality()) {
            case MINOR7 -> "minor-seventh";
            case DOMINANT7 -> "dominant";
            case MAJOR7 -> "major-seventh";
            case HALF_DIMINISHED7 -> "half-diminished";
            default -> "major";
        };
        String alter = chord.root().alter() == 0 ? "" : "<root-alter>" + chord.root().alter() + "</root-alter>";
        return "<harmony><root><root-step>" + chord.root().step() + "</root-step>" + alter + "</root><kind>" + kind + "</kind></harmony>";
    }
}
