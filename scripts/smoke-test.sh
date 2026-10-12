#!/usr/bin/env bash
# End-to-end check of a RUNNING system (docker compose, or a deployed URL):
# upload a chart, wait for the analysis, check the answer, run a progression search, clean up.
#
#   scripts/smoke-test.sh [base-url]        default http://localhost:8080
set -euo pipefail

BASE="${1:-http://localhost:8080}"
FIXTURE="$(cd "$(dirname "$0")/.." && pwd)/src/test/resources/fixtures/ii-v-i-c-major.xml"
fail() { echo "SMOKE TEST FAILED: $*" >&2; exit 1; }
json() { python3 -c "import sys, json; d = json.load(sys.stdin); print($1)"; }

echo "1. waiting for $BASE/actuator/health"
for _ in $(seq 1 60); do
  curl -fsS "$BASE/actuator/health" 2>/dev/null | grep -q '"status":"UP"' && break
  sleep 2
done
curl -fsS "$BASE/actuator/health" | grep -q '"status":"UP"' || fail "the service never became healthy"

echo "2. uploading a chart"
UPLOAD="$(curl -fsS -F "file=@$FIXTURE" "$BASE/api/v1/songs")"
ANALYSIS="$(echo "$UPLOAD" | json "d['analysisId']")"
SONG="$(echo "$UPLOAD" | json "d['songId']")"

echo "3. waiting for the analysis $ANALYSIS"
STATUS=""
for _ in $(seq 1 30); do
  RESULT="$(curl -fsS "$BASE/api/v1/analyses/$ANALYSIS")"
  STATUS="$(echo "$RESULT" | json "d['status']")"
  [ "$STATUS" = "COMPLETE" ] || [ "$STATUS" = "FAILED" ] && break
  sleep 1
done
[ "$STATUS" = "COMPLETE" ] || fail "analysis ended as '$STATUS'"
[ "$(echo "$RESULT" | json "d['detectedKey']")" = "C major" ] || fail "wrong key"
[ "$(echo "$RESULT" | json "[c['roman'] for c in d['chords']]")" = "['ii7', 'V7', 'Imaj7']" ] || fail "wrong Roman numerals"
[ "$(echo "$RESULT" | json "d['chords'][0]['scales'][0]['name']")" = "D Dorian" ] || fail "wrong first scale"

echo "4. searching for the progression"
FOUND="$(curl -fsS "$BASE/api/v1/search/progressions?pattern=ii-V-I&key=C" | json "d['totalElements']")"
[ "$FOUND" -ge 1 ] || fail "ii-V-I in C was not found"

echo "5. cleaning up"
curl -fsS -X DELETE -o /dev/null "$BASE/api/v1/songs/$SONG"

echo "SMOKE TEST PASSED"
