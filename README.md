# Jazz Chart Analyzer

A rule-based jazz chart analysis engine: upload a MusicXML lead sheet, get back the key(s),
Roman-numeral function of every chord, and ranked improvisation scales with plain-English reasons.

> **Status: Phase 1 of 7 (library core).** Parser and chord classification are done.
> Key detection, scale suggestions, the REST API, database, Docker, CI and AWS deployment are
> still to come. Rule-based by design: no audio, no ML, no image recognition.

## Run the tests

```bash
mvn verify        # tests + JaCoCo coverage report (target/site/jacoco/index.html) + 80% gate on domain/analysis
```

Requires Java 21 and Maven 3.9+.

## Results

_Measured numbers (coverage, analysis time, query timings) will be added once the relevant phases are done._
