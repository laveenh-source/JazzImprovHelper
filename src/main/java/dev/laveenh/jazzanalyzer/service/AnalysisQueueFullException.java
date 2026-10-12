package dev.laveenh.jazzanalyzer.service;

/** Every worker is busy and the waiting queue is full. Maps to HTTP 503 with a Retry-After hint. */
public class AnalysisQueueFullException extends RuntimeException {

    public AnalysisQueueFullException() {
        super("The server is busy analysing other charts. Please upload the file again in a few seconds.");
    }
}
