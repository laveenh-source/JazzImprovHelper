package dev.laveenh.jazzanalyzer.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static dev.laveenh.jazzanalyzer.domain.ChordQuality.*;
import static org.assertj.core.api.Assertions.assertThat;

class ProgressionPatternTest {

    @ParameterizedTest
    @CsvSource({"ii-V-I,3", "ii-V-i,3", "ii-V,2", "V-I,2", "I-vi-ii-V,4", "iii-vi-ii-V,4", "ii-bII7-I,3"})
    void everyPatternIsFoundByItsName(String name, int steps) {
        assertThat(ProgressionPattern.parse(name)).get().satisfies(p -> {
            assertThat(p.text()).isEqualTo(name);
            assertThat(p.steps()).hasSize(steps);
        });
    }

    @Test
    void theNameIsCaseSensitiveBecauseCaseMeansMajorOrMinor() {
        assertThat(ProgressionPattern.parse("ii-v-i")).isEmpty();
        assertThat(ProgressionPattern.parse("II-V-I")).isEmpty();
        assertThat(ProgressionPattern.parse("ii-V-I")).get().isEqualTo(ProgressionPattern.II_V_I);
        assertThat(ProgressionPattern.parse("ii-V-i")).get().isEqualTo(ProgressionPattern.II_V_MINOR_I);
        assertThat(ProgressionPattern.parse("nonsense")).isEmpty();
        assertThat(ProgressionPattern.parse(null)).isEmpty();
    }

    @Test
    void iiViIsTwoSevenThenTheTonic() {
        var steps = ProgressionPattern.II_V_I.steps();
        assertThat(steps).extracting(ProgressionPattern.Step::semitonesAboveTonic).containsExactly(2, 7, 0);
        assertThat(steps.get(0).qualities()).contains(MINOR7).doesNotContain(DOMINANT7, MAJOR7);
        assertThat(steps.get(1).qualities()).contains(DOMINANT7).doesNotContain(MINOR7);
        assertThat(steps.get(2).qualities()).contains(MAJOR7).doesNotContain(MINOR7);
    }

    @Test
    void theMinorVersionTakesAHalfDiminishedTwoAndAMinorOne() {
        var steps = ProgressionPattern.II_V_MINOR_I.steps();
        assertThat(steps.get(0).qualities()).contains(HALF_DIMINISHED7);
        assertThat(steps.get(2).qualities()).contains(MINOR7, MINOR).doesNotContain(MAJOR7);
    }

    @Test
    void supportedNamesListsThemAll() {
        assertThat(ProgressionPattern.supportedNames()).containsExactly(
                "ii-V-I", "ii-V-i", "ii-V", "V-I", "I-vi-ii-V", "iii-vi-ii-V", "ii-bII7-I");
    }
}
