package dev.laveenh.jazzanalyzer.cli;

import dev.laveenh.jazzanalyzer.analysis.ChartAnalyzer;
import dev.laveenh.jazzanalyzer.domain.Chart;
import dev.laveenh.jazzanalyzer.domain.ChartAnalysis;
import dev.laveenh.jazzanalyzer.domain.ChordAnalysis;
import dev.laveenh.jazzanalyzer.domain.KeyRegion;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.domain.ScaleSuggestion;
import dev.laveenh.jazzanalyzer.parser.ChartParseException;
import dev.laveenh.jazzanalyzer.parser.MusicXmlParser;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** Command-line front end: {@code AnalyzeCli chart.xml} prints the analysis of a MusicXML lead sheet. */
public final class AnalyzeCli {

    private AnalyzeCli() {
    }

    public static void main(String[] args) {
        // Roman numerals use ø and °, so write UTF-8 regardless of the platform's default encoding.
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
        System.exit(run(args, out, err));
    }

    /** Returns the process exit code: 0 on success, 1 for a bad file, 2 for bad usage. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length != 1) {
            err.println("Usage: AnalyzeCli <chart.xml>");
            return 2;
        }
        try {
            Chart chart = new MusicXmlParser().parse(Path.of(args[0]));
            print(new ChartAnalyzer().analyze(chart), out);
            return 0;
        } catch (ChartParseException e) {
            err.println("Cannot analyse " + args[0] + ": " + e.getMessage());
            return 1;
        } catch (IOException e) {
            err.println("Cannot read " + args[0] + ": " + e.getMessage());
            return 1;
        }
    }

    static void print(ChartAnalysis analysis, PrintStream out) {
        out.println(analysis.title());
        out.println("Main key: " + analysis.mainKey());
        out.println();
        out.println("Key regions");
        for (KeyRegion r : analysis.keyRegions()) {
            out.printf("  %-10s measures %d-%d  confidence %.2f  %s%n",
                    r.key(), r.startMeasure(), r.endMeasure(), r.confidence(), r.reason());
        }
        out.println();
        out.println("Chords");
        for (ChordAnalysis c : analysis.chords()) {
            out.printf("  m%-3d beat %-4s %-9s %-10s %-22s key %s%n", c.measure(), beat(c.beat()),
                    c.chord().symbol(), c.roman(), c.function().label(), c.localKey());
            out.println("      " + c.functionReason());
            if (c.scales().isEmpty()) {
                out.println("      (no scale suggestion for this chord)");
            }
            for (ScaleSuggestion s : c.scales()) {
                out.printf("      %d. %s  (%s)  %s%n", s.rank(), s.name(), notes(s.notes()), s.reason());
            }
        }
    }

    private static String beat(double beat) {
        return beat == Math.rint(beat) ? String.valueOf((int) beat) : String.format("%.2f", beat);
    }

    private static String notes(List<Note> notes) {
        return notes.stream().map(Note::toString).collect(Collectors.joining(" "));
    }
}
