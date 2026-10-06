package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.PostgresIntegrationTest;
import dev.laveenh.jazzanalyzer.persistence.AnalysisEntity;
import dev.laveenh.jazzanalyzer.persistence.AnalysisRepository;
import dev.laveenh.jazzanalyzer.persistence.AnalysisStatus;
import dev.laveenh.jazzanalyzer.persistence.SongEntity;
import dev.laveenh.jazzanalyzer.persistence.SongRepository;
import dev.laveenh.jazzanalyzer.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisProcessorIntegrationTest extends PostgresIntegrationTest {

    @Autowired AnalysisProcessor processor;
    @Autowired SongRepository songs;
    @Autowired AnalysisRepository analyses;
    @Autowired FileStorage storage;

    @BeforeEach
    void clean() {
        clearDatabase();
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream in = AnalysisProcessorIntegrationTest.class.getResourceAsStream("/fixtures/" + name)) {
            return in.readAllBytes();
        }
    }

    private AnalysisEntity pendingAnalysisFor(String storageKey, String checksumChar) {
        SongEntity song = songs.save(new SongEntity("placeholder", "tune.xml", storageKey, checksumChar.repeat(64), Instant.now()));
        return analyses.save(AnalysisEntity.pending(song.getId(), Instant.now()));
    }

    private Long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test
    void aGoodFileEndsCompleteWithEverythingSaved() throws IOException {
        AnalysisEntity analysis = pendingAnalysisFor(storage.store(fixture("ii-v-i-c-major.xml")), "a");

        processor.process(analysis.getId());

        AnalysisEntity done = analyses.findById(analysis.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(AnalysisStatus.COMPLETE);
        assertThat(done.getDetectedKey()).isEqualTo("C major");
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(done.getErrorMessage()).isNull();
        assertThat(count("key_regions")).isEqualTo(1);
        assertThat(count("chords")).isEqualTo(3);
        assertThat(count("scale_suggestions")).isGreaterThanOrEqualTo(3);
        // the title now comes from the file instead of the file name
        assertThat(songs.findById(done.getSongId()).orElseThrow().getTitle()).isEqualTo("ii-V-I in C");
        assertThat(jdbc.queryForList("SELECT roman_numeral FROM chords ORDER BY position_index", String.class))
                .containsExactly("ii7", "V7", "Imaj7");
        assertThat(jdbc.queryForList("SELECT scale_name FROM scale_suggestions s JOIN chords c ON c.id = s.chord_id "
                + "WHERE c.symbol = 'Dm7'", String.class)).containsExactly("D Dorian");
    }

    @Test
    void aFileWithoutChordsFailsWithAnExplanation() throws IOException {
        AnalysisEntity analysis = pendingAnalysisFor(storage.store(fixture("no-harmony.xml")), "b");

        processor.process(analysis.getId());

        AnalysisEntity failed = analyses.findById(analysis.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getErrorMessage()).contains("no chord symbols");
        assertThat(failed.getCompletedAt()).isNotNull();
        assertThat(count("chords")).isZero();
    }

    @Test
    void aTimewiseScoreFailsWithAnExplanation() throws IOException {
        AnalysisEntity analysis = pendingAnalysisFor(storage.store(fixture("timewise.xml")), "c");
        processor.process(analysis.getId());
        assertThat(analyses.findById(analysis.getId()).orElseThrow().getErrorMessage()).contains("score-partwise");
    }

    @Test
    void aMissingStoredFileFailsWithoutLeakingInternals() {
        AnalysisEntity analysis = pendingAnalysisFor("does-not-exist.xml", "d");

        processor.process(analysis.getId());

        AnalysisEntity failed = analyses.findById(analysis.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(failed.getErrorMessage()).isEqualTo("The analysis failed because of an internal error.");
    }

    @Test
    void processingNeverThrowsEvenForAnUnknownAnalysis() {
        processor.process(UUID.randomUUID());   // must simply return
    }

    @Test
    void aFailureLeavesNoHalfSavedResults() throws IOException {
        AnalysisEntity analysis = pendingAnalysisFor(storage.store(fixture("no-harmony.xml")), "e");
        processor.process(analysis.getId());
        assertThat(count("key_regions")).isZero();
        assertThat(count("scale_suggestions")).isZero();
    }
}
