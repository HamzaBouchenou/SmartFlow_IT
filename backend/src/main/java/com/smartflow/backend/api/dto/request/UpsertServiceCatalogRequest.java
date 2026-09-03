package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Ne touche jamais `active` : voir CatalogAdminService.activate/deactivateService.
 *
 * displayOrder est `Integer`, jamais un `int` primitif : voir UpsertFieldOptionRequest pour
 * le piège Jackson que ce type évite (CLAUDE.md).
 */
public record UpsertServiceCatalogRequest(
        @NotBlank String name,
        String description,
        String category,
        @NotNull Long departmentId,
        @NotNull Integer displayOrder) {
}
