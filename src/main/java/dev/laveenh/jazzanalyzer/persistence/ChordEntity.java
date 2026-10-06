package dev.laveenh.jazzanalyzer.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "chords")
public class ChordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "position_index", nullable = false)
    private int positionIndex;

    @Column(nullable = false)
    private int measure;

    @Column(nullable = false)
    private double beat;

    @Column(nullable = false)
    private String symbol;

    @Column(name = "root_pc", nullable = false)
    private short rootPc;

    @Column(nullable = false)
    private String quality;

    @Column(name = "roman_numeral", nullable = false)
    private String romanNumeral;

    @Column(name = "chord_function", nullable = false)
    private String chordFunction;

    @Column(name = "function_reason", nullable = false)
    private String functionReason;

    @Column(name = "local_key", nullable = false)
    private String localKey;

    protected ChordEntity() {
        // required by JPA
    }

    public ChordEntity(UUID analysisId, int positionIndex, int measure, double beat, String symbol, int rootPc,
                       String quality, String romanNumeral, String chordFunction, String functionReason,
                       String localKey) {
        this.analysisId = analysisId;
        this.positionIndex = positionIndex;
        this.measure = measure;
        this.beat = beat;
        this.symbol = symbol;
        this.rootPc = (short) rootPc;
        this.quality = quality;
        this.romanNumeral = romanNumeral;
        this.chordFunction = chordFunction;
        this.functionReason = functionReason;
        this.localKey = localKey;
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

    public int getMeasure() {
        return measure;
    }

    public double getBeat() {
        return beat;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getRootPc() {
        return rootPc;
    }

    public String getQuality() {
        return quality;
    }

    public String getRomanNumeral() {
        return romanNumeral;
    }

    public String getChordFunction() {
        return chordFunction;
    }

    public String getFunctionReason() {
        return functionReason;
    }

    public String getLocalKey() {
        return localKey;
    }
}
