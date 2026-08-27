package com.smartflow.backend.api.dto.response;

/** §6.2 - fiche de catalogue affichée au demandeur. Jamais l'entité ServiceCatalog elle-même. */
public record ServiceCatalogResponse(
        Long id,
        String name,
        String description,
        String category,
        Long departmentId,
        int displayOrder) {
}
