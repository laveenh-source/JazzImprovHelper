package dev.laveenh.jazzanalyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KeyRegionRepository extends JpaRepository<KeyRegionEntity, Long> {

    List<KeyRegionEntity> findByAnalysisIdOrderByPositionIndex(UUID analysisId);
}
