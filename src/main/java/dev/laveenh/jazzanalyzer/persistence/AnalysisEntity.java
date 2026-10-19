package dev.laveenh.jazzanalyzer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One run of the analyzer over a song. A song can have several (for example a retry after a failure). */
@Entity
@Table(name = "analyses")
public class AnalysisEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "song_id", nullable = false)
    private UUID songId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;

    @Column(name = "detected_key")
    private String detectedKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected AnalysisEntity() {
        // required by JPA
    }

    public static AnalysisEntity pending(UUID songId, Instant now) {
        AnalysisEntity a = new AnalysisEntity();
        a.songId = songId;
        a.status = AnalysisStatus.PENDING;
        a.createdAt = now;
        return a;
    }

    public void markRunning() {
        this.status = AnalysisStatus.RUNNING;
    }

    public void markComplete(String detectedKey, Instant now) {
        this.status = AnalysisStatus.COMPLETE;
        this.detectedKey = detectedKey;
        this.completedAt = now;
        this.errorMessage = null;
    }

    public void markFailed(String message, Instant now) {
        this.status = AnalysisStatus.FAILED;
        this.errorMessage = message;
        this.completedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getSongId() {
        return songId;
    }

    public AnalysisStatus getStatus() {
        return status;
    }

    public String getDetectedKey() {
        return detectedKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
