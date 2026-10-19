package dev.laveenh.jazzanalyzer.service;

/** The uploaded file is not acceptable (empty, no name, not well-formed XML...). Maps to HTTP 400. */
public class InvalidUploadException extends RuntimeException {

    public InvalidUploadException(String message) {
        super(message);
    }
}
