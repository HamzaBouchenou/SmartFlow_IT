package com.smartflow.backend.api.dto.response;

/**
 * §6.2 - "Affichage des informations utiles avant la saisie : description, délai cible,
 * pièces nécessaires et contacts."
 */
public record RequestTypeResponse(
        Long id,
        Long serviceCatalogId,
        String name,
        String description,
        String targetDelayDescription,
        String requiredDocuments,
        String contactInfo,
        int displayOrder) {
}
