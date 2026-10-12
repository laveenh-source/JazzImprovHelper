# Examples

`demo-tune.xml` is an original 16-bar tune (chords and a throw-away melody written for this project, not taken from
any song). It has a secondary dominant (A7b9 to Dm7), a tritone substitution (Db7 to Cmaj7), a borrowed chord (Fm7),
and a diminished passing chord (C#dim7). Upload it on the web page, or:

```bash
curl -F "file=@docs/examples/demo-tune.xml" localhost:8080/api/v1/songs
mvn -q compile exec:java -Dexec.args="docs/examples/demo-tune.xml"
```
