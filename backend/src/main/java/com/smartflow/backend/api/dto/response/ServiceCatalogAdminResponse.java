package com.smartflow.backend.api.dto.response;

/** §6.2/§6.10 - une fiche de catalogue, vue administration (inclut les fiches désactivées,
 * contrairement à ServiceCatalogResponse qui ne sert que le catalogue public). */
public record ServiceCatalogAdminResponse(
        Long id,
        String name,
        String description,
        String category,
        Long departmentId,
        String departmentName,
        int displayOrder,
        boolean active) {
}
