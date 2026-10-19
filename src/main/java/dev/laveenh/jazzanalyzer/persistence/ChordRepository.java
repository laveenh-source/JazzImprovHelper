package dev.laveenh.jazzanalyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChordRepository extends JpaRepository<ChordEntity, Long> {

    List<ChordEntity> findByAnalysisIdOrderByPositionIndex(UUID analysisId);
}
