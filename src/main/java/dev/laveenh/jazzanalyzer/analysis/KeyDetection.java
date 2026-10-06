package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Key;
import dev.laveenh.jazzanalyzer.domain.KeyRegion;

import java.util.List;

/**
 * Result of key detection: the key regions of the tune, plus the local key of every chord
 * (same length and order as the chart's chord list) for the later analysis steps.
 */
public record KeyDetection(List<KeyRegion> regions, List<Key> chordKeys) {
}
