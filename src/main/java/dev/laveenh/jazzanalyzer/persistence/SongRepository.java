package dev.laveenh.jazzanalyzer.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SongRepository extends JpaRepository<SongEntity, UUID> {

    Optional<SongEntity> findByChecksumSha256(String checksumSha256);
}
