package dev.laveenh.jazzanalyzer.parser;

import dev.laveenh.jazzanalyzer.analysis.ChordClassifier;
import dev.laveenh.jazzanalyzer.domain.Chart;
import dev.laveenh.jazzanalyzer.domain.ChordSpec;
import dev.laveenh.jazzanalyzer.domain.ChordSpec.Degree;
import dev.laveenh.jazzanalyzer.domain.ChordSpec.DegreeType;
import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.Note;
import dev.laveenh.jazzanalyzer.domain.PlacedChord;
import dev.laveenh.jazzanalyzer.domain.TimeSignature;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a partwise MusicXML lead sheet into a {@link Chart}.
 *
 * <p>We only extract what the analysis needs: title, key/time signature and the chord symbols
 * with their position. Notes are read solely to know how far time has advanced inside a measure.
 */
public final class MusicXmlParser {

    private static final String UNTITLED = "Untitled";

    private final ChordClassifier classifier;

    public MusicXmlParser() {
        this(new ChordClassifier());
    }

    public MusicXmlParser(ChordClassifier classifier) {
        this.classifier = classifier;
    }

    public Chart parse(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return parse(in);
        }
    }

    public Chart parse(InputStream in) {
        return toChart(readDocument(in));
    }

    // ---------------------------------------------------------------- XML loading (secure)

    /**
     * Builds an XML parser that cannot be tricked into reading local files or calling out to the
     * network (XXE). Real MusicXML files often carry a harmless DOCTYPE line, so we do not reject
     * DOCTYPEs; we just refuse to load anything external.
     */
    private static DocumentBuilder secureBuilder() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new ThrowingErrorHandler());
            return builder;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser could not be configured securely", e);
        }
    }

    private static Document readDocument(InputStream in) {
        try {
            return secureBuilder().parse(in);
        } catch (SAXException e) {
            throw new ChartParseException("The file is not well-formed XML: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ChartParseException("The file could not be read: " + e.getMessage(), e);
        }
    }

    /** The default handler prints to stderr and keeps going; we want any error to stop the parse. */
    private static final class ThrowingErrorHandler implements ErrorHandler {
        @Override
        public void warning(SAXParseException e) {
            // warnings are harmless
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    }

    // ---------------------------------------------------------------- MusicXML -> Chart

    private Chart toChart(Document doc) {
        Element root = doc.getDocumentElement();
        if (!"score-partwise".equals(root.getTagName())) {
            throw new ChartParseException("Unsupported MusicXML format <" + root.getTagName()
                    + ">. Only partwise scores (<score-partwise>) are supported.");
        }

        Element part = firstPartWithChords(root);
        if (part == null) {
            throw new NoChordSymbolsException();
        }

        PartReader reader = new PartReader();
        reader.read(part);
        if (reader.chords.isEmpty()) {
            throw new NoChordSymbolsException(); // only N.C. symbols
        }
        return new Chart(title(root), reader.keySignature, reader.timeSignature, reader.chords, reader.measureCount, reader.lastMeasure);
    }

    private static String title(Element root) {
        Element work = child(root, "work");
        String title = work == null ? null : childText(work, "work-title");
        if (title == null || title.isBlank()) {
            title = childText(root, "movement-title");
        }
        return title == null || title.isBlank() ? UNTITLED : title.trim();
    }

    /** Chord symbols usually live in one part; other parts would only repeat them. */
    private static Element firstPartWithChords(Element root) {
        for (Element part : children(root, "part")) {
            if (part.getElementsByTagName("harmony").getLength() > 0) {
                return part;
            }
        }
        return null;
    }

    /** Walks the measures of one part, tracking time so each chord gets a measure and beat. */
    private final class PartReader {
        private final List<PlacedChord> chords = new ArrayList<>();
        private Key keySignature;
        private TimeSignature timeSignature;
        private int measureCount;
        private int lastMeasure;

        private int divisions = 1;      // divisions per quarter note (changes via <attributes>)
        private int beatType = 4;       // denominator of the current time signature

        void read(Element part) {
            int index = 0;
            for (Element measure : children(part, "measure")) {
                index++;
                measureCount++;
                lastMeasure = measureNumber(measure, index);
                readMeasure(measure, lastMeasure);
            }
        }

        private void readMeasure(Element measure, int number) {
            int position = 0; // time since the start of the measure, in divisions
            for (Element el : childElements(measure)) {
                switch (el.getTagName()) {
                    case "attributes" -> readAttributes(el);
                    case "note" -> position += noteAdvance(el);
                    case "forward" -> position += intOr(childText(el, "duration"), 0);
                    case "backup" -> position -= intOr(childText(el, "duration"), 0);
                    case "harmony" -> addChord(el, number, position);
                    default -> { }
                }
            }
        }

        private void readAttributes(Element attributes) {
            Integer newDivisions = parseInt(childText(attributes, "divisions"));
            if (newDivisions != null && newDivisions > 0) {
                divisions = newDivisions;
            }
            Element key = child(attributes, "key");
            if (key != null && keySignature == null) {
                keySignature = readKey(key);
            }
            Element time = child(attributes, "time");
            if (time != null) {
                Integer beats = parseInt(childText(time, "beats"));
                Integer type = parseInt(childText(time, "beat-type"));
                if (beats != null && type != null && type > 0) {
                    beatType = type;
                    if (timeSignature == null) {
                        timeSignature = new TimeSignature(beats, type);
                    }
                }
            }
        }

        /** Notes in a chord (stacked on the previous note) and grace notes take no time. */
        private int noteAdvance(Element note) {
            if (child(note, "chord") != null || child(note, "grace") != null) {
                return 0;
            }
            return intOr(childText(note, "duration"), 0);
        }

        private void addChord(Element harmony, int measureNumber, int position) {
            ChordSpec spec = readHarmony(harmony, measureNumber);
            if ("none".equals(spec.kind())) {
                return; // N.C. = "no chord"
            }
            int offset = intOr(childText(harmony, "offset"), 0);
            double beat = 1.0 + (double) (position + offset) / divisions * beatType / 4.0;
            chords.add(new PlacedChord(measureNumber, beat, classifier.classify(spec)));
        }
    }

    private static Key readKey(Element key) {
        Integer fifths = parseInt(childText(key, "fifths"));
        if (fifths == null) {
            return null;
        }
        return Key.fromSignature(fifths, childText(key, "mode"));
    }

    private static ChordSpec readHarmony(Element harmony, int measureNumber) {
        Element rootEl = child(harmony, "root");
        if (rootEl == null) {
            throw new ChartParseException("Chord symbol in measure " + measureNumber + " has no <root>");
        }
        Note root = readNote(rootEl, "root-step", "root-alter", measureNumber);

        Element kindEl = child(harmony, "kind");
        String kind = kindEl == null ? "" : kindEl.getTextContent().trim();
        String kindText = kindEl == null || !kindEl.hasAttribute("text") ? null : kindEl.getAttribute("text");

        Element bassEl = child(harmony, "bass");
        Note bass = bassEl == null ? null : readNote(bassEl, "bass-step", "bass-alter", measureNumber);

        List<Degree> degrees = new ArrayList<>();
        for (Element d : children(harmony, "degree")) {
            Integer value = parseInt(childText(d, "degree-value"));
            if (value == null) {
                throw new ChartParseException("Chord degree in measure " + measureNumber + " has no <degree-value>");
            }
            int alter = intOr(childText(d, "degree-alter"), 0);
            degrees.add(new Degree(value, alter, degreeType(childText(d, "degree-type"))));
        }
        return new ChordSpec(root, kind, kindText, degrees, bass);
    }

    private static DegreeType degreeType(String text) {
        if (text == null) {
            return DegreeType.ADD;
        }
        return switch (text.trim().toLowerCase(Locale.ROOT)) {
            case "alter" -> DegreeType.ALTER;
            case "subtract" -> DegreeType.SUBTRACT;
            default -> DegreeType.ADD;
        };
    }

    private static Note readNote(Element parent, String stepTag, String alterTag, int measureNumber) {
        String step = childText(parent, stepTag);
        if (step == null) {
            throw new ChartParseException("Chord in measure " + measureNumber + " is missing <" + stepTag + ">");
        }
        int alter = (int) Math.round(doubleOr(childText(parent, alterTag), 0));
        try {
            return new Note(Note.Step.valueOf(step.trim().toUpperCase(Locale.ROOT)), alter);
        } catch (IllegalArgumentException e) {
            throw new ChartParseException("Invalid note name '" + step + "' in measure " + measureNumber);
        }
    }

    /** Measure numbers are strings in MusicXML ("X1", "12a"); fall back to the position in the part. */
    private static int measureNumber(Element measure, int index) {
        Integer n = parseInt(measure.getAttribute("number"));
        return n != null ? n : index;
    }

    // ---------------------------------------------------------------- small DOM helpers

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) {
                result.add(e);
            }
        }
        return result;
    }

    private static List<Element> children(Element parent, String tag) {
        return childElements(parent).stream().filter(e -> e.getTagName().equals(tag)).toList();
    }

    private static Element child(Element parent, String tag) {
        for (Element e : childElements(parent)) {
            if (e.getTagName().equals(tag)) {
                return e;
            }
        }
        return null;
    }

    private static String childText(Element parent, String tag) {
        Element e = child(parent, tag);
        return e == null ? null : e.getTextContent().trim();
    }

    private static Integer parseInt(String text) {
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int intOr(String text, int fallback) {
        Integer n = parseInt(text);
        return n != null ? n : fallback;
    }

    private static double doubleOr(String text, double fallback) {
        if (text == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
