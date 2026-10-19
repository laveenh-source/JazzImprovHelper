package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Chart;
import dev.laveenh.jazzanalyzer.domain.ChartAnalysis;
import dev.laveenh.jazzanalyzer.domain.ChordAnalysis;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.KeyRegion;
import dev.laveenh.jazzanalyzer.domain.PlacedChord;

import java.util.ArrayList;
import java.util.List;

/** Runs the whole pipeline: key detection, then function analysis, then scale suggestions. */
public final class ChartAnalyzer {

    private final KeyDetector keyDetector;
    private final FunctionAnalyzer functionAnalyzer;
    private final ScaleSuggester scaleSuggester;

    public ChartAnalyzer() {
        this(new KeyDetector(), new FunctionAnalyzer(), new ScaleSuggester());
    }

    public ChartAnalyzer(KeyDetector keyDetector, FunctionAnalyzer functionAnalyzer, ScaleSuggester scaleSuggester) {
        this.keyDetector = keyDetector;
        this.functionAnalyzer = functionAnalyzer;
        this.scaleSuggester = scaleSuggester;
    }

    public ChartAnalysis analyze(Chart chart) {
        KeyDetection keys = keyDetector.detect(chart);
        List<PlacedChord> placed = chart.chords();
        List<FunctionResult> functions = functionAnalyzer.analyze(placed, keys.chordKeys());

        List<ChordAnalysis> chords = new ArrayList<>(placed.size());
        for (int i = 0; i < placed.size(); i++) {
            PlacedChord p = placed.get(i);
            Key key = keys.chordKeys().get(i);
            FunctionResult f = functions.get(i);
            chords.add(new ChordAnalysis(p.measure(), p.beat(), p.chord(), key, f.roman(), f.function(),
                    f.reason(), scaleSuggester.suggest(p.chord(), key, f)));
        }
        return new ChartAnalysis(chart.title(), mainKey(keys, chords.size()), keys.regions(), chords);
    }

    /** The key of the region covering the most chords (the earliest one wins a tie). */
    private static Key mainKey(KeyDetection keys, int chordCount) {
        Key best = null;
        int bestCount = -1;
        int index = 0;
        for (KeyRegion region : keys.regions()) {
            int count = 0;
            while (index < chordCount && keys.chordKeys().get(index) == region.key()) {
                count++;
                index++;
            }
            if (count > bestCount) {
                bestCount = count;
                best = region.key();
            }
        }
        return best;
    }
}
