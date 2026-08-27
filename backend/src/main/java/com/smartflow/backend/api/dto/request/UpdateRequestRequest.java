package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/** §6.4 - "modification du brouillon". */
public record UpdateRequestRequest(
        @NotBlank String title,
        String description,
        Map<String, String> fieldValues) {
}
