# Performance

Every number on this page was measured by a script in this repository, on the machine described below, and can be
reproduced with the commands at the end. Nothing here is an estimate. The most useful part is not the numbers but what
they showed: the biggest win was **not an index**.

## Setup (read this before trusting any number)

| | |
|---|---|
| Machine | a 4-vCPU, 16 GB cloud sandbox (shared; no tuning, no isolation from other workloads) |
| PostgreSQL | 16.14, local, **default settings** (`shared_buffers` 128 MB, `work_mem` 4 MB) |
| Data | **synthetic**: 5,000 songs, 5,000 analyses, **328,650 chords**, 657,300 scale rows, made by `scripts/perf/generate-synthetic-data.sql` from randomly transposed standard progressions. Not real music. |
| Method | each query is run once to warm up, then 5 times; the table shows the **median** `Execution Time` from `EXPLAIN (ANALYZE)` |
| Not measured | HTTP latency, concurrent load, and real, differently-shaped data |

Results on other hardware, other settings or real data will differ. The *shape* of the findings (which fix mattered)
is more reliable than the absolute milliseconds.

## 1. The analysis engine

Time to parse and analyse one chart, in the Java engine only (no database, no HTTP), after 3,000 warm-up runs, over 5,000 runs:

| chart | parse (median) | analyse (median) | parse + analyse (median) | p95 of parse + analyse |
|---|---:|---:|---:|---:|
| 3 bars (ii-V-I) | 0.096 ms | 0.039 ms | 0.092 ms* | 0.209 ms |
| **32 bars** | 0.161 ms | 0.096 ms | **0.265 ms** | 0.455 ms |
| 256 bars | 0.797 ms | 0.823 ms | 1.679 ms | 3.161 ms |

\*the three steps are timed separately, so tiny differences like this one are noise.

These are **warm-JVM** figures. The first requests after a start are slower while the JVM compiles the code. The point
is that the music engine is never the bottleneck: a 32-bar chart is analysed in well under a millisecond, so
the upload path is dominated by reading the file and writing the rows to the database.

## 2. The database, before and after

Milliseconds, median of 5 runs, on the synthetic library above. Stage 0 has only primary keys and the unique
checksum; each later stage adds indexes (cumulative):

| query | 0: no indexes | 1: + `chords(analysis_id, position_index)` | 2: + `analyses(song_id, created_at DESC)` and two foreign keys | 3: + `chords(quality, roman_numeral)` |
|---|---:|---:|---:|---:|
| **Song list**, newest 20 | 866 | 917 | **11** | 9 |
| **Delete one song** (cascade) | 2,580 | 2,454 | **2.2** | 2.5 |
| Search ii-V-I in Bb, **v4 (current)** | 1,101 | 942 | 976 | 968 |
| Search ii-V-I, any key, **v4 (current)** | 1,124 | 932 | 970 | 915 |
| Stats: common transitions | 538 | 426 | 398 | 410 |
| Stats: chord qualities | 187 | 209 | 182 | 187 |

### What these numbers say

1. **Foreign-key and lookup indexes: 80x to 1000x.** PostgreSQL does not index foreign keys on its own. Without
   `analyses(song_id, created_at)` the song list re-scanned the analyses for every song (866 ms to 11 ms), and without
   indexes on the two child tables, deleting one song scanned them in full to apply `ON DELETE CASCADE` (2,580 ms to 2.2 ms).
2. **`chords(analysis_id, position_index)`: a modest 14% on the search, 21% on the transitions statistic.** The plan
   before shows `Sort Method: external merge  Disk: 9112kB`, a sort that spilled to disk because `work_mem` is only 4 MB;
   after, rows come out of the index already in order and the sort is gone. The rest of the search time is the window
   functions themselves, which no index can remove.
3. **`chords(quality, roman_numeral)` did nothing.** It was the index the original plan suggested. No query got
   faster (186.6 vs 182.1 ms for the statistic it was meant for is noise), because these queries need every chord
   row anyway. **It is not in the schema** (see `V2__add_indexes.sql`), and a test asserts that it is absent.
4. The remaining cost of the search (about 0.9 s here) is real work: window functions over 328,650 rows. It grows with
   the library. The next step, if this mattered, would be to store the progression pre-computed at write time.

### The search query: the biggest win was a rewrite, not an index

The first version of the progression search took **15.2 seconds** for "any key". Three versions of the query
(kept in `scripts/perf/variants/`), measured with no indexes:

| version | what changed | Bb | any key |
|---|---|---:|---:|
| v1 | `CROSS JOIN` against the 12 possible tonics, then filter | 2,220 | not measured |
| v2 | work the tonic out from the first chord instead (no 12x multiplication) | 2,145 | **15,237** |
| v3 | carry the song id from the first join, instead of joining `latest` a second time | 1,464 | 5,214 |
| **v4 (current)** | carry the song **title** from the first join too, so nothing is joined after the window functions | **1,101** | **1,124** |

Why v2 was so slow, from its `EXPLAIN ANALYZE`: PostgreSQL cannot estimate how many rows survive a filter on a
*window function's output*, so it guessed **1 row** where there were 13,861. Trusting the guess, it chose a nested loop that
`Seq Scan`ned the whole `songs` table **13,861 times** (`loops=13861`). An index could not fix that, because the
planner never asked for one. The fix was to restructure the query so that no join happens after the window
functions (v4). It also made the search independent of whether a key is given: both cases now take about the same time.

The lesson I would repeat in an interview: *measure first, read the plan, and check whether the problem is the data
access or the shape of the query.*

## What I did not measure

- **HTTP load.** There is no load test of the running service (no k6/JMeter run).
  A k6 script is not included because I could not run one, and I would rather ship no number than an invented one.
- **Concurrency.** All timings are single-query. Several searches at once would compete for the CPU and `work_mem`.
- **The upload path end to end** (file in, rows out): only its two halves are timed (engine above; row counts in tests).
- **Real data.** Real charts have different chord counts and repeat structure than the generator produces.
- **Cold start and memory use.**

## Reproduce

```bash
# the engine (needs `mvn -q test-compile` once, and the Jackson jars on the classpath):
mvn -q test-compile
CP="target/classes:target/test-classes:$(find ~/.m2 -name 'jackson-*-2.18.2.jar' | tr '\n' ':')"
java -cp "$CP" dev.laveenh.jazzanalyzer.tools.AnalysisTimingTool

# the database (creates and drops its own database "jazz_bench"; needs a role that may create databases):
PGHOST=localhost PGUSER=jazz PGPASSWORD=jazz scripts/perf/benchmark-search.sh 5000 5
# results in target/perf/results.md, the raw plans in target/perf/explain-search-{before,after}.txt
```

To load the synthetic data into a database of your own (and remove it again):

```bash
psql -v songs=5000 -f scripts/perf/generate-synthetic-data.sql jazz
psql -f scripts/perf/delete-synthetic-data.sql jazz
```
