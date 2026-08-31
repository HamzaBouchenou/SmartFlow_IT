package com.smartflow.backend.api.dto.response;

/**
 * §6.10 - une ligne de l'écran "Paramètres généraux". `value` est l'effectif actuel
 * (surcharge en base si elle existe, sinon le défaut appliqué par le code qui consomme
 * `key`) ; `overridden` distingue les deux pour l'écran sans deviner depuis `value` seul.
 */
public record SystemParameterResponse(
        String key,
        String label,
        String description,
        String type,
        String value,
        boolean overridden) {
}
