package dev.laveenh.jazzanalyzer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "key_regions")
public class KeyRegionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "position_index", nullable = false)
    private int positionIndex;

    @Column(name = "key_name", nullable = false)
    private String keyName;

    @Column(name = "start_measure", nullable = false)
    private int startMeasure;

    @Column(name = "end_measure", nullable = false)
    private int endMeasure;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false)
    private String reason;

    protected KeyRegionEntity() {
        // required by JPA
    }

    public KeyRegionEntity(UUID analysisId, int positionIndex, String keyName, int startMeasure, int endMeasure,
                           double confidence, String reason) {
        this.analysisId = analysisId;
        this.positionIndex = positionIndex;
        this.keyName = keyName;
        this.startMeasure = startMeasure;
        this.endMeasure = endMeasure;
        this.confidence = confidence;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public UUID getAnalysisId() {
        return analysisId;
    }

    public int getPositionIndex() {
        return positionIndex;
    }

    public String getKeyName() {
        return keyName;
    }

    public int getStartMeasure() {
        return startMeasure;
    }

    public int getEndMeasure() {
        return endMeasure;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getReason() {
        return reason;
    }
}
