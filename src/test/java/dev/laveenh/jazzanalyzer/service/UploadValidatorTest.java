package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.config.JazzProperties;
import dev.laveenh.jazzanalyzer.parser.MusicXmlParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadValidatorTest {

    private static final long MAX = 1024;
    private static final String XML = "<score-partwise/>";

    private final UploadValidator validator = new UploadValidator(
            new JazzProperties(new JazzProperties.Upload(MAX), new JazzProperties.Storage(Path.of("unused"))),
            new MusicXmlParser());

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/xml", content.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @CsvSource({"tune.xml", "tune.musicxml", "TUNE.XML", "My Tune.MusicXML", "a.b.xml"})
    void acceptsTheRightExtensions(String name) {
        assertThat(validator.validate(file(name, XML))).isEqualTo(XML.getBytes(StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @CsvSource({"tune.pdf", "tune.txt", "tune.xml.exe", "tune", "tune.xmll", "xml"})
    void rejectsOtherExtensions(String name) {
        assertThatThrownBy(() -> validator.validate(file(name, XML)))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining(".xml and .musicxml");
    }

    @Test
    void explainsThatCompressedMusicXmlIsNotSupportedYet() {
        assertThatThrownBy(() -> validator.validate(file("tune.mxl", XML)))
                .isInstanceOf(UnsupportedFileTypeException.class)
                .hasMessageContaining(".mxl");
    }

    @Test
    void rejectsEmptyAndMissingFiles() {
        assertThatThrownBy(() -> validator.validate(file("tune.xml", "")))
                .isInstanceOf(InvalidUploadException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> validator.validate(null)).isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void rejectsFilesOverTheLimit() {
        String big = "<a>" + "x".repeat((int) MAX) + "</a>";
        assertThatThrownBy(() -> validator.validate(file("tune.xml", big)))
                .isInstanceOf(FileTooLargeException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void acceptsAFileExactlyAtTheLimit() {
        String exact = "<a>" + "x".repeat((int) MAX - "<a></a>".length()) + "</a>";
        assertThat(exact.length()).isEqualTo((int) MAX);
        assertThat(validator.validate(file("tune.xml", exact))).hasSize((int) MAX);
    }

    @Test
    void rejectsXmlThatIsNotWellFormed() {
        assertThatThrownBy(() -> validator.validate(file("tune.xml", "<score-partwise><oops>")))
                .isInstanceOf(InvalidUploadException.class)
                .hasMessageContaining("not well-formed");
        assertThatThrownBy(() -> validator.validate(file("tune.xml", "just some text")))
                .isInstanceOf(InvalidUploadException.class);
    }

    @Test
    void wellFormedXmlWithoutChordsPassesHere() {
        // musical problems are reported later, by the analysis, as a FAILED status with a message
        assertThat(validator.validate(file("tune.xml", "<score-timewise/>"))).isNotEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', value = {
            "tune.xml;tune.xml",
            "../../etc/passwd.xml;passwd.xml",
            "C:\\Users\\me\\tune.xml;tune.xml",
            "/var/tmp/tune.xml;tune.xml",
            "  spaced.xml  ;spaced.xml"})
    void displayNamesLoseTheirDirectoryPart(String original, String expected) {
        assertThat(validator.displayName(original)).isEqualTo(expected);
    }

    @Test
    void displayNamesLoseControlCharactersAndAreCapped() {
        assertThat(validator.displayName("bad\u0000na\nme.xml")).isEqualTo("badname.xml");
        assertThat(validator.displayName("x".repeat(500) + ".xml")).hasSize(200);
    }

    @Test
    void aNameThatIsNothingButADirectoryIsRejected() {
        assertThatThrownBy(() -> validator.displayName("folder/")).isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> validator.displayName("   ")).isInstanceOf(InvalidUploadException.class);
        assertThatThrownBy(() -> validator.displayName(null)).isInstanceOf(InvalidUploadException.class);
    }
}
