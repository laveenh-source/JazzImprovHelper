package dev.laveenh.jazzanalyzer.api;

/** A request parameter has a bad value (page size out of range, unknown sort field...). Maps to HTTP 400. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
