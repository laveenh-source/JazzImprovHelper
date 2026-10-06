package dev.laveenh.jazzanalyzer.domain;

/**
 * A stretch of the tune that sits in one key.
 *
 * @param confidence a 0-1 heuristic score (diatonic fit, cadence evidence, tonic presence),
 *                   not a calibrated probability
 * @param reason     one-line explanation, e.g. "ii-V-I in measures 5-7"
 */
public record KeyRegion(Key key, int startMeasure, int endMeasure, double confidence, String reason) {
}
