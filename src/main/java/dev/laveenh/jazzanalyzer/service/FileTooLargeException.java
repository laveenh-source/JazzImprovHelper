package dev.laveenh.jazzanalyzer.service;

/** The upload is bigger than the limit. Maps to HTTP 413. */
public class FileTooLargeException extends RuntimeException {

    public FileTooLargeException(String message) {
        super(message);
    }
}
