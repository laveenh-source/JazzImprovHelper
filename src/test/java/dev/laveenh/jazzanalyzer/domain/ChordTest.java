package dev.laveenh.jazzanalyzer.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static dev.laveenh.jazzanalyzer.domain.ChordQuality.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChordTest {

    @ParameterizedTest
    @CsvSource({"C,MAJOR,C", "D,MINOR7,Dm7", "G,DOMINANT7,G7", "C,MAJOR7,Cmaj7", "B,HALF_DIMINISHED7,Bm7b5",
            "C,DIMINISHED7,Cdim7", "C,MINOR_MAJOR7,CmMaj7", "Bb,MAJOR6,Bb6", "F#,AUGMENTED,F#aug",
            "C,MAJOR7_SHARP5,Cmaj7#5", "Eb,DOMINANT7_SUS4,Eb7sus4"})
    void symbolFollowsLeadSheetConvention(String root, ChordQuality quality, String expected) {
        assertThat(Chord.of(Note.parse(root), quality).symbol()).isEqualTo(expected);
    }

    @Test
    void extensionsReplaceTheSeventh() {
        Note c = Note.parse("C");
        assertThat(new Chord(c, DOMINANT7, 9, Set.of(), null, false).symbol()).isEqualTo("C9");
        assertThat(new Chord(c, DOMINANT7, 13, Set.of(), null, false).symbol()).isEqualTo("C13");
        assertThat(new Chord(c, MINOR7, 11, Set.of(), null, false).symbol()).isEqualTo("Cm11");
        assertThat(new Chord(c, MAJOR7, 9, Set.of(), null, false).symbol()).isEqualTo("Cmaj9");
        assertThat(new Chord(c, MAJOR6, 9, Set.of(), null, false).symbol()).isEqualTo("C6/9");
        assertThat(new Chord(c, MAJOR, 9, Set.of(), null, false).symbol()).isEqualTo("Cadd9");
    }

    @Test
    void alterationsAreListedInOrderAndAltOverridesThem() {
        Note g = Note.parse("G");
        Set<Alteration> alterations = Set.of(new Alteration(11, 1), new Alteration(9, -1));
        assertThat(new Chord(g, DOMINANT7, 0, alterations, null, false).symbol()).isEqualTo("G7b9#11");
        assertThat(new Chord(g, DOMINANT7, 0, alterations, null, true).symbol()).isEqualTo("G7alt");
    }

    @Test
    void slashChordShowsBassButBassEqualToRootIsDropped() {
        Note c = Note.parse("C");
        assertThat(new Chord(c, MAJOR, 0, Set.of(), Note.parse("E"), false).symbol()).isEqualTo("C/E");
        assertThat(new Chord(c, MAJOR, 0, Set.of(), c, false).bass()).isNull();
    }

    @Test
    void rejectsInvalidArguments() {
        Note c = Note.parse("C");
        assertThatThrownBy(() -> new Chord(null, MAJOR, 0, Set.of(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chord(c, null, 0, Set.of(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Chord(c, DOMINANT7, 10, Set.of(), null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
