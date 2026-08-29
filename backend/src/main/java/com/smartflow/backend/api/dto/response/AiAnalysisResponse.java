package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/**
 * §12.1/RG-10 - suggestedValue reste une aide (CLASSIFICATION : JSON {"category",
 * "priority"} ; SUMMARY : texte). acceptedValue/validatedById/validatedAt restent null tant
 * qu'aucun humain n'a validé (ADR-16, docs/DECISIONS.md).
 */
public record AiAnalysisResponse(Long id, Long requestId, String analysisType, String rawResult,
                                  Double confidenceScore, String suggestedValue, String acceptedValue,
                                  Long validatedById, String validatedByName, Instant validatedAt, Instant createdAt) {
}
