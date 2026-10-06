package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.persistence.AnalysisEntity;
import dev.laveenh.jazzanalyzer.persistence.AnalysisRepository;
import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;
import dev.laveenh.jazzanalyzer.persistence.ChordEntity;
import dev.laveenh.jazzanalyzer.persistence.ChordRepository;
import dev.laveenh.jazzanalyzer.persistence.KeyRegionEntity;
import dev.laveenh.jazzanalyzer.persistence.KeyRegionRepository;
import dev.laveenh.jazzanalyzer.persistence.ScaleSuggestionEntity;
import dev.laveenh.jazzanalyzer.persistence.ScaleSuggestionRepository;
import dev.laveenh.jazzanalyzer.persistence.SongEntity;
import dev.laveenh.jazzanalyzer.persistence.SongListRow;
import dev.laveenh.jazzanalyzer.persistence.SongQueryRepository;
import dev.laveenh.jazzanalyzer.persistence.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read side: loads stored analyses and song lists. */
@Service
@Transactional(readOnly = true)
public class AnalysisQueryService {

    /** One page of songs plus the total, for pagination. */
    public record SongPage(List<SongListRow> rows, long total) {
    }

    private final SongRepository songs;
    private final AnalysisRepository analyses;
    private final KeyRegionRepository keyRegions;
    private final ChordRepository chords;
    private final ScaleSuggestionRepository scaleSuggestions;
    private final SongQueryRepository songQueries;

    public AnalysisQueryService(SongRepository songs, AnalysisRepository analyses, KeyRegionRepository keyRegions,
                                ChordRepository chords, ScaleSuggestionRepository scaleSuggestions,
                                SongQueryRepository songQueries) {
        this.songs = songs;
        this.analyses = analyses;
        this.keyRegions = keyRegions;
        this.chords = chords;
        this.scaleSuggestions = scaleSuggestions;
        this.songQueries = songQueries;
    }

    public AnalysisDetails getAnalysis(UUID analysisId) {
        AnalysisEntity analysis = analyses.findById(analysisId)
                .orElseThrow(() -> new NotFoundException("Analysis", analysisId));
        return load(analysis);
    }

    /** The newest analysis of a song, whatever its status. */
    public AnalysisDetails getLatestForSong(UUID songId) {
        songs.findById(songId).orElseThrow(() -> new NotFoundException("Song", songId));
        AnalysisEntity latest = analyses.findFirstBySongIdOrderByCreatedAtDesc(songId)
                .orElseThrow(() -> new AnalysisNotReadyException("Song " + songId + " has no analysis."));
        return load(latest);
    }

    /** The newest analysis of a song, which must be COMPLETE. */
    public AnalysisDetails getCompleteForSong(UUID songId) {
        AnalysisDetails details = getLatestForSong(songId);
        if (details.analysis().getStatus() != AnalysisStatus.COMPLETE) {
            throw new AnalysisNotReadyException("The latest analysis of song " + songId + " is "
                    + details.analysis().getStatus() + ", so there are no chords to show.");
        }
        return details;
    }

    public SongPage listSongs(String key, String sortField, boolean ascending, int page, int size) {
        String normalizedKey = key == null || key.isBlank() ? null : key.trim();
        List<SongListRow> rows = songQueries.findPage(normalizedKey, sortField, ascending, size, (long) page * size);
        return new SongPage(rows, songQueries.count(normalizedKey));
    }

    private AnalysisDetails load(AnalysisEntity analysis) {
        SongEntity song = songs.findById(analysis.getSongId()).orElseThrow();
        if (analysis.getStatus() != AnalysisStatus.COMPLETE) {
            return new AnalysisDetails(song, analysis, List.of(), List.of(), Map.of());
        }
        List<KeyRegionEntity> regions = keyRegions.findByAnalysisIdOrderByPositionIndex(analysis.getId());
        List<ChordEntity> chordRows = chords.findByAnalysisIdOrderByPositionIndex(analysis.getId());
        List<Long> chordIds = chordRows.stream().map(ChordEntity::getId).toList();
        Map<Long, List<ScaleSuggestionEntity>> scales = chordIds.isEmpty() ? Map.of()
                : scaleSuggestions.findByChordIdInOrderByChordIdAscRankAsc(chordIds).stream()
                        .collect(Collectors.groupingBy(ScaleSuggestionEntity::getChordId));
        return new AnalysisDetails(song, analysis, regions, chordRows, scales);
    }
}
