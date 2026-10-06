package dev.laveenh.jazzanalyzer.persistence;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hand-written SQL for the song list. JPA is great for simple CRUD, but "each song with the state of its
 * latest analysis, filtered, sorted and paged" reads better as SQL, so it is written as SQL.
 */
@Repository
public class SongQueryRepository {

    /** The only sortable fields, mapped to SQL columns. User input never reaches the ORDER BY clause. */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "title", "s.title",
            "uploadedAt", "s.uploaded_at",
            "detectedKey", "a.detected_key");

    private final JdbcClient jdbc;

    public SongQueryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public static boolean isSortable(String field) {
        return SORT_COLUMNS.containsKey(field);
    }

    public static java.util.Set<String> sortableFields() {
        return SORT_COLUMNS.keySet();
    }

    /**
     * @param key optional: "Bb major" matches exactly, "Bb" matches the key in either mode; case-insensitive
     */
    public List<SongListRow> findPage(String key, String sortField, boolean ascending, int limit, long offset) {
        String column = SORT_COLUMNS.get(sortField);
        if (column == null) {
            throw new IllegalArgumentException("Cannot sort by " + sortField);
        }
        // LATERAL lets the subquery see the current song, so each song brings only its newest analysis.
        String sql = """
                SELECT s.id, s.title, s.original_filename, s.uploaded_at,
                       a.id AS analysis_id, a.status, a.detected_key
                FROM songs s
                LEFT JOIN LATERAL (
                    SELECT id, status, detected_key
                    FROM analyses
                    WHERE song_id = s.id
                    ORDER BY created_at DESC
                    LIMIT 1
                ) a ON TRUE
                WHERE (CAST(:key AS TEXT) IS NULL OR lower(a.detected_key) = lower(:key)
                                    OR lower(a.detected_key) LIKE lower(:key) || ' %')
                ORDER BY ${order} NULLS LAST, s.id
                LIMIT :limit OFFSET :offset
                """.replace("${order}", column + (ascending ? " ASC" : " DESC"));
        return jdbc.sql(sql)
                .param("key", key)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, row) -> new SongListRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("title"),
                        rs.getString("original_filename"),
                        rs.getObject("uploaded_at", java.time.OffsetDateTime.class).toInstant(),
                        rs.getObject("analysis_id", UUID.class),
                        rs.getString("status") == null ? null : AnalysisStatus.valueOf(rs.getString("status")),
                        rs.getString("detected_key")))
                .list();
    }

    public long count(String key) {
        String sql = """
                SELECT count(*)
                FROM songs s
                LEFT JOIN LATERAL (
                    SELECT detected_key FROM analyses WHERE song_id = s.id ORDER BY created_at DESC LIMIT 1
                ) a ON TRUE
                WHERE (CAST(:key AS TEXT) IS NULL OR lower(a.detected_key) = lower(:key)
                                    OR lower(a.detected_key) LIKE lower(:key) || ' %')
                """;
        return jdbc.sql(sql).param("key", key).query(Long.class).single();
    }
}
