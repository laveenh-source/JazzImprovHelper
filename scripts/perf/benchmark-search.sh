#!/usr/bin/env bash
# Times the application's real SQL on a SYNTHETIC library, before and after adding indexes.
#
#   scripts/perf/benchmark-search.sh [songs=5000] [runs=7]
#
# Needs: a running PostgreSQL, a role that can create databases (PGHOST/PGUSER/PGPASSWORD, defaults
# localhost/jazz/jazz), Maven + JDK (to print the application's SQL). It creates and drops its own
# database ($BENCH_DB, default jazz_bench) and never touches your real one.
#
# Method: build the schema from the real V1 migration, load synthetic data, ANALYZE, then for every
# query run `EXPLAIN (ANALYZE)` once to warm up and <runs> more times, and report the MEDIAN
# "Execution Time". Indexes are then added in stages, with a fresh ANALYZE and a new measurement each time.
set -euo pipefail

SONGS="${1:-5000}"
RUNS="${2:-7}"
DB="${BENCH_DB:-jazz_bench}"
export PGHOST="${PGHOST:-localhost}" PGUSER="${PGUSER:-jazz}" PGPASSWORD="${PGPASSWORD:-jazz}"

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/target/perf"
mkdir -p "$OUT"
cd "$ROOT"

psqld() { psql -X -q -v ON_ERROR_STOP=1 -d "$DB" "$@"; }

[ -f target/test-classes/dev/laveenh/jazzanalyzer/tools/PrintSearchSql.class ] || mvn -q test-compile
sql_for() { java -cp target/classes:target/test-classes dev.laveenh.jazzanalyzer.tools.PrintSearchSql "$1" 2>/dev/null | grep -v JAVA_TOOL; }

# --- the queries, exactly as the application builds them, with the bind parameters filled in.
# v1 to v3 are earlier versions of the search query, kept in scripts/perf/variants so each improvement is measurable.
fill() { sed -e "s/:tonic/$1/g" -e 's/:limit/20/' -e 's/:offset/0/'; }
V=scripts/perf/variants
fill 10   < $V/search-v1-crossjoin.sql          > "$OUT/q1.sql"
fill 10   < $V/search-v2-derived-tonic.sql      > "$OUT/q2.sql"
fill 10   < $V/search-v3-song-id-carried.sql    > "$OUT/q3.sql"
sql_for ii-V-I | fill 10                        > "$OUT/q4.sql"
fill NULL < $V/search-v2-derived-tonic.sql      > "$OUT/q5.sql"
fill NULL < $V/search-v3-song-id-carried.sql    > "$OUT/q6.sql"
sql_for ii-V-I | fill NULL                      > "$OUT/q7.sql"
sql_for stats-qualities | sed -e 's/:limit/10/'                   > "$OUT/q8.sql"
sql_for stats-transitions | sed -e 's/:limit/10/'                 > "$OUT/q9.sql"
sql_for song-list | sed -e 's/:key/NULL/g' -e 's/:limit/20/' -e 's/:offset/0/' > "$OUT/q10.sql"

NAMES=("Bb v1: cross join" "Bb v2: derived tonic" "Bb v3: song id carried" "Bb v4: title carried (current)" \
       "any key v2" "any key v3" "any key v4 (current)" \
       "stats: qualities" "stats: transitions" "song list, newest 20" "delete one song")

echo ">> building $DB with $SONGS synthetic songs"
dropdb --if-exists "$DB"; createdb "$DB"
psqld -f src/main/resources/db/migration/V1__create_schema.sql
psqld -v songs="$SONGS" -f scripts/perf/generate-synthetic-data.sql
psqld -c "ANALYZE"
{
  echo "date:        $(date -u +%FT%TZ)"
  echo "postgres:    $(psqld -At -c 'select version()' | cut -c1-70)"
  echo "cpus:        $(nproc)   memory: $(free -m | awk '/Mem:/ {print $2}') MB"
  echo "shared_buf:  $(psqld -At -c 'show shared_buffers')   work_mem: $(psqld -At -c 'show work_mem')"
  psqld -At -F ' ' -c "select 'rows:       songs=' || (select count(*) from songs) || ' analyses=' || (select count(*) from analyses) || ' chords=' || (select count(*) from chords) || ' scale_suggestions=' || (select count(*) from scale_suggestions)"
} | tee "$OUT/environment.txt"

median() { sort -n | awk '{a[NR]=$1} END {print (NR%2) ? a[(NR+1)/2] : (a[NR/2]+a[NR/2+1])/2}'; }

time_select() {   # $1 = sql file
  local i
  for i in $(seq 0 "$RUNS"); do
    { echo "EXPLAIN (ANALYZE, SUMMARY ON)"; cat "$1"; echo ";"; } | psqd_stdin | awk '/Execution Time/ {print $3}'
  done | tail -n +2 | median
}
psqd_stdin() { psqld -At; }

time_delete() {
  local i
  for i in $(seq 0 "$RUNS"); do
    psqld -At <<'SQL' | awk '/Execution Time/ {print $3}'
BEGIN;
EXPLAIN (ANALYZE, SUMMARY ON) DELETE FROM songs WHERE title = 'SYNTHETIC #02500';
ROLLBACK;
SQL
  done | tail -n +2 | median
}

declare -a STAGES RESULTS
measure_stage() {
  local stage="$1" row="" v
  echo ">> measuring: $stage"
  for f in q1 q2 q3 q4 q5 q6 q7 q8 q9 q10; do
    v="$(time_select "$OUT/$f.sql")"; row+="$v|"
  done
  row+="$(time_delete)"
  STAGES+=("$stage"); RESULTS+=("$row")
}

explain_to() { { echo "EXPLAIN (ANALYZE, BUFFERS)"; cat "$OUT/q4.sql"; echo ";"; } | psqld -At > "$1"; }

measure_stage "0. no indexes (primary keys and the unique checksum only)"
explain_to "$OUT/explain-search-before.txt"

psqld -c "CREATE INDEX idx_chords_analysis_position ON chords (analysis_id, position_index)" -c "ANALYZE"
measure_stage "1. + chords (analysis_id, position_index)"

psqld -c "CREATE INDEX idx_analyses_song_created ON analyses (song_id, created_at DESC)" \
      -c "CREATE INDEX idx_key_regions_analysis ON key_regions (analysis_id)" \
      -c "CREATE INDEX idx_scale_suggestions_chord ON scale_suggestions (chord_id)" -c "ANALYZE"
measure_stage "2. + analyses (song_id, created_at DESC) and the two child-table foreign keys"

psqld -c "CREATE INDEX idx_chords_quality_roman ON chords (quality, roman_numeral)" -c "ANALYZE"
measure_stage "3. + chords (quality, roman_numeral)"
explain_to "$OUT/explain-search-after.txt"

# --- report (milliseconds, median of $RUNS runs). One row per query, one column per stage.
{
  printf '| query |'; for st in "${STAGES[@]}"; do printf ' %s |' "${st%%.*}"; done; echo
  printf '|---|'; for _ in "${STAGES[@]}"; do printf -- '---:|'; done; echo
  for q in "${!NAMES[@]}"; do
    printf '| %s |' "${NAMES[$q]}"
    for i in "${!STAGES[@]}"; do
      IFS='|' read -r -a cells <<< "${RESULTS[$i]}"
      printf ' %s |' "${cells[$q]}"
    done
    echo
  done
  echo
  echo "Stages:"; for st in "${STAGES[@]}"; do echo "- $st"; done
} | tee "$OUT/results.md"

dropdb "$DB"
echo ">> done. Raw plans: $OUT/explain-search-before.txt and $OUT/explain-search-after.txt"
