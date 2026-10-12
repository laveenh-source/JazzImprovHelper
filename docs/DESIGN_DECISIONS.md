# Design decisions

Short notes on *why* things are the way they are. Each one is something to be able to explain in an interview.
For a worked example of the analysis, see [HOW_IT_WORKS.md](HOW_IT_WORKS.md).

## Music theory engine

### Rule-based analysis, not machine learning
The task is well defined and the rules are known: ii-V-I patterns, Roman-numeral function, scale-chord
relationships. Rules are **explainable** (every chord comes with a one-line reason), **testable** (the worked examples
are unit tests), **editable by a musician** who disagrees (a data file, not a trained model), and need no training data
(which for jazz charts would be copyrighted). The price is that it only knows what it was taught: anything outside its
rules is labelled `UNCLASSIFIED` rather than guessed. That is a deliberate trade, and the README says so.

### A pure-Java engine with no framework in it
`domain` and `analysis` have no Spring imports. They can be unit-tested in milliseconds, reused by the CLI, and
the 80% coverage gate applies to exactly the part that contains the music theory. Spring is a layer around it.

### Key detection is a scoring model plus dynamic programming
Every chord scores against all 24 keys (how many chord tones fit the scale, plus bonuses for ii-V-I evidence, tonic
chords and the key signature). A dynamic-programming pass picks one key per chord, with a **penalty for changing key**.
Why not simple rules? A single chromatic chord should not flip the key, but a real modulation must. The penalty is the
price of a key change. It was tuned against the test tunes (including a 32-bar minor ii-V-i tune): every key-detection test passes for
penalties from 1.0 to 2.0 and fails outside that range, so 1.5 sits in the middle of the working range.
The first chord of a tune gets almost no weight, because tunes often start on IV or ii; the key signature and the
resolving chords decide.

### Scale advice lives in a data file (first match wins)
`theory/scale-rules.json` is an ordered list of rules. The first rule whose conditions match is used, so specific
rules go above general ones. The file is validated at startup (unknown scale, quality, degree or even a
misspelled field fails loudly), because a rule that silently never matches is the worst kind of bug. Each rule is
tagged `spec` (from the original table) or `added`, so a reviewer knows which to check.

### Scales are spelled by scale degree
F# Lydian contains E#, not F. Each degree gets its own letter name, then the accidental is computed. A table of
pitch classes would be shorter but would print wrong note names.

### Chord symbols are normalised once, in one place
`Cmaj7#5`, `C7#5`, `C13`, `Cadd9` can each arrive in several MusicXML shapes. `ChordClassifier` folds them into
one form. The conventions are the musician's: a plain triad with an *alteration* implies a flat 7th, a natural
added tone does not (`Cadd9` is C E G D), and a major 7th is never implied.

## API and persistence

### Spring Boot 4, Flyway, JPA for CRUD and SQL for analysis
Flyway owns the schema (versioned SQL files in git); Hibernate is set to `validate`, so it fails at startup if
the entities and the tables disagree. This caught real mistakes during development (a `NUMERIC` column for a `double`,
a `CHAR` for a `String`). JPA is used where it is good (simple reads and writes). The song list, the progression
search and the statistics are hand-written SQL, because window functions, `LATERAL` joins and CTEs say what they mean
more clearly than JPQL does.

### The checksum is the identity of a song
SHA-256 of the file, `UNIQUE` in the database. Uploading the same bytes again returns the existing analysis instead of
repeating work, even under a different file name. A previous *failed* analysis is retried instead, because the
failure may have been fixed since. A race between two identical uploads is settled by the `UNIQUE` constraint, not by
application locking.

### An `AnalysisExecutor` seam; async by default
The upload endpoint returns **202 Accepted** at once and the client polls. The work runs on a **bounded** thread pool
with a **bounded** queue; when both are full the answer is **503 with Retry-After**, not an out-of-memory crash an hour
later. Tests use a synchronous implementation of the same interface so they can assert on the finished result. Status
changes are short, separate transactions, and the slow work (parse, analyse) happens outside any transaction so a
database connection is never held while the CPU works.

### Failures are data
A malformed or empty-of-chords file never crashes a worker: the analysis moves to `FAILED` with a readable message.
Malformed XML is caught at upload (400) because it is cheap and nothing needs storing; a valid file with no chords is
accepted and fails in the analysis, because that is a musical problem found by the analysis. Analyses stranded by a
restart are marked `FAILED` at startup.

### Security, in layers
- **XXE**: the XML parser refuses external entities and external DTDs (but accepts the harmless DOCTYPE exporters write);
  tests prove a payload cannot read a local file.
- **Uploads**: size, extension and well-formedness are checked; files are stored under **generated keys**, never under the
  uploaded name; a display name has its path and control characters stripped.
- **SQL**: values are bind parameters; the one dynamic part (`ORDER BY`) comes from a whitelist.
- **Errors**: one JSON shape; unexpected errors return a generic message while the details go to the log.
- **Rate limiting**: per client IP with a token bucket (Bucket4j): a small bucket for uploads, a larger one for reads.
  It is in memory per instance, which is fine for one task and would need a shared store for many.
- **Served files**: the user's XML is only ever sent as a sandboxed download, so it cannot run script in the browser.
- **Secrets**: nothing in git. The database password is generated by RDS and injected by ECS from Secrets Manager; GitHub
  deploys with a short-lived OIDC token, not stored keys.

### Caching only what cannot change
A `COMPLETE` analysis is immutable, so it is cached (Caffeine) and dropped when its song is deleted. Pending,
running and failed analyses are never cached, since they are about to change.

### Indexes were measured, not assumed
The first schema had no secondary indexes on purpose, so the "before" `EXPLAIN ANALYZE` was honest. Indexes were added
in stages, with a measurement after each. See [PERFORMANCE.md](PERFORMANCE.md) for what helped, and what did not.

## Infrastructure

### Storage behind an interface, chosen by profile
`FileStorage` has a local-disk and an S3 implementation, selected by the Spring profile. Docker Compose runs the
**S3** implementation against a local S3 stand-in, so the production code path is exercised on every laptop and in
CI, not just on AWS.

### One multi-stage image, non-root
Build with the JDK and Maven; ship a slim JRE and one jar, running as an unprivileged user, with a health check
and a JVM that exits on out-of-memory so ECS restarts it.

### Explicit Terraform, no stored credentials, a budget first
Every AWS resource is written out and small enough to read. IAM is least-privilege per role. The deploy workflow uses
GitHub OIDC. A budget with email alerts is created by the same Terraform. See [AWS_DEPLOYMENT.md](AWS_DEPLOYMENT.md).

### Vendored front-end library
The sheet-music library is committed (with its licence, version and integrity hash) instead of loaded from a CDN: the
page works offline, the content-security policy can forbid third-party scripts, and a CDN problem cannot affect it.

## What was left out, and why
- **Audio, machine learning, image recognition**: out of scope by design (see the README).
- **Compressed `.mxl` files**: needs zip-bomb protection; not done. The API says so clearly when one is uploaded.
- **Authentication (users, BCrypt, JWT)**: an optional stretch goal; the API is open. Not implemented.
- **A real message queue (SQS)**: an in-process pool is enough for one instance; the `AnalysisExecutor` interface is where one would plug in.
- **HTTPS by default**: needs a domain and a certificate; supported by Terraform when you provide one.
