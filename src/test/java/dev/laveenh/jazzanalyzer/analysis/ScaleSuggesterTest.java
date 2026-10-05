package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.domain.ChordQuality;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.Mode;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.domain.ScaleSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static dev.laveenh.jazzanalyzer.TestCharts.chart;
import static dev.laveenh.jazzanalyzer.TestCharts.chord;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaleSuggesterTest {

    private final KeyDetector detector = new KeyDetector();
    private final FunctionAnalyzer functions = new FunctionAnalyzer();
    private final ScaleSuggester suggester = new ScaleSuggester();

    /** Runs key detection, function analysis and scale suggestion, returns scale types for the chord at index. */
    private List<String> scalesFor(String keyText, String progression, int index) {
        Key signature = keyText == null ? null
                : new Key(Note.parse(keyText.split(" ")[0]), Mode.valueOf(keyText.split(" ")[1].toUpperCase()));
        var chart = chart(signature, progression);
        var keys = detector.detect(chart).chordKeys();
        var f = functions.analyze(chart.chords(), keys).get(index);
        return suggester.suggest(chart.chords().get(index).chord(), keys.get(index), f).stream()
                .map(ScaleSuggestion::scaleType).toList();
    }

    @ParameterizedTest(name = "[{index}] chord {2} of \"{1}\" -> {3}")
    @CsvSource(delimiter = ';', value = {
            // the rule table from the project description
            "C major;Cmaj7 | Fmaj7 | Dm7 | G7 | Cmaj7;0;Ionian,Lydian",               // maj7 as I
            "C major;Cmaj7 | Fmaj7 | Dm7 | G7 | Cmaj7;1;Lydian,Ionian",               // maj7 as IV
            "C major;Cmaj7 | Fmaj7 | Dm7 | G7 | Cmaj7;2;Dorian",                      // m7 as ii
            "C major;Cmaj7 | Em7 | Dm7 | G7 | Cmaj7;1;Phrygian",                      // m7 as iii
            "C major;Cmaj7 | Am7 | Dm7 | G7 | Cmaj7;1;Aeolian",                       // m7 as vi
            "C major;Cmaj7 | Am7 | Dm7 | G7 | Cmaj7;3;Mixolydian,Altered",            // dom7 as V
            "C major;Dm7 | A7 | Dm7;1;Phrygian dominant,Altered,Mixolydian b13",      // dom7 resolving to minor
            "C major;Dm7 | Db7 | Cmaj7;1;Lydian dominant",                             // bII7
            "C major;Cmaj7 | G7#11 | Cmaj7;1;Lydian dominant",                         // dom7 with #11
            "C major;Cmaj7 | G7alt | Cmaj7;1;Altered,Half-whole diminished",           // 7alt
            "C major;Cmaj7 | Bm7b5 | Cmaj7;1;Locrian,Locrian #2",                     // m7b5
            "C major;Cmaj7 | C#dim7 | Dm7;1;Whole-half diminished",                   // dim7
            "C minor;Cm7 | CmMaj7 | Cm7;1;Melodic minor,Harmonic minor",              // mMaj7
            "C major;Cmaj7 | Cmaj7#5 | Cmaj7;1;Lydian augmented",                     // maj7#5
            // additions beyond the original table
            "C major;Cmaj7 | G7b13 | Cmaj7;1;Mixolydian b13",
            "C major;Cmaj7 | G7b9 | Cmaj7;1;Half-whole diminished,Phrygian dominant",
            "C major;Cmaj7 | G7b9#11 | Cmaj7;1;Half-whole diminished",
            "C major;Cmaj7 | G7sus4 | Cmaj7;1;Mixolydian",
            "C major;Cmaj7 | G7#5 | Cmaj7;1;Whole tone,Altered",
            "C major;Cmaj7 | Caug | Cmaj7;1;Lydian augmented,Whole tone",
            "C major;Cmaj7 | Bdim | Cmaj7;1;Whole-half diminished",
            "C major;Cmaj7 | Bb7 | Cmaj7;1;Lydian dominant,Mixolydian",                // backdoor bVII7
            "C major;Cmaj7 | C7 | Fmaj7;1;Mixolydian,Altered",                         // V7/IV
            "C major;Cmaj7 | Cm6 | Cmaj7;1;Dorian,Melodic minor",
            "C minor;Cm7 | Fm7 | Cm7;0;Dorian,Aeolian",                                // minor tonic
            "C minor;Cm7 | Fm7 | Cm7;1;Dorian,Aeolian",                                // iv
            "C major;Cmaj7 | Abmaj7 | Cmaj7;1;Lydian,Ionian",
            "C major;Cmaj7 | Gmaj7 | Cmaj7;1;Ionian,Lydian",
            "C major;Cmaj7 | Bbm7 | Cmaj7;1;Dorian",
            "C major;Cmaj7 | F#7 | Cmaj7;1;Mixolydian"
    })
    void suggestsTheDocumentedScales(String key, String progression, int index, String expectedScales) {
        assertThat(scalesFor(key, progression, index)).containsExactlyElementsOf(List.of(expectedScales.split(",")));
    }

    @Test
    void suggestionsAreRankedSpelledAndExplained() {
        var chart = chart(new Key(Note.parse("C"), Mode.MAJOR), "Dm7 | G7 | Cmaj7");
        var keys = detector.detect(chart).chordKeys();
        var f = functions.analyze(chart.chords(), keys).get(0);

        List<ScaleSuggestion> suggestions = suggester.suggest(chart.chords().get(0).chord(), keys.get(0), f);

        assertThat(suggestions).hasSize(1);
        ScaleSuggestion dorian = suggestions.get(0);
        assertThat(dorian.rank()).isEqualTo(1);
        assertThat(dorian.name()).isEqualTo("D Dorian");
        assertThat(dorian.scaleType()).isEqualTo("Dorian");
        assertThat(dorian.notes()).extracting(Note::toString).containsExactly("D", "E", "F", "G", "A", "B", "C");
        assertThat(dorian.reason()).isEqualTo("Standard choice over a ii chord");
    }

    @Test
    void ranksAreConsecutiveFromOne() {
        var chart = chart(new Key(Note.parse("C"), Mode.MAJOR), "Dm7 | A7 | Dm7");
        var keys = detector.detect(chart).chordKeys();
        var f = functions.analyze(chart.chords(), keys).get(1);
        assertThat(suggester.suggest(chart.chords().get(1).chord(), keys.get(1), f))
                .extracting(ScaleSuggestion::rank).containsExactly(1, 2, 3);
    }

    @Test
    void scalesAreSpelledFromTheChordRoot() {
        var chart = chart(new Key(Note.parse("Bb"), Mode.MAJOR), "Cm7 | F7 | Bbmaj7");
        var keys = detector.detect(chart).chordKeys();
        var f = functions.analyze(chart.chords(), keys).get(1);
        var mixolydian = suggester.suggest(chart.chords().get(1).chord(), keys.get(1), f).get(0);
        assertThat(mixolydian.name()).isEqualTo("F Mixolydian");
        assertThat(mixolydian.notes()).extracting(Note::toString).containsExactly("F", "G", "A", "Bb", "C", "D", "Eb");
    }

    @Test
    void chordsWeDoNotModelGetNoSuggestionsInsteadOfAGuess() {
        Chord unknown = new Chord(Note.parse("C"), ChordQuality.UNKNOWN, 0, null, null, false);
        var f = new FunctionResult("?", dev.laveenh.jazzanalyzer.domain.ChordFunction.UNCLASSIFIED, "", "I", Resolution.NONE);
        assertThat(suggester.suggest(unknown, new Key(Note.parse("C"), Mode.MAJOR), f)).isEmpty();
    }

    // ------------------------------------------------------------ the data file itself

    @Test
    void everyShippedRuleSaysWhereItCameFrom() throws java.io.IOException {
        // 'spec' = from the original rule table, 'added' = needs the author's review
        try (var in = getClass().getResourceAsStream("/theory/scale-rules.json")) {
            var rules = new com.fasterxml.jackson.databind.ObjectMapper().readTree(in).get("rules");
            assertThat(rules).isNotEmpty();
            for (var rule : rules) {
                assertThat(rule.get("origin").asText()).as(rule.get("id").asText()).isIn("spec", "added");
            }
        }
        assertThat(ScaleRules.loadDefault()).isNotNull();
    }

    @Test
    void firstMatchingRuleWinsAndAnEmptyWhenMatchesEverything() {
        ScaleRules rules = ScaleRules.load("/bad-rules/mode-and-fallback.json");
        ScaleSuggester custom = new ScaleSuggester(rules);
        var f = new FunctionResult("i7", dev.laveenh.jazzanalyzer.domain.ChordFunction.TONIC, "", "I", Resolution.NONE);

        Chord am7 = chord("Am7");
        assertThat(custom.suggest(am7, new Key(Note.parse("A"), Mode.MINOR), f))
                .extracting(ScaleSuggestion::scaleType).containsExactly("Aeolian");
        assertThat(custom.suggest(am7, new Key(Note.parse("C"), Mode.MAJOR), f))
                .extracting(ScaleSuggestion::scaleType).containsExactly("Dorian");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = ';', value = {
            "unknown-scale.json;unknown scale 'Doriann'",
            "unknown-quality.json;unknown quality 'MINOR_SEVENTH'",
            "unknown-degree.json;unknown degree 'ii7'",
            "unknown-function.json;unknown function 'SUBDOM'",
            "bad-formula.json;bad formula",
            "no-suggestions.json;at least one suggestion",
            "missing-rules.json;needs both 'scales' and 'rules'"
    })
    void aBrokenDataFileFailsFastWithAClearMessage(String file, String messagePart) {
        assertThatThrownBy(() -> ScaleRules.load("/bad-rules/" + file))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(messagePart);
    }

    @Test
    void aMisspelledFieldIsRejectedRatherThanIgnored() {
        assertThatThrownBy(() -> ScaleRules.load("/bad-rules/typo-field.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("qualty");
    }

    @Test
    void aWrongTypeForAFieldIsRejected() {
        assertThatThrownBy(() -> ScaleRules.load("/bad-rules/wrong-type-mode.json"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMissingResourceIsReported() {
        assertThatThrownBy(() -> ScaleRules.load("/bad-rules/nope.json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing resource");
    }
}
