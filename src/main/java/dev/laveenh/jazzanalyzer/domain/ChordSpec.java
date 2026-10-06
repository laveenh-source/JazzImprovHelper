package dev.laveenh.jazzanalyzer.domain;

import java.util.List;

/**
 * The raw ingredients of a chord symbol as they appear in a file (MusicXML kind string,
 * degree list...) before they are normalized into a {@link Chord}.
 *
 * @param kindText the printed symbol from the file's {@code text} attribute, or null
 */
public record ChordSpec(Note root, String kind, String kindText, List<Degree> degrees, Note bass) {

    public enum DegreeType { ADD, ALTER, SUBTRACT }

    /** One MusicXML {@code <degree>} element, e.g. value 9, alter -1, type ADD = "add b9". */
    public record Degree(int value, int alter, DegreeType type) {
    }

    public ChordSpec {
        degrees = degrees == null ? List.of() : List.copyOf(degrees);
    }
}
