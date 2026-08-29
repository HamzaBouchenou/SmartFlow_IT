package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** RG-10 - validation humaine d'une suggestion IA existante. */
public record ValidateAiAnalysisRequest(@NotBlank String acceptedValue) {
}
