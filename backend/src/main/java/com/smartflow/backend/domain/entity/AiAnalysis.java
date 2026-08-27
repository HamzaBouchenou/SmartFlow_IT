package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.AiAnalysisType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Result of one AI service call, kept as an aid the user must validate or correct (RG-10 -
 * "Aucune écriture directe de l'IA sur une demande"). validatedBy/validatedAt/acceptedValue
 * stay null until a human acts; the AI never writes suggestedValue into Request or
 * RequestFieldValue directly.
 */
@Entity
@Table(name = "ai_analyses")
public class AiAnalysis extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Enumerated(EnumType.STRING)
    @Column(name = "analysis_type", nullable = false, length = 32)
    private AiAnalysisType analysisType;

    @Column(name = "raw_result", columnDefinition = "text")
    private String rawResult;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "suggested_value", columnDefinition = "text")
    private String suggestedValue;

    @Column(name = "accepted_value", columnDefinition = "text")
    private String acceptedValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validated_by_id")
    private User validatedBy;

    @Column(name = "validated_at")
    private Instant validatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    protected AiAnalysis() {
    }

    public AiAnalysis(Request request, AiAnalysisType analysisType, String suggestedValue) {
        this.request = request;
        this.analysisType = analysisType;
        this.suggestedValue = suggestedValue;
    }

    public Request getRequest() {
        return request;
    }

    public AiAnalysisType getAnalysisType() {
        return analysisType;
    }

    public String getRawResult() {
        return rawResult;
    }

    public void setRawResult(String rawResult) {
        this.rawResult = rawResult;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public String getSuggestedValue() {
        return suggestedValue;
    }

    public String getAcceptedValue() {
        return acceptedValue;
    }

    public void setAcceptedValue(String acceptedValue) {
        this.acceptedValue = acceptedValue;
    }

    public User getValidatedBy() {
        return validatedBy;
    }

    public void setValidatedBy(User validatedBy) {
        this.validatedBy = validatedBy;
    }

    public Instant getValidatedAt() {
        return validatedAt;
    }

    public void setValidatedAt(Instant validatedAt) {
        this.validatedAt = validatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
