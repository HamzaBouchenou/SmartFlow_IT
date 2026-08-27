package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/** §6.4 - "Création" du brouillon. fieldValues (code de champ -> valeur brute) est optionnel. */
public record CreateRequestRequest(
        @NotNull Long requestTypeId,
        @NotBlank String title,
        String description,
        Map<String, String> fieldValues) {
}
