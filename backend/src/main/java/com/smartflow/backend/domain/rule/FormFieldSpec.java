package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.FieldType;

import java.util.List;

/**
 * The shape FormValidationRule needs from a persisted FormField (+ its FieldOption rows for
 * LIST) - application/service resolves this from the real entities so the rule itself never
 * touches JPA or the database.
 */
public record FormFieldSpec(String code, FieldType fieldType, boolean required, List<String> allowedValues,
                             String visibleWhenFieldCode, String visibleWhenValue) {

    public FormFieldSpec {
        allowedValues = allowedValues == null ? List.of() : List.copyOf(allowedValues);
    }
}
