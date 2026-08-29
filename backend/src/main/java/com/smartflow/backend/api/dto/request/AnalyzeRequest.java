package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.AiAnalysisType;
import jakarta.validation.constraints.NotNull;

/** §11.2/§12.1 - POST /api/v1/ai/requests/{id}/analyze. */
public record AnalyzeRequest(@NotNull AiAnalysisType analysisType) {
}
