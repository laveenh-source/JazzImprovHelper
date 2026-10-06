package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.Scale;
import dev.laveenh.jazzanalyzer.domain.ScaleSuggestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Suggests ranked improvisation scales for a chord by looking it up in the data-driven rule table.
 * There is no theory hard-coded here: change {@code theory/scale-rules.json} to change the advice.
 */
public final class ScaleSuggester {

    private final ScaleRules rules;

    public ScaleSuggester() {
        this(ScaleRules.loadDefault());
    }

    public ScaleSuggester(ScaleRules rules) {
        this.rules = rules;
    }

    /** Ranked suggestions, or an empty list when no rule applies (e.g. a chord kind we do not model). */
    public List<ScaleSuggestion> suggest(Chord chord, Key key, FunctionResult analysis) {
        ChordContext context = new ChordContext(chord, key, analysis.degree(), analysis.function(), analysis.resolution());
        ScaleRules.Rule rule = rules.firstMatch(context);
        if (rule == null) {
            return List.of();
        }
        List<ScaleSuggestion> suggestions = new ArrayList<>();
        int rank = 1;
        for (ScaleRules.Suggestion s : rule.suggestions()) {
            Scale scale = rules.scale(s.scale(), chord.root());
            suggestions.add(new ScaleSuggestion(rank++, scale.toString(), s.scale(), scale.notes(), s.reason()));
        }
        return suggestions;
    }
}
