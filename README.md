# Jazz Chart Analyzer

A rule-based jazz chart analysis engine: upload a MusicXML lead sheet, get back the key(s),
Roman-numeral function of every chord, and ranked improvisation scales with plain-English reasons.

> **Status: Phase 3 of 7 (API and database).** The analysis engine, a REST API, a PostgreSQL schema
> (Flyway) and OpenAPI docs are done. Background processing and search, Docker, CI and AWS deployment
> are still to come. Rule-based by design: no audio, no ML, no image recognition.

## Run the tests

```bash
mvn verify        # tests + JaCoCo coverage report (target/site/jacoco/index.html) + 80% gate on domain/analysis
```

Requires Java 21 and Maven 3.9+.

## Analyse a chart from the command line

```bash
mvn -q compile exec:java -Dexec.args="src/test/resources/fixtures/minor-ii-v-i.xml"
```

Prints the key regions (with the reason for each), then every chord with its Roman numeral, function and
ranked scales. The scale advice lives in `src/main/resources/theory/scale-rules.json`; every rule is tagged
`"origin": "spec"` or `"origin": "added"` so the added ones are easy to review.

## Run the API

The app needs a PostgreSQL database (Docker Compose will provide one in Phase 5). With a local Postgres:

```bash
createdb jazz        # and a role: user "jazz", password "jazz" (the local-development defaults)
mvn spring-boot:run  # or: java -jar target/jazz-analyzer-0.1.0-SNAPSHOT.jar
```

Override the defaults with `DB_URL`, `DB_USER`, `DB_PASSWORD` and `STORAGE_DIR` (uploaded files). Then:

```bash
# upload (202 Accepted; the same file again returns 200 with the existing analysis)
curl -F "file=@chart.xml" localhost:8080/api/v1/songs

curl localhost:8080/api/v1/analyses/<analysisId>                 # status + full result
curl "localhost:8080/api/v1/songs?key=Bb&sort=title,asc&size=10" # list, filter by key, paginate
curl localhost:8080/api/v1/songs/<songId>                        # song + latest analysis summary
curl localhost:8080/api/v1/songs/<songId>/chords                 # per-chord detail
curl -X DELETE localhost:8080/api/v1/songs/<songId>              # 204
curl localhost:8080/actuator/health
```

Interactive docs: `http://localhost:8080/swagger-ui.html` (OpenAPI JSON at `/v3/api-docs`).
The tests start their own PostgreSQL with Testcontainers, so they need Docker running (`mvn verify`).

## Results

_Measured numbers (coverage, analysis time, query timings) will be added once the relevant phases are done._
