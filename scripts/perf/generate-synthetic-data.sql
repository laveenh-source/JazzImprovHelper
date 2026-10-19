-- =====================================================================================
-- SYNTHETIC TEST DATA. NOT REAL MUSIC.
--
-- Generates fake songs (title 'SYNTHETIC #00001', file 'synthetic-00001.xml') made of randomly
-- chosen, randomly transposed standard progressions, so the search and statistics queries can be
-- timed on a realistically sized library. Nothing here comes from a real chart.
--
--   psql -v songs=5000 -f scripts/perf/generate-synthetic-data.sql <database>
--   psql -f scripts/perf/delete-synthetic-data.sql <database>        -- to remove it again
--
-- Seeded, so the same command always produces the same data.
-- =====================================================================================
\if :{?songs}
\else
\set songs 5000
\endif

SELECT setseed(0.42);

-- the building blocks: (template, step, semitones above the tonic, quality, Roman numeral)
CREATE TEMP TABLE synth_template (template INT, step INT, semitones INT, quality TEXT, roman TEXT) ;
INSERT INTO synth_template VALUES
    (1, 1, 2, 'MINOR7', 'ii7'),            (1, 2, 7, 'DOMINANT7', 'V7'),         (1, 3, 0, 'MAJOR7', 'Imaj7'),
    (2, 1, 2, 'HALF_DIMINISHED7', 'iiø7'), (2, 2, 7, 'DOMINANT7', 'V7'),         (2, 3, 0, 'MINOR7', 'i7'),
    (3, 1, 0, 'MAJOR7', 'Imaj7'),          (3, 2, 9, 'MINOR7', 'vi7'),           (3, 3, 2, 'MINOR7', 'ii7'),   (3, 4, 7, 'DOMINANT7', 'V7'),
    (4, 1, 4, 'MINOR7', 'iii7'),           (4, 2, 9, 'MINOR7', 'vi7'),           (4, 3, 2, 'MINOR7', 'ii7'),   (4, 4, 7, 'DOMINANT7', 'V7'),
    (5, 1, 0, 'DOMINANT7', 'I7'),          (5, 2, 5, 'DOMINANT7', 'IV7'),        (5, 3, 0, 'DOMINANT7', 'I7'), (5, 4, 7, 'DOMINANT7', 'V7'),
    (6, 1, 0, 'MAJOR7', 'Imaj7'),          (6, 2, 5, 'MAJOR7', 'IVmaj7'),
    (7, 1, 2, 'MINOR7', 'ii7'),            (7, 2, 1, 'DOMINANT7', 'bII7'),       (7, 3, 0, 'MAJOR7', 'Imaj7'),
    (8, 1, 7, 'DOMINANT7', 'V7'),          (8, 2, 0, 'MAJOR6', 'I6');

-- songs: each gets a few key centres, each key centre a few progressions
CREATE TEMP TABLE synth_song AS
SELECT n AS song_no, gen_random_uuid() AS song_id, gen_random_uuid() AS analysis_id
FROM generate_series(1, :songs) n;

INSERT INTO songs (id, title, original_filename, storage_key, checksum_sha256, uploaded_at)
SELECT song_id,
       'SYNTHETIC #' || lpad(song_no::text, 5, '0'),
       'synthetic-' || lpad(song_no::text, 5, '0') || '.xml',
       'synthetic/' || lpad(song_no::text, 5, '0'),
       encode(sha256(convert_to('synthetic-' || song_no, 'UTF8')), 'hex'),
       now() - make_interval(secs => song_no)
FROM synth_song;

INSERT INTO analyses (id, song_id, status, detected_key, created_at, completed_at)
SELECT analysis_id, song_id, 'COMPLETE', 'C major', now() - make_interval(secs => song_no), now() - make_interval(secs => song_no)
FROM synth_song;

-- 12 to 24 segments per song, each a random template in a random key
CREATE TEMP TABLE synth_segment AS
SELECT s.song_no, s.analysis_id, g AS seg_no,
       1 + floor(random() * 8)::int AS template,
       floor(random() * 12)::int AS tonic
FROM synth_song s
CROSS JOIN LATERAL generate_series(1, 12 + floor(random() * 13)::int) g;

INSERT INTO chords (analysis_id, position_index, measure, beat, symbol, root_pc, quality, roman_numeral,
                    chord_function, function_reason, local_key)
SELECT analysis_id,
       position - 1,
       position,
       1,
       (ARRAY['C','Db','D','Eb','E','F','F#','G','Ab','A','Bb','B'])[root_pc + 1] ||
           CASE quality WHEN 'MINOR7' THEN 'm7' WHEN 'DOMINANT7' THEN '7' WHEN 'MAJOR7' THEN 'maj7'
                        WHEN 'HALF_DIMINISHED7' THEN 'm7b5' WHEN 'MAJOR6' THEN '6' ELSE '' END,
       root_pc, quality, roman, 'TONIC', 'synthetic', 'C major'
FROM (
    SELECT sg.analysis_id,
           row_number() OVER (PARTITION BY sg.analysis_id ORDER BY sg.seg_no, t.step) AS position,
           (sg.tonic + t.semitones) % 12 AS root_pc,
           t.quality, t.roman
    FROM synth_segment sg
    JOIN synth_template t ON t.template = sg.template
) c;

INSERT INTO scale_suggestions (chord_id, rank, scale_name, scale_notes, reason)
SELECT c.id, r.rank, 'SYNTHETIC scale', 'C D E F G A B', 'synthetic'
FROM chords c
JOIN synth_song s ON s.analysis_id = c.analysis_id
CROSS JOIN (VALUES (1), (2)) AS r(rank);

SELECT (SELECT count(*) FROM songs WHERE title LIKE 'SYNTHETIC #%')            AS synthetic_songs,
       (SELECT count(*) FROM chords c JOIN synth_song s USING (analysis_id))   AS synthetic_chords,
       (SELECT count(*) FROM scale_suggestions WHERE reason = 'synthetic')     AS synthetic_scale_rows;
