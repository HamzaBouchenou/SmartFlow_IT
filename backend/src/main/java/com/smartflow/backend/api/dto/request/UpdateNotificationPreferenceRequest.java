package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * §6.8 - activer/désactiver l'e-mail pour un type de notification. `Boolean` et non
 * `boolean` : un champ primitif absent du JSON ferait échouer la désérialisation Jackson en
 * MALFORMED_REQUEST au lieu d'une VALIDATION_ERROR propre (CLAUDE.md) - et ici l'absence
 * n'a pas de défaut sensé (activer et désactiver sont deux intentions opposées), donc
 * `@NotNull`.
 */
public record UpdateNotificationPreferenceRequest(@NotNull Boolean emailEnabled) {
}
