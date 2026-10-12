WITH latest AS (          -- the newest finished analysis of each song
    SELECT DISTINCT ON (song_id) id, song_id
    FROM analyses
    WHERE status = 'COMPLETE'
    ORDER BY song_id, created_at DESC
),
changes AS (              -- LAG: what was the previous chord?
    SELECT c.analysis_id, c.position_index, c.measure, c.symbol, c.root_pc, c.quality,
           LAG(c.root_pc)  OVER w AS prev_root,
           LAG(c.quality)  OVER w AS prev_quality
    FROM chords c
    JOIN latest l ON l.id = c.analysis_id
    WINDOW w AS (PARTITION BY c.analysis_id ORDER BY c.position_index)
),
distinct_chords AS (      -- a repeated chord is not a new step of the progression
    SELECT analysis_id, position_index, measure, symbol, root_pc, quality
    FROM changes
    WHERE prev_root IS DISTINCT FROM root_pc OR prev_quality IS DISTINCT FROM quality
),
windowed AS (             -- LEAD: put the next chords on the same row
    SELECT analysis_id, measure AS start_measure,
           symbol AS s0, root_pc AS r0, quality AS q0,
        LEAD(symbol, 1)   OVER w AS s1,
        LEAD(root_pc, 1)  OVER w AS r1,
        LEAD(quality, 1)  OVER w AS q1,
        LEAD(symbol, 2)   OVER w AS s2,
        LEAD(root_pc, 2)  OVER w AS r2,
        LEAD(quality, 2)  OVER w AS q2,
       LEAD(measure, 2) OVER w AS end_measure
    FROM distinct_chords
    WINDOW w AS (PARTITION BY analysis_id ORDER BY position_index)
),
matches AS (              -- keep rows whose chords have the pattern's root intervals and qualities
    SELECT w.analysis_id,
           (w.r0 - 2 + 12) % 12 AS tonic,
           w.start_measure, w.end_measure,
           concat_ws(' ', w.s0, w.s1, w.s2) AS chords
    FROM windowed w
    WHERE w.end_measure IS NOT NULL
      AND w.q0 IN ('MINOR', 'MINOR7')
                      AND (w.r1 - w.r0 + 12) % 12 = 5 AND w.q1 IN ('AUGMENTED7', 'DOMINANT7', 'DOMINANT7_SUS4')
                      AND (w.r2 - w.r0 + 12) % 12 = 10 AND w.q2 IN ('MAJOR', 'MAJOR6', 'MAJOR7', 'MAJOR7_SHARP5')
)
SELECT s.id AS song_id, s.title, m.analysis_id, m.tonic, m.start_measure, m.end_measure, m.chords,
       count(*) OVER () AS total
FROM matches m
JOIN latest l ON l.id = m.analysis_id
JOIN songs s ON s.id = l.song_id
WHERE CAST(:tonic AS INTEGER) IS NULL OR m.tonic = CAST(:tonic AS INTEGER)
ORDER BY s.title, m.start_measure, m.tonic
LIMIT :limit OFFSET :offset
