package dev.laveenh.jazzanalyzer.service;

import dev.laveenh.jazzanalyzer.config.JazzProperties;
import dev.laveenh.jazzanalyzer.parser.ChartParseException;
import dev.laveenh.jazzanalyzer.parser.MusicXmlParser;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/** Checks an upload before anything is stored: present, not too big, right extension, well-formed XML. */
@Component
public class UploadValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".xml", ".musicxml");
    private static final int MAX_DISPLAY_NAME_LENGTH = 200;

    private final long maxBytes;
    private final MusicXmlParser parser;

    public UploadValidator(JazzProperties properties, MusicXmlParser parser) {
        this.maxBytes = properties.upload().maxBytes();
        this.parser = parser;
    }

    /** The validated content of the upload. */
    public byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("No file was uploaded, or the file is empty.");
        }
        if (file.getSize() > maxBytes) {
            throw tooLarge();
        }
        String name = displayName(file.getOriginalFilename());
        checkExtension(name);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new InvalidUploadException("The uploaded file could not be read.");
        }
        if (content.length > maxBytes) {
            throw tooLarge();
        }
        try {
            parser.requireWellFormedXml(new ByteArrayInputStream(content));
        } catch (ChartParseException e) {
            throw new InvalidUploadException(e.getMessage());
        }
        return content;
    }

    /**
     * A safe name to show back to users. It is only ever displayed, never used as a path: stored files get
     * generated keys. Any directory part and control characters are removed.
     */
    public String displayName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidUploadException("The uploaded file has no name.");
        }
        String name = originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").trim();
        if (name.isEmpty()) {
            throw new InvalidUploadException("The uploaded file has no name.");
        }
        return name.length() > MAX_DISPLAY_NAME_LENGTH ? name.substring(0, MAX_DISPLAY_NAME_LENGTH) : name;
    }

    private static void checkExtension(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mxl")) {
            throw new UnsupportedFileTypeException(
                    "Compressed MusicXML (.mxl) is not supported yet. Export an uncompressed .xml or .musicxml file.");
        }
        if (ALLOWED_EXTENSIONS.stream().noneMatch(lower::endsWith)) {
            throw new UnsupportedFileTypeException("Only .xml and .musicxml files are accepted.");
        }
    }

    private FileTooLargeException tooLarge() {
        return new FileTooLargeException("The file is larger than the " + (maxBytes / 1024 / 1024) + " MB limit.");
    }
}
