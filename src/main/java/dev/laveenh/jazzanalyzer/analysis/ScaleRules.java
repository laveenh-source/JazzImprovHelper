package dev.laveenh.jazzanalyzer.analysis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.laveenh.jazzanalyzer.domain.ChordFunction;
import dev.laveenh.jazzanalyzer.domain.ChordQuality;
import dev.laveenh.jazzanalyzer.domain.Mode;
import dev.laveenh.jazzanalyzer.domain.Scale;
import dev.laveenh.jazzanalyzer.domain.Note;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ranked scale rule table, loaded from {@code theory/scale-rules.json} and validated on load,
 * so a typo in the data file fails fast with a clear message instead of silently never matching.
 */
public final class ScaleRules {

    private static final String RESOURCE = "/theory/scale-rules.json";
    private static final Set<String> DEGREES =
            Set.of("I", "bII", "II", "bIII", "III", "IV", "#IV", "V", "bVI", "VI", "bVII", "VII");

    @JsonIgnoreProperties("_readme")
    record RulesFile(Map<String, String> scales, List<Rule> rules) {
    }

    record Rule(String id, String origin, String note, When when, List<Suggestion> suggestions) {
    }

    record When(List<String> quality, List<String> function, List<String> degree, List<String> resolution,
                String mode, List<String> alterations, List<String> exactAlterations, Boolean alt) {
    }

    record Suggestion(String scale, String reason) {
    }

    private final Map<String, String> scales;
    private final List<Rule> rules;

    private ScaleRules(RulesFile file) {
        this.scales = file.scales();
        this.rules = file.rules();
        validate();
    }

    public static ScaleRules loadDefault() {
        return load(RESOURCE);
    }

    static ScaleRules load(String resource) {
        try (InputStream in = ScaleRules.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing resource " + resource);
            }
            return new ScaleRules(new ObjectMapper().readValue(in, RulesFile.class));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource + ": " + e.getMessage(), e);
        }
    }

    /** The first rule whose conditions all match, or null when nothing applies. */
    Rule firstMatch(ChordContext context) {
        return rules.stream().filter(rule -> matches(rule.when(), context)).findFirst().orElse(null);
    }

    /** Spells the named scale on the given root, e.g. ("Dorian", D) = D E F G A B C. */
    Scale scale(String name, Note root) {
        return Scale.build(name, root, scales.get(name));
    }

    // ------------------------------------------------------------ matching

    private static boolean matches(When when, ChordContext c) {
        if (when == null) {
            return true;
        }
        return oneOf(when.quality(), c.chord().quality().name())
                && oneOf(when.function(), c.function().name())
                && oneOfIgnoreCase(when.degree(), c.degree())
                && oneOf(when.resolution(), c.resolution().name())
                && (when.mode() == null || when.mode().equals(c.key().mode().name()))
                && (when.alt() == null || when.alt() == c.chord().alt())
                && hasAllAlterations(when.alterations(), c)
                && hasExactlyAlterations(when.exactAlterations(), c);
    }

    private static boolean oneOf(List<String> allowed, String value) {
        return allowed == null || allowed.contains(value);
    }

    private static boolean oneOfIgnoreCase(List<String> allowed, String value) {
        return allowed == null || allowed.stream().anyMatch(a -> a.equalsIgnoreCase(value));
    }

    private static boolean hasAllAlterations(List<String> required, ChordContext c) {
        if (required == null) {
            return true;
        }
        List<String> present = c.chord().alterations().stream().map(Object::toString).toList();
        return present.containsAll(required);
    }

    /** True when the chord's alterations are exactly this set: 'just a b9', not 'b9 and something else'. */
    private static boolean hasExactlyAlterations(List<String> exact, ChordContext c) {
        if (exact == null) {
            return true;
        }
        Set<String> present = c.chord().alterations().stream().map(Object::toString).collect(Collectors.toSet());
        return present.equals(Set.copyOf(exact));
    }

    // ------------------------------------------------------------ validation

    private void validate() {
        if (scales == null || rules == null) {
            throw new IllegalStateException("scale-rules.json needs both 'scales' and 'rules'");
        }
        Note c = Note.parse("C");
        scales.forEach((name, formula) -> {
            try {
                Scale.build(name, c, formula);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Scale '" + name + "' has a bad formula: " + e.getMessage(), e);
            }
        });
        for (Rule rule : rules) {
            String where = "Rule '" + rule.id() + "': ";
            if (rule.suggestions() == null || rule.suggestions().isEmpty()) {
                throw new IllegalStateException(where + "needs at least one suggestion");
            }
            for (Suggestion s : rule.suggestions()) {
                if (!scales.containsKey(s.scale())) {
                    throw new IllegalStateException(where + "unknown scale '" + s.scale() + "'");
                }
            }
            validateWhen(where, rule.when());
        }
    }

    private static void validateWhen(String where, When when) {
        if (when == null) {
            return;
        }
        checkEnum(where, "quality", when.quality(), names(ChordQuality.values()));
        checkEnum(where, "function", when.function(), names(ChordFunction.values()));
        checkEnum(where, "resolution", when.resolution(), names(Resolution.values()));
        if (when.mode() != null) {
            checkEnum(where, "mode", List.of(when.mode()), names(Mode.values()));
        }
        if (when.degree() != null) {
            for (String degree : when.degree()) {
                boolean known = DEGREES.stream().anyMatch(d -> d.equalsIgnoreCase(degree));
                if (!known) {
                    throw new IllegalStateException(where + "unknown degree '" + degree + "' (use " + DEGREES + ")");
                }
            }
        }
    }

    private static void checkEnum(String where, String field, List<String> values, Set<String> allowed) {
        if (values == null) {
            return;
        }
        for (String v : values) {
            if (!allowed.contains(v)) {
                throw new IllegalStateException(where + "unknown " + field + " '" + v + "' (use " + allowed + ")");
            }
        }
    }

    private static Set<String> names(Enum<?>[] values) {
        return Set.copyOf(Arrays.stream(values).map(Enum::name).toList());
    }
}
