-- Indexes chosen from measurements, not guesses. Method, data and the full before/after tables are in
-- docs/PERFORMANCE.md (5,000 synthetic songs, 328,650 chords, PostgreSQL 16, median of 5 EXPLAIN ANALYZE runs).
-- Only indexes that made a measurable difference are here.

-- Song list ("each song with its newest analysis") and the progression search's "newest finished analysis
-- of each song" both look analyses up by song and sort by date.
--   song list, newest 20:   917 ms -> 11 ms
CREATE INDEX idx_analyses_song_created ON analyses (song_id, created_at DESC);

-- PostgreSQL does not index foreign keys by itself. Without these, deleting a song had to scan the whole
-- child table once per parent row to apply ON DELETE CASCADE.
--   delete one song (cascade):   2,454 ms -> 2.2 ms
CREATE INDEX idx_key_regions_analysis ON key_regions (analysis_id);
CREATE INDEX idx_scale_suggestions_chord ON scale_suggestions (chord_id);

-- Reading an analysis' chords in order, and the window functions (PARTITION BY analysis_id ORDER BY
-- position_index) in the progression search and statistics: the index delivers rows already sorted, so the
-- 16 MB sort that spilled to disk (Sort Method: external merge) disappears.
--   progression search:   about 1,100 ms -> about 940 ms (modest: most of the time is the window functions themselves)
--   stats: transitions:   538 ms -> 426 ms
CREATE INDEX idx_chords_analysis_position ON chords (analysis_id, position_index);

-- Deliberately NOT created: chords (quality, roman_numeral). It was the obvious candidate for the statistics
-- and search queries, and it was measured: no query got faster, because they all need every chord row anyway.
