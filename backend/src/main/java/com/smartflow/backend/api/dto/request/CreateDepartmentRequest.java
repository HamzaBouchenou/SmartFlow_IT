package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** §4.1 - parentId `null` crée une direction, un id existant crée un service en dessous. */
public record CreateDepartmentRequest(@NotBlank String name, Long parentId, Long leadId) {
}
