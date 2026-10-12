package dev.laveenh.jazzanalyzer.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Summary queries over the whole library (only the newest finished analysis of each song counts). */
@Repository
public class StatsRepository {

    public record QualityCount(String quality, long chords, double percent) {
    }

    public record TransitionCount(String from, String to, long occurrences) {
    }

    private static final String LATEST = """
            latest AS (
                SELECT DISTINCT ON (song_id) id
                FROM analyses
                WHERE status = 'COMPLETE'
                ORDER BY song_id, created_at DESC
            )""";

    /** Share of each chord quality; SUM(...) OVER () is the grand total each count is divided by. */
    public static final String QUALITIES_SQL = "WITH " + LATEST + """

            SELECT c.quality,
                   count(*) AS chords,
                   round(100.0 * count(*) / sum(count(*)) OVER (), 1) AS percent
            FROM chords c
            JOIN latest l ON l.id = c.analysis_id
            GROUP BY c.quality
            ORDER BY chords DESC, c.quality
            LIMIT :limit
            """;

    /** LEAD pairs each chord with the next one inside the same analysis. */
    public static final String TRANSITIONS_SQL = "WITH " + LATEST + """
            ,
            pairs AS (
                SELECT c.roman_numeral AS from_roman,
                       LEAD(c.roman_numeral) OVER (PARTITION BY c.analysis_id ORDER BY c.position_index) AS to_roman
                FROM chords c
                JOIN latest l ON l.id = c.analysis_id
            )
            SELECT from_roman, to_roman, count(*) AS occurrences
            FROM pairs
            WHERE to_roman IS NOT NULL AND to_roman <> from_roman
            GROUP BY from_roman, to_roman
            ORDER BY occurrences DESC, from_roman, to_roman
            LIMIT :limit
            """;

    private final JdbcClient jdbc;

    public StatsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Most common chord qualities. {@code SUM(...) OVER ()} gives the grand total to compute each share. */
    public List<QualityCount> topChordQualities(int limit) {
        return jdbc.sql(QUALITIES_SQL)
                .param("limit", limit)
                .query((rs, n) -> new QualityCount(rs.getString("quality"), rs.getLong("chords"), rs.getDouble("percent")))
                .list();
    }

    /**
     * Most common "chord A then chord B" moves, by Roman numeral (ii7 to V7, V7 to Imaj7...).
     * {@code LEAD} pairs each chord with the next one inside the same analysis.
     */
    public List<TransitionCount> topTransitions(int limit) {
        return jdbc.sql(TRANSITIONS_SQL)
                .param("limit", limit)
                .query((rs, n) -> new TransitionCount(rs.getString("from_roman"), rs.getString("to_roman"),
                        rs.getLong("occurrences")))
                .list();
    }
}
