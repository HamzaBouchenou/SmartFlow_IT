package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.FieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * §6.3/§6.10 - un champ d'un FormDefinition DRAFT (ADR-17 - jamais PUBLISHED/ARCHIVED).
 *
 * required/displayOrder sont tous deux `Boolean`/`Integer`, jamais `boolean`/`int` : un
 * champ primitif absent du JSON fait échouer la désérialisation Jackson en
 * MALFORMED_REQUEST (400) plutôt que de recevoir sa valeur par défaut - même piège déjà
 * documenté sur UpsertStepRequest (CLAUDE.md). displayOrder reste `@NotNull` (pas de
 * défaut sensé) ; required a un défaut sensé (absent = non obligatoire) via isRequired().
 */
public record UpsertFormFieldRequest(
        @NotBlank String code,
        @NotBlank String label,
        @NotNull FieldType fieldType,
        Boolean required,
        @NotNull Integer displayOrder,
        String helpText,
        String visibleWhenFieldCode,
        String visibleWhenValue) {

    /** Absent dans le JSON signifie `false` (non obligatoire), pas une erreur. */
    public boolean isRequired() {
        return Boolean.TRUE.equals(required);
    }
}
