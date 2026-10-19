package dev.laveenh.jazzanalyzer.analysis;

import dev.laveenh.jazzanalyzer.domain.Chord;
import dev.laveenh.jazzanalyzer.domain.ChordFunction;
import dev.laveenh.jazzanalyzer.domain.Key;

/** Everything a scale rule can look at: the chord plus its analysed role. */
record ChordContext(Chord chord, Key key, String degree, ChordFunction function, Resolution resolution) {
}
