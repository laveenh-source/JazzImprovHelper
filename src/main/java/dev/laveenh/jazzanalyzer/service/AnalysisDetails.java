package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.persistence.AnalysisEntity;
import dev.laveenh.jazzanalyzer.persistence.ChordEntity;
import dev.laveenh.jazzanalyzer.persistence.KeyRegionEntity;
import dev.laveenh.jazzanalyzer.persistence.ScaleSuggestionEntity;
import dev.laveenh.jazzanalyzer.persistence.SongEntity;

import java.util.List;
import java.util.Map;

/**
 * Everything stored about one analysis, loaded with a fixed number of queries.
 * The result lists are empty unless the analysis is COMPLETE.
 */
public record AnalysisDetails(SongEntity song, AnalysisEntity analysis, List<KeyRegionEntity> keyRegions,
                              List<ChordEntity> chords, Map<Long, List<ScaleSuggestionEntity>> scalesByChordId) {
}
