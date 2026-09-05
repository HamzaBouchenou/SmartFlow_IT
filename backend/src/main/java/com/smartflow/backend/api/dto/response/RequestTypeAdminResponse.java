package com.smartflow.backend.api.dto.response;

/** §6.2/§6.10 - un type de demande, vue administration (inclut les types désactivés). */
public record RequestTypeAdminResponse(
        Long id,
        Long serviceCatalogId,
        String name,
        String description,
        String targetDelayDescription,
        String requiredDocuments,
        String contactInfo,
        boolean reopenAllowed,
        int displayOrder,
        boolean active) {
}
