package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpsertFieldOptionRequest(@NotBlank String value, @NotBlank String label, int displayOrder) {
}
