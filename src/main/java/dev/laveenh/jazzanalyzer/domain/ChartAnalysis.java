package dev.laveenh.jazzanalyzer.domain;

import java.util.List;

/**
 * The full result of analysing a chart.
 *
 * @param mainKey the key of the region that covers the most chords
 */
public record ChartAnalysis(String title, Key mainKey, List<KeyRegion> keyRegions, List<ChordAnalysis> chords) {
}
