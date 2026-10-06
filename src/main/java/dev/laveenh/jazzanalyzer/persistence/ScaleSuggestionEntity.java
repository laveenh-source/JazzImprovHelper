package dev.laveenh.jazzanalyzer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "scale_suggestions")
public class ScaleSuggestionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chord_id", nullable = false)
    private Long chordId;

    @Column(nullable = false)
    private short rank;

    @Column(name = "scale_name", nullable = false)
    private String scaleName;

    @Column(name = "scale_notes", nullable = false)
    private String scaleNotes;

    @Column(nullable = false)
    private String reason;

    protected ScaleSuggestionEntity() {
        // required by JPA
    }

    public ScaleSuggestionEntity(Long chordId, int rank, String scaleName, String scaleNotes, String reason) {
        this.chordId = chordId;
        this.rank = (short) rank;
        this.scaleName = scaleName;
        this.scaleNotes = scaleNotes;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Long getChordId() {
        return chordId;
    }

    public int getRank() {
        return rank;
    }

    public String getScaleName() {
        return scaleName;
    }

    public String getScaleNotes() {
        return scaleNotes;
    }

    public String getReason() {
        return reason;
    }
}
