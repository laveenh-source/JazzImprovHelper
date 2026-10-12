package dev.laveenh.jazzanalyzer.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchServiceKeyParsingTest {

    @ParameterizedTest
    @CsvSource({"C,0", "c,0", "Db,1", "C#,1", "D,2", "Eb,3", "eb,3", "E,4", "F,5", "F#,6", "Gb,6", "G,7",
            "Ab,8", "G#,8", "A,9", "Bb,10", "bb,10", "B,11", "Cb,11", "B#,0", "'Bb major',10", "'G minor',7",
            "'f# MINOR',6", "'  A  ',9"})
    void readsNoteNames(String text, int pitchClass) {
        assertThat(SearchService.tonicPitchClass(text)).isEqualTo(pitchClass);
    }

    @Test
    void blankMeansAnyKey() {
        assertThat(SearchService.tonicPitchClass(null)).isNull();
        assertThat(SearchService.tonicPitchClass("")).isNull();
        assertThat(SearchService.tonicPitchClass("   ")).isNull();
    }

    @ParameterizedTest
    @CsvSource({"H", "Bbb", "Bb dorian", "major", "1", "'C; DROP TABLE songs'"})
    void rejectsAnythingElse(String text) {
        assertThatThrownBy(() -> SearchService.tonicPitchClass(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown key");
    }
}
