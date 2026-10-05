package dev.laveenh.jazzanalyzer.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaleTest {

    private static String spell(String root, String formula) {
        return Scale.build("x", Note.parse(root), formula).notes().stream()
                .map(Note::toString).reduce((a, b) -> a + " " + b).orElseThrow();
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = ';', value = {
            "C;1 2 b3 4 5 6 b7;C D Eb F G A Bb",
            "D;1 2 b3 4 5 6 b7;D E F G A B C",
            "F#;1 2 3 #4 5 6 7;F# G# A# B# C# D# E#",
            "G;1 2 3 4 5 6 b7;G A B C D E F",
            "Bb;1 b2 b3 3 b5 b6 b7;Bb Cb Db D Fb Gb Ab",
            "Db;1 2 3 #4 5 6 b7;Db Eb F G Ab Bb Cb",
            "E;1 b2 3 4 5 b6 b7;E F G# A B C D",
            "C;1 2 b3 4 b5 b6 6 7;C D Eb F Gb Ab A B",
            "C;1 b2 b3 3 #4 5 6 b7;C Db Eb E F# G A Bb",
            "C;1 2 3 #4 #5 b7;C D E F# G# Bb",
            "A;1 2 b3 4 b5 b6 b7;A B C D Eb F G"})
    void spellsEachDegreeWithItsOwnLetter(String root, String formula, String expected) {
        assertThat(spell(root, formula)).isEqualTo(expected);
    }

    @Test
    void toStringShowsRootAndName() {
        assertThat(Scale.build("Dorian", Note.parse("D"), "1 2 b3 4 5 6 b7")).hasToString("D Dorian");
    }

    @Test
    void rejectsBadFormulas() {
        assertThatThrownBy(() -> Scale.degreeNote(Note.parse("C"), "8")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Scale.degreeNote(Note.parse("C"), "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
