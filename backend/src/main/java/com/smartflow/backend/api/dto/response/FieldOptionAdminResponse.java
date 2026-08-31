package com.smartflow.backend.api.dto.response;

/** §6.3/§6.10 - une valeur possible d'un champ LIST, vue administration (porte `id`,
 * nécessaire pour cibler une modification/suppression - FieldOptionResponse, côté lecture
 * publique, ne le fait pas). */
public record FieldOptionAdminResponse(Long id, String value, String label, int displayOrder) {
}
