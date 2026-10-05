# Jazz Chart Analyzer

A rule-based jazz chart analysis engine: upload a MusicXML lead sheet, get back the key(s),
Roman-numeral function of every chord, and ranked improvisation scales with plain-English reasons.

> **Status: Phase 2 of 7 (analysis engine).** Parsing, chord classification, key detection with
> local key regions, Roman-numeral analysis and data-driven scale suggestions are done, with a CLI.
> The REST API, database, Docker, CI and AWS deployment are still to come. Rule-based by design: no audio, no ML, no image recognition.

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

## Results

_Measured numbers (coverage, analysis time, query timings) will be added once the relevant phases are done._
