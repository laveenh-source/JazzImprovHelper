package dev.laveenh.jazzanalyzer.service;

/** The results asked for do not exist (yet): the latest analysis is pending, running or failed. Maps to HTTP 409. */
public class AnalysisNotReadyException extends RuntimeException {

    public AnalysisNotReadyException(String message) {
        super(message);
    }
}
