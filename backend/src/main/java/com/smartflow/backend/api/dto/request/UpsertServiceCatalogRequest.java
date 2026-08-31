package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Ne touche jamais `active` : voir CatalogAdminService.activate/deactivateService. */
public record UpsertServiceCatalogRequest(
        @NotBlank String name,
        String description,
        String category,
        @NotNull Long departmentId,
        int displayOrder) {
}
