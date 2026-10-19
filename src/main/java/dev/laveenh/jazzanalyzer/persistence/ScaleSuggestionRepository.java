package dev.laveenh.jazzanalyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ScaleSuggestionRepository extends JpaRepository<ScaleSuggestionEntity, Long> {

    /** One query for all the chords of an analysis, instead of one query per chord. */
    List<ScaleSuggestionEntity> findByChordIdInOrderByChordIdAscRankAsc(Collection<Long> chordIds);
}
