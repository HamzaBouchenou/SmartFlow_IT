package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** §6.3/§6.10 - un champ d'un FormDefinition DRAFT (ADR-17 - jamais PUBLISHED/ARCHIVED). */
public record UpsertFormFieldRequest(
        @NotBlank String code,
        @NotBlank String label,
        @NotNull FieldType fieldType,
        boolean required,
        int displayOrder,
        String helpText,
        String visibleWhenFieldCode,
        String visibleWhenValue) {
}
