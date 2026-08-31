package com.smartflow.backend.api.dto.response;

import com.smartflow.backend.domain.enums.FieldType;

import java.util.List;

/** §6.3/§6.10 - un champ configuré, vue administration. */
public record FormFieldAdminResponse(
        Long id,
        String code,
        String label,
        FieldType fieldType,
        boolean required,
        int displayOrder,
        String helpText,
        String visibleWhenFieldCode,
        String visibleWhenValue,
        List<FieldOptionAdminResponse> options) {
}
