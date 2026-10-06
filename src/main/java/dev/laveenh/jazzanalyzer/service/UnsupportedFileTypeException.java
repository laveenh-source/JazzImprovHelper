package dev.laveenh.jazzanalyzer.service;

/** The file extension is not one we accept. Maps to HTTP 415. */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String message) {
        super(message);
    }
}
