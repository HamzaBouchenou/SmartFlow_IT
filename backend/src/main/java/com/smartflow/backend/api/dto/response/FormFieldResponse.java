package com.smartflow.backend.api.dto.response;

import com.smartflow.backend.domain.enums.FieldType;

import java.util.List;

/**
 * §6.3 - un champ configuré : type, obligation, ordre, aide à la saisie, valeurs possibles
 * (pour LIST) et l'affichage conditionnel simple ("selon la valeur d'un autre champ").
 * visibleWhenFieldCode/visibleWhenValue sont null quand le champ est toujours visible.
 */
public record FormFieldResponse(
        Long id,
        String code,
        String label,
        FieldType fieldType,
        boolean required,
        int displayOrder,
        String helpText,
        String visibleWhenFieldCode,
        String visibleWhenValue,
        List<FieldOptionResponse> options) {
}
