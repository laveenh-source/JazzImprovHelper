package dev.laveenh.jazzanalyzer.persistence;

import dev.laveenh.jazzanalyzer.analysis.ProgressionPattern;
import dev.laveenh.jazzanalyzer.domain.ChordQuality;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Finds progressions such as ii-V-I across every analysed song with SQL window functions.
 *
 * <p>How the query works, step by step (see {@link #buildSql}):
 * <ol>
 *   <li>{@code latest}: the newest COMPLETE analysis of each song, joined to its song once, here, so the title
 *       travels with every row. Nothing is joined <em>after</em> the window functions: PostgreSQL cannot estimate
 *       how many rows survive a filter on window output, and an earlier version that joined {@code songs} at the
 *       end got a nested-loop plan that rescanned the table once per match (see docs/PERFORMANCE.md).</li>
 *   <li>{@code changes}: {@code LAG} looks at the previous chord, so that {@code distinct_chords} can drop
 *       chords that merely repeat it (Dm7 | Dm7 | G7 should still count as ii-V).</li>
 *   <li>{@code windowed}: {@code LEAD} pulls the next chords of the progression onto each row, so one row
 *       holds a chord and the next N-1 chords as columns.</li>
 *   <li>{@code matches}: keep rows whose chords have the pattern's root intervals and qualities, e.g. for
 *       ii-V-I a minor chord, then a dominant 5 semitones above it, then a major chord 10 above it. The tonic
 *       is worked out from the first chord, and the optional key filter is applied last.</li>
 * </ol>
 */
@Repository
public class ProgressionSearchRepository {

    private final JdbcClient jdbc;

    public ProgressionSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** One page of matches plus the total number of matches. {@code tonic} (0-11) may be null for "any key". */
    public Page search(ProgressionPattern pattern, Integer tonic, int limit, long offset) {
        List<Row> rows = jdbc.sql(buildSql(pattern))
                .param("tonic", tonic, java.sql.Types.INTEGER)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, n) -> new Row(new ProgressionMatchRow(
                        rs.getObject("song_id", UUID.class),
                        rs.getString("title"),
                        rs.getObject("analysis_id", UUID.class),
                        rs.getInt("tonic"),
                        rs.getInt("start_measure"),
                        rs.getInt("end_measure"),
                        rs.getString("chords")), rs.getLong("total")))
                .list();
        long total = rows.isEmpty() ? 0 : rows.get(0).total();
        return new Page(rows.stream().map(Row::match).toList(), total);
    }

    public record Page(List<ProgressionMatchRow> matches, long total) {
    }

    private record Row(ProgressionMatchRow match, long total) {
    }

    /**
     * The SQL for a pattern. Everything spliced into the text (column numbers, semitone offsets, quality
     * names) comes from the {@link ProgressionPattern} enum, never from user input; the tonic, limit and
     * offset go in as bind parameters.
     */
    public static String buildSql(ProgressionPattern pattern) {
        int n = pattern.steps().size();

        String leadColumns = IntStream.range(1, n)
                .mapToObj(i -> """
                               LEAD(symbol, %1$d)   OVER w AS s%1$d,
                               LEAD(root_pc, %1$d)  OVER w AS r%1$d,
                               LEAD(quality, %1$d)  OVER w AS q%1$d,
                       """.formatted(i))
                .collect(Collectors.joining());

        // the first chord fixes the tonic: tonic = root of chord 0 minus its offset above the tonic
        int firstOffset = pattern.steps().get(0).semitonesAboveTonic();

        // one condition per chord; chords after the first are checked by their distance from the first chord
        String stepTests = IntStream.range(0, n)
                .mapToObj(i -> {
                    ProgressionPattern.Step step = pattern.steps().get(i);
                    String qualities = step.qualities().stream().map(ChordQuality::name)
                            .sorted().map(q -> "'" + q + "'").collect(Collectors.joining(", "));
                    if (i == 0) {
                        return "w.q0 IN (%s)".formatted(qualities);
                    }
                    int distance = Math.floorMod(step.semitonesAboveTonic() - firstOffset, 12);
                    return "(w.r%d - w.r0 + 12) %% 12 = %d AND w.q%d IN (%s)".formatted(i, distance, i, qualities);
                })
                .collect(Collectors.joining("\n                      AND "));

        String chordList = IntStream.range(0, n).mapToObj(i -> "w.s" + i).collect(Collectors.joining(", "));

        return """
                WITH latest AS (          -- the newest finished analysis of each song, with its song's title
                    SELECT DISTINCT ON (a.song_id) a.id, a.song_id, s.title
                    FROM analyses a
                    JOIN songs s ON s.id = a.song_id
                    WHERE a.status = 'COMPLETE'
                    ORDER BY a.song_id, a.created_at DESC
                ),
                changes AS (              -- LAG: what was the previous chord?
                    SELECT l.song_id, l.title, c.analysis_id, c.position_index, c.measure, c.symbol, c.root_pc, c.quality,
                           LAG(c.root_pc)  OVER w AS prev_root,
                           LAG(c.quality)  OVER w AS prev_quality
                    FROM chords c
                    JOIN latest l ON l.id = c.analysis_id
                    WINDOW w AS (PARTITION BY c.analysis_id ORDER BY c.position_index)
                ),
                distinct_chords AS (      -- a repeated chord is not a new step of the progression
                    SELECT song_id, title, analysis_id, position_index, measure, symbol, root_pc, quality
                    FROM changes
                    WHERE prev_root IS DISTINCT FROM root_pc OR prev_quality IS DISTINCT FROM quality
                ),
                windowed AS (             -- LEAD: put the next chords on the same row
                    SELECT song_id, title, analysis_id, measure AS start_measure,
                           symbol AS s0, root_pc AS r0, quality AS q0,
                %s       LEAD(measure, %d) OVER w AS end_measure
                    FROM distinct_chords
                    WINDOW w AS (PARTITION BY analysis_id ORDER BY position_index)
                ),
                matches AS (              -- keep rows whose chords have the pattern's root intervals and qualities
                    SELECT w.song_id, w.title, w.analysis_id,
                           (w.r0 - %d + 12) %% 12 AS tonic,
                           w.start_measure, w.end_measure,
                           concat_ws(' ', %s) AS chords
                    FROM windowed w
                    WHERE w.end_measure IS NOT NULL
                      AND %s
                )
                SELECT m.song_id, m.title, m.analysis_id, m.tonic, m.start_measure, m.end_measure, m.chords,
                       count(*) OVER () AS total
                FROM matches m
                WHERE CAST(:tonic AS INTEGER) IS NULL OR m.tonic = CAST(:tonic AS INTEGER)
                ORDER BY m.title, m.start_measure, m.tonic
                LIMIT :limit OFFSET :offset
                """.formatted(leadColumns, n - 1, firstOffset, chordList, stepTests);
    }
}
