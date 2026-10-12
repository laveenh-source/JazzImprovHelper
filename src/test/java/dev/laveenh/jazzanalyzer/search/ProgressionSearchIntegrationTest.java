package dev.laveenh.jazzanalyzer.search;

import dev.laveenh.jazzanalyzer.PostgresIntegrationTest;
import dev.laveenh.jazzanalyzer.TestCharts;
import dev.laveenh.jazzanalyzer.analysis.ProgressionPattern;
import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.persistence.ProgressionMatchRow;
import dev.laveenh.jazzanalyzer.persistence.ProgressionSearchRepository;
import dev.laveenh.jazzanalyzer.persistence.StatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The window-function search and statistics SQL, against real PostgreSQL with hand-seeded chords. */
class ProgressionSearchIntegrationTest extends PostgresIntegrationTest {

    @Autowired ProgressionSearchRepository search;
    @Autowired StatsRepository stats;

    private int songCounter;

    @BeforeEach
    void clean() {
        clearDatabase();
        songCounter = 0;
    }

    /** Inserts a song with one analysis whose chords are the given measures ("Dm7 | G7 | Cmaj7"). */
    private UUID song(String title, String status, int createdMinutesAgo, String progression) {
        UUID songId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        String checksum = String.format("%064x", ++songCounter);
        jdbc.update("INSERT INTO songs (id, title, original_filename, storage_key, checksum_sha256, uploaded_at) "
                + "VALUES (?, ?, ?, ?, ?, now())", songId, title, title + ".xml", songId + ".xml", checksum);
        jdbc.update("INSERT INTO analyses (id, song_id, status, detected_key, created_at) VALUES (?, ?, ?, 'C major', ?)",
                analysisId, songId, status, OffsetDateTime.now().minusMinutes(createdMinutesAgo));

        int position = 0;
        String[] measures = progression.split("\\|");
        for (int m = 0; m < measures.length; m++) {
            for (String symbol : measures[m].trim().split("\\s+")) {
                Chord chord = TestCharts.chord(symbol);
                jdbc.update("INSERT INTO chords (analysis_id, position_index, measure, beat, symbol, root_pc, quality, "
                                + "roman_numeral, chord_function, function_reason, local_key) "
                                + "VALUES (?, ?, ?, 1, ?, ?, ?, ?, 'TONIC', 'test', 'C major')",
                        analysisId, position++, m + 1, symbol, chord.root().pitchClass(), chord.quality().name(),
                        "I" + chord.quality().suffix());
            }
        }
        return songId;
    }

    private UUID song(String title, String progression) {
        return song(title, "COMPLETE", 0, progression);
    }

    private ProgressionSearchRepository.Page find(ProgressionPattern pattern, Integer tonic) {
        return search.search(pattern, tonic, 50, 0);
    }

    @Test
    void findsAnIiViInTheRequestedKeyOnly() {
        song("In Bb", "Cm7 | F7 | Bbmaj7");
        song("In C", "Dm7 | G7 | Cmaj7");

        var bb = find(ProgressionPattern.II_V_I, 10);
        assertThat(bb.matches()).singleElement().satisfies(m -> {
            assertThat(m.title()).isEqualTo("In Bb");
            assertThat(m.tonicPitchClass()).isEqualTo(10);
            assertThat(m.startMeasure()).isEqualTo(1);
            assertThat(m.endMeasure()).isEqualTo(3);
            assertThat(m.chords()).isEqualTo("Cm7 F7 Bbmaj7");
        });
        assertThat(bb.total()).isEqualTo(1);

        assertThat(find(ProgressionPattern.II_V_I, 0).matches()).extracting(ProgressionMatchRow::title).containsExactly("In C");
        assertThat(find(ProgressionPattern.II_V_I, 7).matches()).isEmpty();
    }

    @Test
    void withoutAKeyItSearchesEveryKeyAndReportsWhichOne() {
        song("In Bb", "Cm7 | F7 | Bbmaj7");
        song("In C", "Dm7 | G7 | Cmaj7");
        song("Neither", "Cmaj7 | Fmaj7 | Gmaj7");

        var all = find(ProgressionPattern.II_V_I, null);
        assertThat(all.matches()).extracting(ProgressionMatchRow::title, ProgressionMatchRow::tonicPitchClass)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("In Bb", 10), org.assertj.core.groups.Tuple.tuple("In C", 0));
        assertThat(all.total()).isEqualTo(2);
    }

    @Test
    void aTuneCanContainSeveralOccurrencesInDifferentKeys() {
        song("Modulating", "Dm7 | G7 | Cmaj7 | Cmaj7 | Fm7 | Bb7 | Ebmaj7 | Ebmaj7");

        var all = find(ProgressionPattern.II_V_I, null);
        assertThat(all.matches()).extracting(ProgressionMatchRow::tonicPitchClass, ProgressionMatchRow::startMeasure)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(0, 1), org.assertj.core.groups.Tuple.tuple(3, 5));
    }

    @Test
    void repeatedChordsDoNotBreakAProgression() {
        song("Held chords", "Cm7 | Cm7 | F7 | F7 | Bbmaj7 | Bbmaj7");
        var bb = find(ProgressionPattern.II_V_I, 10);
        assertThat(bb.matches()).singleElement().satisfies(m -> {
            assertThat(m.startMeasure()).isEqualTo(1);
            assertThat(m.endMeasure()).isEqualTo(5);   // the Bbmaj7 that completes it
        });
    }

    @Test
    void chordsInTheSameMeasureCountToo() {
        song("Two per bar", "Cm7 F7 | Bbmaj7");
        assertThat(find(ProgressionPattern.II_V_I, 10).matches()).singleElement()
                .satisfies(m -> assertThat(m.startMeasure()).isEqualTo(1));
    }

    @Test
    void theQualitiesMustFit() {
        song("Minor resolution", "Cm7 | F7 | Bbm7");        // ii-V-i, not ii-V-I
        song("Dominant ii", "C7 | F7 | Bbmaj7");              // ii is not minor
        song("Major V", "Cm7 | F | Bbmaj7");                  // V is not a dominant 7
        assertThat(find(ProgressionPattern.II_V_I, 10).matches()).isEmpty();
        assertThat(find(ProgressionPattern.II_V_MINOR_I, 10).matches()).extracting(ProgressionMatchRow::title)
                .containsExactly("Minor resolution");
    }

    @Test
    void theRootIntervalsMustFit() {
        song("Wrong intervals", "Cm7 | G7 | Bbmaj7");        // V is on G, not F
        assertThat(find(ProgressionPattern.II_V_I, null).matches()).isEmpty();
    }

    @Test
    void findsAMinorIiViWithAHalfDiminishedTwo() {
        song("Minor", "Bm7b5 | E7 | Am7");
        song("Minor alt", "Bm7b5 | E7alt | Am7");
        var a = find(ProgressionPattern.II_V_MINOR_I, 9);
        assertThat(a.matches()).extracting(ProgressionMatchRow::title).containsExactly("Minor", "Minor alt");
        assertThat(find(ProgressionPattern.II_V_I, 9).matches()).isEmpty();
    }

    @Test
    void findsTheTritoneSubstitutionVersion() {
        song("Tritone", "Dm7 | Db7 | Cmaj7");
        assertThat(find(ProgressionPattern.II_TRITONE_I, 0).matches()).singleElement()
                .satisfies(m -> assertThat(m.chords()).isEqualTo("Dm7 Db7 Cmaj7"));
        assertThat(find(ProgressionPattern.II_V_I, 0).matches()).isEmpty();
    }

    @Test
    void findsFourChordTurnarounds() {
        song("Turnaround", "Cmaj7 | Am7 | Dm7 | G7");
        song("Rhythm changes", "Em7 | Am7 | Dm7 | G7");
        assertThat(find(ProgressionPattern.I_VI_II_V, 0).matches()).extracting(ProgressionMatchRow::title).containsExactly("Turnaround");
        assertThat(find(ProgressionPattern.III_VI_II_V, 0).matches()).extracting(ProgressionMatchRow::title).containsExactly("Rhythm changes");
    }

    @Test
    void findsTwoChordPatterns() {
        song("Cadence", "G7 | Cmaj7");
        assertThat(find(ProgressionPattern.V_I, 0).matches()).hasSize(1);
        song("Half cadence", "Dm7 | G7");
        assertThat(find(ProgressionPattern.II_V, 0).matches()).extracting(ProgressionMatchRow::title).contains("Half cadence");
    }

    @Test
    void aProgressionCannotSpanTwoDifferentSongs() {
        // song A ends with ii-V, song B starts with the I chord: neither contains a whole ii-V-I
        song("Ends with ii-V", "Cmaj7 | Dm7 | G7");
        song("Starts with I", "Cmaj7 | Am7");
        assertThat(find(ProgressionPattern.II_V_I, 0).matches()).isEmpty();
    }

    @Test
    void onlyTheNewestFinishedAnalysisOfASongIsSearched() {
        UUID songId = song("Retried", "COMPLETE", 60, "Dm7 | G7 | Cmaj7");           // old analysis had the progression
        // a newer analysis of the same song, without it
        UUID newer = UUID.randomUUID();
        jdbc.update("INSERT INTO analyses (id, song_id, status, detected_key, created_at) VALUES (?, ?, 'COMPLETE', 'F major', now())",
                newer, songId);
        jdbc.update("INSERT INTO chords (analysis_id, position_index, measure, beat, symbol, root_pc, quality, roman_numeral, "
                + "chord_function, function_reason, local_key) VALUES (?, 0, 1, 1, 'Fmaj7', 5, 'MAJOR7', 'Imaj7', 'TONIC', 't', 'F major')", newer);

        assertThat(find(ProgressionPattern.II_V_I, null).matches()).isEmpty();
    }

    @Test
    void unfinishedAndFailedAnalysesAreIgnored() {
        song("Failed", "FAILED", 0, "Dm7 | G7 | Cmaj7");
        song("Running", "RUNNING", 0, "Dm7 | G7 | Cmaj7");
        song("Pending", "PENDING", 0, "Dm7 | G7 | Cmaj7");
        assertThat(find(ProgressionPattern.II_V_I, null).matches()).isEmpty();
    }

    @Test
    void pagesAndReportsTheTotal() {
        for (int i = 1; i <= 5; i++) {
            song("Tune " + i, "Dm7 | G7 | Cmaj7");
        }
        var first = search.search(ProgressionPattern.II_V_I, 0, 2, 0);
        var last = search.search(ProgressionPattern.II_V_I, 0, 2, 4);
        assertThat(first.matches()).extracting(ProgressionMatchRow::title).containsExactly("Tune 1", "Tune 2");
        assertThat(first.total()).isEqualTo(5);
        assertThat(last.matches()).extracting(ProgressionMatchRow::title).containsExactly("Tune 5");
        assertThat(search.search(ProgressionPattern.II_V_I, 0, 2, 10).matches()).isEmpty();
        assertThat(search.search(ProgressionPattern.II_V_I, 0, 2, 10).total()).isZero();
    }

    @Test
    void generatedSqlUsesWindowFunctionsAndCtes() {
        String sql = ProgressionSearchRepository.buildSql(ProgressionPattern.II_V_I);
        assertThat(sql).contains("WITH latest AS", "LAG(", "LEAD(", "OVER w", "PARTITION BY");
        assertThat(sql).contains("IN ('MINOR', 'MINOR7')", "(w.r0 - 2 + 12) % 12 AS tonic",
                "(w.r1 - w.r0 + 12) % 12 = 5", "(w.r2 - w.r0 + 12) % 12 = 10");
        assertThat(sql).doesNotContain("%s", "%d");
    }

    // ------------------------------------------------------------ statistics

    @Test
    void countsTheMostCommonChordQualities() {
        song("A", "Dm7 | G7 | Cmaj7");
        song("B", "Dm7 | G7 | Cmaj7 | Am7");

        var qualities = stats.topChordQualities(10);
        assertThat(qualities).extracting(StatsRepository.QualityCount::quality, StatsRepository.QualityCount::chords)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("MINOR7", 3L),
                        org.assertj.core.groups.Tuple.tuple("DOMINANT7", 2L),
                        org.assertj.core.groups.Tuple.tuple("MAJOR7", 2L));
        assertThat(qualities.get(0).percent()).isEqualTo(42.9);   // 3 of 7 chords
        assertThat(stats.topChordQualities(1)).hasSize(1);
    }

    @Test
    void countsTheMostCommonMovesBetweenChords() {
        UUID a = song("A", "Dm7 | G7 | Cmaj7");
        // give the chords real Roman numerals so the transitions are meaningful
        jdbc.update("UPDATE chords SET roman_numeral = CASE symbol WHEN 'Dm7' THEN 'ii7' WHEN 'G7' THEN 'V7' ELSE 'Imaj7' END");
        UUID b = song("B", "Dm7 | G7 | Cmaj7");
        jdbc.update("UPDATE chords SET roman_numeral = CASE symbol WHEN 'Dm7' THEN 'ii7' WHEN 'G7' THEN 'V7' ELSE 'Imaj7' END");

        var moves = stats.topTransitions(10);
        assertThat(moves).extracting(StatsRepository.TransitionCount::from, StatsRepository.TransitionCount::to,
                        StatsRepository.TransitionCount::occurrences)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("V7", "Imaj7", 2L),
                        org.assertj.core.groups.Tuple.tuple("ii7", "V7", 2L));
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void statisticsOfAnEmptyLibraryAreEmpty() {
        assertThat(stats.topChordQualities(10)).isEmpty();
        assertThat(stats.topTransitions(10)).isEmpty();
    }
}
