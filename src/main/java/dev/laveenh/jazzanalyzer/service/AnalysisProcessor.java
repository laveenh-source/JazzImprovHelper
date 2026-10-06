package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.analysis.ChartAnalyzer;
import dev.laveenh.jazzanalyzer.domain.Chart;
import dev.laveenh.jazzanalyzer.domain.ChartAnalysis;
import dev.laveenh.jazzanalyzer.domain.ChordAnalysis;
import dev.laveenh.jazzanalyzer.domain.KeyRegion;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.domain.ScaleSuggestion;
import dev.laveenh.jazzanalyzer.parser.ChartParseException;
import dev.laveenh.jazzanalyzer.parser.MusicXmlParser;
import dev.laveenh.jazzanalyzer.persistence.AnalysisEntity;
import dev.laveenh.jazzanalyzer.persistence.AnalysisRepository;
import dev.laveenh.jazzanalyzer.persistence.ChordEntity;
import dev.laveenh.jazzanalyzer.persistence.ChordRepository;
import dev.laveenh.jazzanalyzer.persistence.KeyRegionEntity;
import dev.laveenh.jazzanalyzer.persistence.KeyRegionRepository;
import dev.laveenh.jazzanalyzer.persistence.ScaleSuggestionEntity;
import dev.laveenh.jazzanalyzer.persistence.ScaleSuggestionRepository;
import dev.laveenh.jazzanalyzer.persistence.SongEntity;
import dev.laveenh.jazzanalyzer.persistence.SongRepository;
import dev.laveenh.jazzanalyzer.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Runs one analysis: PENDING, then RUNNING, then COMPLETE or FAILED. It never throws, so whoever
 * calls it (a request thread now, a background worker in Phase 4) cannot be crashed by a bad file.
 *
 * <p>The parsing and analysing happen <em>outside</em> any database transaction. Each status change is
 * its own short transaction, so a slow analysis never holds a connection open and a failure still
 * leaves a visible FAILED status.
 */
@Component
public class AnalysisProcessor {

    private static final Logger log = LoggerFactory.getLogger(AnalysisProcessor.class);

    private final AnalysisRepository analyses;
    private final SongRepository songs;
    private final KeyRegionRepository keyRegions;
    private final ChordRepository chords;
    private final ScaleSuggestionRepository scaleSuggestions;
    private final FileStorage storage;
    private final MusicXmlParser parser;
    private final ChartAnalyzer analyzer;
    private final TransactionTemplate tx;
    private final Clock clock;

    public AnalysisProcessor(AnalysisRepository analyses, SongRepository songs, KeyRegionRepository keyRegions,
                             ChordRepository chords, ScaleSuggestionRepository scaleSuggestions,
                             FileStorage storage, MusicXmlParser parser, ChartAnalyzer analyzer,
                             TransactionTemplate tx, Clock clock) {
        this.analyses = analyses;
        this.songs = songs;
        this.keyRegions = keyRegions;
        this.chords = chords;
        this.scaleSuggestions = scaleSuggestions;
        this.storage = storage;
        this.parser = parser;
        this.analyzer = analyzer;
        this.tx = tx;
        this.clock = clock;
    }

    public void process(UUID analysisId) {
        try {
            UUID songId = tx.execute(status -> start(analysisId));
            SongEntity song = songs.findById(songId).orElseThrow();

            byte[] content = storage.read(song.getStorageKey());
            Chart chart = parser.parse(new ByteArrayInputStream(content));
            ChartAnalysis result = analyzer.analyze(chart);

            tx.executeWithoutResult(status -> saveResult(analysisId, song.getId(), result));
        } catch (ChartParseException e) {
            // The file is well-formed XML but not a usable lead sheet: tell the user why.
            fail(analysisId, e.getMessage());
        } catch (Exception e) {
            log.error("Analysis {} failed unexpectedly", analysisId, e);
            fail(analysisId, "The analysis failed because of an internal error.");
        }
    }

    private UUID start(UUID analysisId) {
        AnalysisEntity analysis = analyses.findById(analysisId).orElseThrow();
        analysis.markRunning();
        analyses.save(analysis);
        return analysis.getSongId();
    }

    private void saveResult(UUID analysisId, UUID songId, ChartAnalysis result) {
        List<KeyRegionEntity> regionRows = new ArrayList<>();
        int regionIndex = 0;
        for (KeyRegion r : result.keyRegions()) {
            regionRows.add(new KeyRegionEntity(analysisId, regionIndex++, r.key().toString(),
                    r.startMeasure(), r.endMeasure(), r.confidence(), r.reason()));
        }
        keyRegions.saveAll(regionRows);

        int position = 0;
        for (ChordAnalysis c : result.chords()) {
            ChordEntity chord = chords.save(new ChordEntity(analysisId, position++, c.measure(), c.beat(),
                    c.chord().symbol(), c.chord().root().pitchClass(), c.chord().quality().name(), c.roman(),
                    c.function().name(), c.functionReason(), c.localKey().toString()));
            List<ScaleSuggestionEntity> scales = new ArrayList<>();
            for (ScaleSuggestion s : c.scales()) {
                scales.add(new ScaleSuggestionEntity(chord.getId(), s.rank(), s.name(), notes(s), s.reason()));
            }
            scaleSuggestions.saveAll(scales);
        }

        SongEntity song = songs.findById(songId).orElseThrow();
        song.setTitle(truncate(result.title(), 255));
        songs.save(song);

        AnalysisEntity analysis = analyses.findById(analysisId).orElseThrow();
        analysis.markComplete(result.mainKey().toString(), clock.instant());
        analyses.save(analysis);
    }

    private void fail(UUID analysisId, String message) {
        try {
            tx.executeWithoutResult(status -> analyses.findById(analysisId).ifPresent(a -> {
                a.markFailed(message, clock.instant());
                analyses.save(a);
            }));
        } catch (Exception e) {
            log.error("Could not record failure of analysis {}", analysisId, e);
        }
    }

    private static String notes(ScaleSuggestion s) {
        return s.notes().stream().map(Note::toString).collect(Collectors.joining(" "));
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max);
    }
}
