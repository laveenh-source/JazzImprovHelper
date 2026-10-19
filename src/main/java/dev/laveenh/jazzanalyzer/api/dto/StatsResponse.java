package dev.laveenh.jazzanalyzer.api.dto;

import dev.laveenh.jazzanalyzer.persistence.StatsRepository;

import java.util.List;

public record StatsResponse(List<StatsRepository.QualityCount> chordQualities,
                            List<StatsRepository.TransitionCount> commonTransitions) {
}
