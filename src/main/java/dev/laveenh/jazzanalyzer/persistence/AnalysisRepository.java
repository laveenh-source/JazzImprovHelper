package dev.laveenh.jazzanalyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<AnalysisEntity, UUID> {

    Optional<AnalysisEntity> findFirstBySongIdOrderByCreatedAtDesc(UUID songId);
}
