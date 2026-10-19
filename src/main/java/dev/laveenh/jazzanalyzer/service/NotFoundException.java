package dev.laveenh.jazzanalyzer.service;

import java.util.UUID;

/** A song or analysis does not exist. Maps to HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, UUID id) {
        super(what + " " + id + " was not found");
    }
}
