package dev.laveenh.jazzanalyzer.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteTest {

    @ParameterizedTest
    @CsvSource({"C,0", "C#,1", "Db,1", "D,2", "Eb,3", "E,4", "Fb,4", "E#,5", "F#,6", "Gb,6",
            "G,7", "Ab,8", "A,9", "Bb,10", "B,11", "Cb,11", "B#,0", "Ebb,2"})
    void pitchClassAccountsForAccidentals(String text, int expectedPitchClass) {
        assertThat(Note.parse(text).pitchClass()).isEqualTo(expectedPitchClass);
    }

    @ParameterizedTest
    @CsvSource({"C", "F#", "Bb", "Ebb", "G##"})
    void toStringRoundTripsWithParse(String text) {
        assertThat(Note.parse(text)).hasToString(text);
    }

    @Test
    void enharmonicNotesAreDifferentSpellingsOfTheSamePitchClass() {
        assertThat(Note.parse("F#")).isNotEqualTo(Note.parse("Gb"));
        assertThat(Note.parse("F#").pitchClass()).isEqualTo(Note.parse("Gb").pitchClass());
    }

    @Test
    void parseRejectsGarbage() {
        assertThatThrownBy(() -> Note.parse("H")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Note.parse("Cx")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Note.parse("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Note.parse(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
