package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Ne touche jamais `active` : voir CatalogAdminService.activate/deactivateRequestType. */
public record UpsertRequestTypeRequest(
        @NotNull Long serviceCatalogId,
        @NotBlank String name,
        String description,
        String targetDelayDescription,
        String requiredDocuments,
        String contactInfo,
        boolean reopenAllowed,
        int displayOrder) {
}
