package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.analysis.ProgressionPattern;
import dev.laveenh.jazzanalyzer.persistence.ProgressionSearchRepository;
import dev.laveenh.jazzanalyzer.persistence.StatsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Progression search and library statistics. */
@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final Pattern KEY = Pattern.compile("^([A-Ga-g])([#b]?)(?:\\s+(?:major|minor))?$", Pattern.CASE_INSENSITIVE);
    private static final int[] NATURAL_PITCH_CLASS = {9, 11, 0, 2, 4, 5, 7};   // A B C D E F G

    private final ProgressionSearchRepository search;
    private final StatsRepository stats;

    public SearchService(ProgressionSearchRepository search, StatsRepository stats) {
        this.search = search;
        this.stats = stats;
    }

    /** @throws IllegalArgumentException with a user-readable message for an unknown pattern or key */
    public ProgressionSearchRepository.Page findProgression(String patternText, String keyText, int limit, long offset) {
        ProgressionPattern pattern = ProgressionPattern.parse(patternText).orElseThrow(() ->
                new IllegalArgumentException("Unknown pattern '" + patternText + "'. Supported patterns: "
                        + ProgressionPattern.supportedNames() + " (capitalisation matters: ii-V-I is major, ii-V-i is minor)."));
        return search.search(pattern, tonicPitchClass(keyText), limit, offset);
    }

    public List<StatsRepository.QualityCount> chordQualities(int limit) {
        return stats.topChordQualities(limit);
    }

    public List<StatsRepository.TransitionCount> transitions(int limit) {
        return stats.topTransitions(limit);
    }

    /**
     * "Bb", "bb", "F#" or "G minor" become a pitch class 0-11; null/blank means any key. The mode word is
     * accepted but ignored: the pattern itself says whether it is major (ii-V-I) or minor (ii-V-i).
     */
    static Integer tonicPitchClass(String keyText) {
        if (keyText == null || keyText.isBlank()) {
            return null;
        }
        Matcher m = KEY.matcher(keyText.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Unknown key '" + keyText + "'. Use a note name such as Bb, F# or 'G minor'.");
        }
        int letter = Character.toUpperCase(m.group(1).charAt(0)) - 'A';
        int pitchClass = NATURAL_PITCH_CLASS[letter];
        String accidental = m.group(2);
        if (accidental.equals("#")) {
            pitchClass++;
        } else if (accidental.toLowerCase(Locale.ROOT).equals("b")) {
            pitchClass--;
        }
        return Math.floorMod(pitchClass, 12);
    }
}
