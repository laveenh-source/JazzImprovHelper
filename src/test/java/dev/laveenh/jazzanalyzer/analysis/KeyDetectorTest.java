package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Chart;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.KeyRegion;
import dev.laveenh.jazzanalyzer.domain.Mode;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.parser.MusicXmlParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static dev.laveenh.jazzanalyzer.TestCharts.chart;
import static org.assertj.core.api.Assertions.assertThat;

class KeyDetectorTest {

    private static final Key C_MAJOR = new Key(Note.parse("C"), Mode.MAJOR);
    private static final Key A_MINOR = new Key(Note.parse("A"), Mode.MINOR);
    private static final Key BB_MAJOR = new Key(Note.parse("Bb"), Mode.MAJOR);

    private final KeyDetector detector = new KeyDetector();

    private List<KeyRegion> regions(Key signature, String progression) {
        return detector.detect(chart(signature, progression)).regions();
    }

    @Test
    void majorIiVIGivesOneRegionWithTheCadenceAsReason() {
        List<KeyRegion> regions = regions(C_MAJOR, "Dm7 | G7 | Cmaj7");

        assertThat(regions).hasSize(1);
        KeyRegion region = regions.get(0);
        assertThat(region.key()).hasToString("C major");
        assertThat(region.startMeasure()).isEqualTo(1);
        assertThat(region.endMeasure()).isEqualTo(3);
        assertThat(region.reason()).isEqualTo("ii-V-I in measures 1-3");
        assertThat(region.confidence()).isBetween(0.9, 1.0);
    }

    @Test
    void minorIiVIFindsTheMinorKey() {
        List<KeyRegion> regions = regions(A_MINOR, "Bm7b5 | E7alt | Am7");

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).key()).hasToString("A minor");
        assertThat(regions.get(0).reason()).isEqualTo("ii-V-i in measures 1-3");
    }

    @Test
    void worksWithoutAKeySignature() {
        assertThat(regions(null, "Dm7 | G7 | Cmaj7").get(0).key()).hasToString("C major");
        assertThat(regions(null, "Bm7b5 | E7alt | Am7").get(0).key()).hasToString("A minor");
    }

    @Test
    void tritoneSubstitutionIiVIsRecognised() {
        KeyRegion region = regions(C_MAJOR, "Dm7 | Db7 | Cmaj7").get(0);
        assertThat(region.key()).hasToString("C major");
        assertThat(region.reason()).isEqualTo("ii-bII7-I in measures 1-3");
    }

    @Test
    void repeatedChordsDoNotBreakAPattern() {
        KeyRegion region = regions(C_MAJOR, "Dm7 | Dm7 | G7 | G7 | Cmaj7 | Cmaj7").get(0);
        assertThat(region.reason()).isEqualTo("ii-V-I in measures 1-5");
        assertThat(region.endMeasure()).isEqualTo(6);
    }

    @Test
    void aSecondaryDominantInsideTheKeyDoesNotChangeTheKey() {
        // A7 is V7/ii in C. The key signature and the diatonic chords keep this one region.
        List<KeyRegion> regions = regions(C_MAJOR, "Dm7 | A7 | Dm7");
        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).key()).hasToString("C major");
    }

    @Test
    void aModulatingBridgeProducesSeveralRegions() {
        List<KeyRegion> regions = regions(C_MAJOR,
                "Dm7 | G7 | Cmaj7 | Cmaj7 | Fm7 | Bb7 | Ebmaj7 | Ebmaj7");

        assertThat(regions).extracting(r -> r.key().toString()).containsExactly("C major", "Eb major");
        assertThat(regions).extracting(KeyRegion::startMeasure).containsExactly(1, 5);
        assertThat(regions).extracting(KeyRegion::endMeasure).containsExactly(4, 8);
        assertThat(regions.get(1).reason()).isEqualTo("ii-V-I in measures 5-7");
    }

    @Test
    void majorKeyThenItsRelativeMinorAreSeparateRegions() {
        List<KeyRegion> regions = regions(BB_MAJOR, "Cm7 | F7 | Bbmaj7 | Ebmaj7 | Am7b5 | D7 | Gm7 | Gm7");

        assertThat(regions).extracting(r -> r.key().toString()).containsExactly("Bb major", "G minor");
        assertThat(regions).extracting(KeyRegion::startMeasure).containsExactly(1, 5);
        assertThat(regions.get(0).reason()).isEqualTo("ii-V-I in measures 1-3");
        assertThat(regions.get(1).reason()).isEqualTo("ii-V-i in measures 5-7");
    }

    @Test
    void aTuneThatOpensOnTheFourthChordIsStillInItsTonic() {
        // Opens on IV (Fmaj7) like many standards; the signature and the resolutions say C major.
        List<KeyRegion> regions = regions(C_MAJOR,
                "Fmaj7 | Em7 A7 | Dm7 | G7 | Cmaj7 | Am7 | Dm7 G7 | Cmaj7");

        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).key()).hasToString("C major");
    }

    @Test
    void withoutASignatureTheResolvingChordBeatsTheOpeningChord() {
        List<KeyRegion> regions = regions(null, "Fmaj7 | Fmaj7 | Dm7 | G7 | Cmaj7 | Cmaj7");
        assertThat(regions.get(0).key()).hasToString("C major");
    }

    @Test
    void withoutCadencesItFallsBackToDiatonicFit() {
        List<KeyRegion> regions = regions(null, "Cmaj7 | Fmaj7 | Cmaj7 | Fmaj7 | Cmaj7");
        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).key()).hasToString("C major");
        assertThat(regions.get(0).reason()).startsWith("No ii-V-I found; best diatonic fit (5 of 5 chords)");
    }

    @Test
    void fallbackReasonMentionsAMatchingKeySignature() {
        KeyRegion region = regions(C_MAJOR, "Cmaj7 | Fmaj7 | Cmaj7").get(0);
        assertThat(region.reason()).endsWith("matches the key signature");
    }

    @Test
    void theKeyIsSpelledTheWayTheChartSpellsItsTonic() {
        assertThat(regions(null, "Gbmaj7 | Gbmaj7").get(0).key()).hasToString("Gb major");
        assertThat(regions(null, "F#maj7 | F#maj7").get(0).key()).hasToString("F# major");
    }

    @Test
    void unknownSpellingFallsBackToTheKeySignatureThenADefault() {
        // No tonic chord in the region: spelling comes from the signature, else a default name.
        Key eFlat = new Key(Note.parse("Eb"), Mode.MAJOR);
        assertThat(regions(eFlat, "Fm7 | Bb7").get(0).key()).hasToString("Eb major");
        // G7 alone fits C major and A minor equally; with no tonic chord the default name is used.
        assertThat(regions(null, "G7 | G7").get(0).key()).hasToString("C major");
    }

    @Test
    void aSingleChordChartHasOneRegion() {
        List<KeyRegion> regions = regions(null, "Cmaj7");
        assertThat(regions).hasSize(1);
        assertThat(regions.get(0).startMeasure()).isEqualTo(1);
    }

    @Test
    void everyChordGetsTheKeyOfItsRegion() {
        KeyDetection detection = detector.detect(chart(C_MAJOR,
                "Dm7 | G7 | Cmaj7 | Cmaj7 | Fm7 | Bb7 | Ebmaj7 | Ebmaj7"));
        assertThat(detection.chordKeys()).extracting(Key::toString)
                .containsExactly("C major", "C major", "C major", "C major",
                        "Eb major", "Eb major", "Eb major", "Eb major");
    }

    @Test
    void aMidMeasureKeyChangeEndsTheOldRegionInThatMeasure() {
        List<KeyRegion> regions = regions(C_MAJOR, "Dm7 | G7 | Cmaj7 | Cmaj7 Fm7 | Bb7 | Ebmaj7 | Ebmaj7 | Ebmaj7");
        assertThat(regions).hasSizeGreaterThanOrEqualTo(2);
        for (int i = 1; i < regions.size(); i++) {
            assertThat(regions.get(i).startMeasure()).isGreaterThanOrEqualTo(regions.get(i - 1).endMeasure());
        }
    }

    @Test
    void readsKeyRegionsFromAParsedModulatingFixture() throws IOException {
        Chart parsed;
        try (InputStream in = getClass().getResourceAsStream("/fixtures/modulation.xml")) {
            parsed = new MusicXmlParser().parse(in);
        }
        List<KeyRegion> regions = detector.detect(parsed).regions();
        assertThat(regions).extracting(r -> r.key().toString()).containsExactly("C major", "Eb major");
    }

    @Test
    void patternsFinderReportsNamesAndChordRanges() {
        var patterns = detector.findPatterns(chart(null, "Dm7 | G7 | Cmaj7").chords());
        assertThat(patterns).hasSize(1);
        assertThat(patterns.get(0).name()).isEqualTo("ii-V-I");
        assertThat(patterns.get(0).startChord()).isZero();
        assertThat(patterns.get(0).endChord()).isEqualTo(2);
    }

    @Test
    void notEveryMinorDominantMajorTripletIsAnIiVI() {
        assertThat(detector.findPatterns(chart(null, "Dm7 | G7 | Dbmaj7").chords())).isEmpty();
        assertThat(detector.findPatterns(chart(null, "Dm7 | A7 | Gmaj7").chords())).isEmpty();
        assertThat(detector.findPatterns(chart(null, "Cmaj7 | G7 | Cmaj7").chords())).isEmpty();
    }
}
