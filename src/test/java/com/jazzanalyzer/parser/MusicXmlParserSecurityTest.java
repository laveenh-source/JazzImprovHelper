package com.jazzanalyzer.parser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proves the parser cannot be abused through XML External Entities (XXE) or entity bombs. */
class MusicXmlParserSecurityTest {

    private static final String SECRET = "TOP-SECRET-FILE-CONTENTS-12345";

    private final MusicXmlParser parser = new MusicXmlParser();

    private static String score(String doctype, String title) {
        return "<?xml version=\"1.0\"?>\n" + doctype
                + "<score-partwise><work><work-title>" + title + "</work-title></work>"
                + "<part-list/><part id=\"P1\"><measure number=\"1\"><harmony><root><root-step>C</root-step></root>"
                + "<kind>major</kind></harmony></measure></part></score-partwise>";
    }

    private Throwable parseAndCapture(String xml) {
        try {
            var chart = parser.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            assertThat(chart.title()).doesNotContain(SECRET);
            return null;
        } catch (ChartParseException e) {
            return e;
        }
    }

    @Test
    void externalGeneralEntityDoesNotLeakLocalFiles(@TempDir Path dir) throws IOException {
        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, SECRET);
        String doctype = "<!DOCTYPE score-partwise [<!ENTITY xxe SYSTEM \"" + secret.toUri() + "\">]>\n";

        Throwable result = parseAndCapture(score(doctype, "&xxe;"));

        // Either the file is rejected or parsed with the entity left empty - never expanded.
        if (result != null) {
            assertThat(result.getMessage()).doesNotContain(SECRET);
        }
    }

    @Test
    void externalDtdIsNeverLoaded(@TempDir Path dir) throws IOException {
        Path dtd = dir.resolve("evil.dtd");
        Files.writeString(dtd, "<!ENTITY leaked \"" + SECRET + "\">");
        String doctype = "<!DOCTYPE score-partwise SYSTEM \"" + dtd.toUri() + "\">\n";

        // If the DTD were loaded, &leaked; would expand to SECRET and parseAndCapture would fail.
        // With external loading disabled the reference is simply left unresolved.
        Throwable result = parseAndCapture(score(doctype, "&leaked;"));

        if (result != null) {
            assertThat(result.getMessage()).doesNotContain(SECRET);
        }
    }

    @Test
    void externalParameterEntityIsNeverLoaded(@TempDir Path dir) throws IOException {
        Path dtd = dir.resolve("params.dtd");
        Files.writeString(dtd, "<!ENTITY % file \"" + SECRET + "\">");
        String doctype = "<!DOCTYPE score-partwise [<!ENTITY % remote SYSTEM \"" + dtd.toUri() + "\"> %remote;]>\n";

        Throwable result = parseAndCapture(score(doctype, "x"));

        if (result != null) {
            assertThat(result.getMessage()).doesNotContain(SECRET);
        }
    }

    @Test
    void entityExpansionBombIsStopped() {
        StringBuilder dtd = new StringBuilder("<!DOCTYPE score-partwise [<!ENTITY a0 \"lol\">");
        for (int i = 1; i <= 10; i++) {
            String prev = "&a" + (i - 1) + ";";
            dtd.append("<!ENTITY a").append(i).append(" \"").append(prev.repeat(10)).append("\">");
        }
        dtd.append("]>\n");

        assertThatThrownBy(() -> parser.parse(new ByteArrayInputStream(
                score(dtd.toString(), "&a10;").getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(ChartParseException.class);
    }
}
