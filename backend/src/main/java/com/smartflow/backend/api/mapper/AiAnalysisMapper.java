package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.AiAnalysisResponse;
import com.smartflow.backend.domain.entity.AiAnalysis;
import com.smartflow.backend.domain.entity.User;

public final class AiAnalysisMapper {

    private AiAnalysisMapper() {
    }

    public static AiAnalysisResponse toResponse(AiAnalysis analysis) {
        User validatedBy = analysis.getValidatedBy();
        return new AiAnalysisResponse(analysis.getId(), analysis.getRequest().getId(), analysis.getAnalysisType().name(),
                analysis.getRawResult(), analysis.getConfidenceScore(), analysis.getSuggestedValue(),
                analysis.getAcceptedValue(), validatedBy != null ? validatedBy.getId() : null,
                validatedBy != null ? validatedBy.getFirstName() + " " + validatedBy.getLastName() : null,
                analysis.getValidatedAt(), analysis.getCreatedAt());
    }
}
