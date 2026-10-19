package dev.laveenh.jazzanalyzer.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class KeyTest {

    @ParameterizedTest(name = "{0} fifths, {1} -> {2}")
    @CsvSource({
            "0,MAJOR,C major", "1,MAJOR,G major", "2,MAJOR,D major", "5,MAJOR,B major", "6,MAJOR,F# major",
            "-1,MAJOR,F major", "-2,MAJOR,Bb major", "-3,MAJOR,Eb major", "-6,MAJOR,Gb major", "-7,MAJOR,Cb major",
            "0,MINOR,A minor", "1,MINOR,E minor", "-1,MINOR,D minor", "-2,MINOR,G minor", "-3,MINOR,C minor",
            "3,MINOR,F# minor", "-5,MINOR,Bb minor"})
    void fromFifthsSpellsTheTonicCorrectly(int fifths, Mode mode, String expected) {
        assertThat(Key.fromFifths(fifths, mode)).hasToString(expected);
    }

    @ParameterizedTest(name = "{0} fifths, mode {1} -> {2}")
    @CsvSource({
            "0,dorian,D minor", "0,phrygian,E minor", "0,lydian,F major", "0,mixolydian,G major",
            "0,aeolian,A minor", "0,locrian,B minor", "-1,mixolydian,C major", "-2,dorian,C minor",
            "0,major,C major", "0,ionian,C major", "0,minor,A minor", "-2,MAJOR,Bb major"})
    void fromSignatureReadsChurchModesByTheirTonalCenter(int fifths, String mode, String expected) {
        assertThat(Key.fromSignature(fifths, mode)).hasToString(expected);
    }

    @org.junit.jupiter.api.Test
    void missingOrUnknownModeMeansMajor() {
        assertThat(Key.fromSignature(-3, null)).hasToString("Eb major");
        assertThat(Key.fromSignature(-3, "whatever")).hasToString("Eb major");
    }
}
