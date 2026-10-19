package dev.laveenh.jazzanalyzer.api.dto;

import dev.laveenh.jazzanalyzer.service.AnalysisDetails;

import java.util.List;
import java.util.UUID;

/** The per-chord detail of a song's latest completed analysis. */
public record SongChordsResponse(UUID songId, UUID analysisId, String title, String detectedKey, List<ChordDto> chords) {

    public static SongChordsResponse from(AnalysisDetails d) {
        return new SongChordsResponse(d.song().getId(), d.analysis().getId(), d.song().getTitle(),
                d.analysis().getDetectedKey(),
                d.chords().stream().map(c -> ChordDto.from(c, d.scalesByChordId())).toList());
    }
}
