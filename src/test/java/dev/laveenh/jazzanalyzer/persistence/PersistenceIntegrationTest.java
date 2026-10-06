package dev.laveenh.jazzanalyzer.persistence;

import dev.laveenh.jazzanalyzer.PostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PersistenceIntegrationTest extends PostgresIntegrationTest {

    @Autowired SongRepository songs;
    @Autowired AnalysisRepository analyses;
    @Autowired KeyRegionRepository keyRegions;
    @Autowired ChordRepository chords;
    @Autowired ScaleSuggestionRepository scaleSuggestions;
    @Autowired SongQueryRepository songQueries;

    @BeforeEach
    void clean() {
        clearDatabase();
    }

    private static String checksum(char c) {
        return String.valueOf(c).repeat(64);
    }

    private SongEntity song(String title, char checksumChar, Instant uploadedAt) {
        return songs.save(new SongEntity(title, title + ".xml", UUID.randomUUID() + ".xml", checksum(checksumChar), uploadedAt));
    }

    private AnalysisEntity completeAnalysis(SongEntity song, String key, Instant created) {
        AnalysisEntity a = AnalysisEntity.pending(song.getId(), created);
        a.markComplete(key, created.plusSeconds(1));
        return analyses.save(a);
    }

    @Test
    void flywayCreatedTheSchemaAndHibernateAcceptsIt() {
        // the application context only starts if Flyway ran and ddl-auto=validate agrees with the tables
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("songs", "analyses", "key_regions", "chords", "scale_suggestions",
                "flyway_schema_history");
    }

    @Test
    void storesAndReadsBackAWholeAnalysis() {
        SongEntity song = song("Tune", 'a', Instant.now().truncatedTo(ChronoUnit.MICROS));
        AnalysisEntity analysis = completeAnalysis(song, "C major", Instant.now());

        keyRegions.save(new KeyRegionEntity(analysis.getId(), 0, "C major", 1, 3, 0.95, "ii-V-I in measures 1-3"));
        ChordEntity chord = chords.save(new ChordEntity(analysis.getId(), 0, 1, 1.0, "Dm7", 2, "MINOR7", "ii7",
                "SUBDOMINANT", "diatonic", "C major"));
        scaleSuggestions.save(new ScaleSuggestionEntity(chord.getId(), 1, "D Dorian", "D E F G A B C", "ii chord"));

        assertThat(songs.findByChecksumSha256(checksum('a'))).get().extracting(SongEntity::getTitle).isEqualTo("Tune");
        assertThat(analyses.findById(analysis.getId())).get().satisfies(a -> {
            assertThat(a.getStatus()).isEqualTo(AnalysisStatus.COMPLETE);
            assertThat(a.getDetectedKey()).isEqualTo("C major");
        });
        assertThat(keyRegions.findByAnalysisIdOrderByPositionIndex(analysis.getId()))
                .singleElement().satisfies(r -> assertThat(r.getConfidence()).isEqualTo(0.95));
        assertThat(chords.findByAnalysisIdOrderByPositionIndex(analysis.getId()))
                .singleElement().satisfies(c -> assertThat(c.getBeat()).isEqualTo(1.0));
        assertThat(scaleSuggestions.findByChordIdInOrderByChordIdAscRankAsc(List.of(chord.getId())))
                .singleElement().satisfies(s -> assertThat(s.getScaleNotes()).isEqualTo("D E F G A B C"));
    }

    @Test
    void chordsComeBackInScoreOrderAndScalesInRankOrder() {
        SongEntity song = song("Order", 'b', Instant.now());
        AnalysisEntity analysis = completeAnalysis(song, "C major", Instant.now());
        // saved out of order on purpose
        ChordEntity second = chords.save(new ChordEntity(analysis.getId(), 1, 2, 1.0, "G7", 7, "DOMINANT7", "V7", "DOMINANT", "r", "C major"));
        ChordEntity first = chords.save(new ChordEntity(analysis.getId(), 0, 1, 1.0, "Dm7", 2, "MINOR7", "ii7", "SUBDOMINANT", "r", "C major"));
        scaleSuggestions.save(new ScaleSuggestionEntity(second.getId(), 2, "G Altered", "x", "r"));
        scaleSuggestions.save(new ScaleSuggestionEntity(second.getId(), 1, "G Mixolydian", "x", "r"));

        assertThat(chords.findByAnalysisIdOrderByPositionIndex(analysis.getId()))
                .extracting(ChordEntity::getSymbol).containsExactly("Dm7", "G7");
        assertThat(scaleSuggestions.findByChordIdInOrderByChordIdAscRankAsc(List.of(first.getId(), second.getId())))
                .extracting(ScaleSuggestionEntity::getScaleName).containsExactly("G Mixolydian", "G Altered");
    }

    @Test
    void theChecksumIsUnique() {
        song("One", 'c', Instant.now());
        assertThatThrownBy(() -> song("Two", 'c', Instant.now())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anAnalysisNeedsAnExistingSongAndAValidStatus() {
        assertThatThrownBy(() -> analyses.save(AnalysisEntity.pending(UUID.randomUUID(), Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        SongEntity song = song("S", 'd', Instant.now());
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO analyses (id, song_id, status, created_at) VALUES (?, ?, 'BOGUS', now())",
                UUID.randomUUID(), song.getId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test
    void deletingASongCascadesToEverythingBelowIt() {
        SongEntity song = song("Doomed", 'e', Instant.now());
        AnalysisEntity analysis = completeAnalysis(song, "C major", Instant.now());
        keyRegions.save(new KeyRegionEntity(analysis.getId(), 0, "C major", 1, 3, 0.9, "r"));
        ChordEntity chord = chords.save(new ChordEntity(analysis.getId(), 0, 1, 1.0, "C", 0, "MAJOR", "I", "TONIC", "r", "C major"));
        scaleSuggestions.save(new ScaleSuggestionEntity(chord.getId(), 1, "C Ionian", "x", "r"));

        songs.deleteById(song.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM analyses", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM key_regions", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chords", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM scale_suggestions", Long.class)).isZero();
    }

    @Test
    void theLatestAnalysisOfASongIsTheNewestOne() {
        SongEntity song = song("Retried", 'f', Instant.now());
        Instant now = Instant.now();
        AnalysisEntity failed = AnalysisEntity.pending(song.getId(), now.minusSeconds(60));
        failed.markFailed("boom", now.minusSeconds(59));
        analyses.save(failed);
        AnalysisEntity retry = analyses.save(AnalysisEntity.pending(song.getId(), now));

        assertThat(analyses.findFirstBySongIdOrderByCreatedAtDesc(song.getId())).get()
                .extracting(AnalysisEntity::getId).isEqualTo(retry.getId());
    }

    // ------------------------------------------------------------ the hand-written song list SQL

    private void threeSongs() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        SongEntity a = song("Alpha", '1', t);
        SongEntity b = song("Bravo", '2', t.plusSeconds(10));
        SongEntity c = song("Charlie", '3', t.plusSeconds(20));
        completeAnalysis(a, "C major", t);
        completeAnalysis(b, "Bb major", t);
        completeAnalysis(c, "Bb minor", t);
    }

    @Test
    void listsSongsWithTheirLatestAnalysis() {
        threeSongs();
        List<SongListRow> rows = songQueries.findPage(null, "title", true, 10, 0);

        assertThat(rows).extracting(SongListRow::title).containsExactly("Alpha", "Bravo", "Charlie");
        assertThat(rows).extracting(SongListRow::detectedKey).containsExactly("C major", "Bb major", "Bb minor");
        assertThat(rows).extracting(SongListRow::latestStatus).containsOnly(AnalysisStatus.COMPLETE);
    }

    @Test
    void onlyTheNewestAnalysisOfEachSongIsListed() {
        SongEntity song = song("Twice", '7', Instant.now());
        Instant now = Instant.now();
        AnalysisEntity old = AnalysisEntity.pending(song.getId(), now.minusSeconds(100));
        old.markComplete("D major", now.minusSeconds(99));
        analyses.save(old);
        completeAnalysis(song, "G major", now);

        List<SongListRow> rows = songQueries.findPage(null, "title", true, 10, 0);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).detectedKey()).isEqualTo("G major");
    }

    @Test
    void aSongWithoutAnyAnalysisStillAppears() {
        song("Lonely", '8', Instant.now());
        assertThat(songQueries.findPage(null, "title", true, 10, 0))
                .singleElement().satisfies(r -> {
                    assertThat(r.latestStatus()).isNull();
                    assertThat(r.detectedKey()).isNull();
                });
    }

    @Test
    void sortsAndPages() {
        threeSongs();
        assertThat(songQueries.findPage(null, "title", false, 10, 0))
                .extracting(SongListRow::title).containsExactly("Charlie", "Bravo", "Alpha");
        assertThat(songQueries.findPage(null, "uploadedAt", false, 1, 0))
                .extracting(SongListRow::title).containsExactly("Charlie");
        assertThat(songQueries.findPage(null, "uploadedAt", false, 1, 1))
                .extracting(SongListRow::title).containsExactly("Bravo");
        assertThat(songQueries.findPage(null, "uploadedAt", false, 1, 3)).isEmpty();
        assertThat(songQueries.findPage(null, "detectedKey", true, 10, 0))
                .extracting(SongListRow::detectedKey).containsExactly("Bb major", "Bb minor", "C major");
    }

    @Test
    void filtersByKeyExactlyOrByTonic() {
        threeSongs();
        assertThat(songQueries.findPage("Bb major", "title", true, 10, 0)).extracting(SongListRow::title).containsExactly("Bravo");
        assertThat(songQueries.findPage("bb MAJOR", "title", true, 10, 0)).extracting(SongListRow::title).containsExactly("Bravo");
        assertThat(songQueries.findPage("Bb", "title", true, 10, 0)).extracting(SongListRow::title).containsExactly("Bravo", "Charlie");
        assertThat(songQueries.findPage("B", "title", true, 10, 0)).isEmpty();   // 'B' is not a prefix match of 'Bb'
        assertThat(songQueries.count("Bb")).isEqualTo(2);
        assertThat(songQueries.count(null)).isEqualTo(3);
        assertThat(songQueries.count("F# minor")).isZero();
    }

    @Test
    void sortFieldsAreWhitelistedSoUserInputNeverReachesTheSql() {
        assertThat(SongQueryRepository.isSortable("title")).isTrue();
        assertThat(SongQueryRepository.isSortable("title; DROP TABLE songs")).isFalse();
        // Spring's @Repository proxy wraps the IllegalArgumentException; the point is that nothing ran
        assertThatThrownBy(() -> songQueries.findPage(null, "1; DROP TABLE songs", true, 10, 0))
                .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                .hasMessageContaining("Cannot sort by");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM songs", Long.class)).isNotNull();
    }
}
