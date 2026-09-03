package com.smartflow.backend.api.dto.response;

/**
 * §6.8 - une préférence de notification par type. `mandatory` vient de
 * MandatoryNotificationRule (ADR-12) : l'interface doit afficher ces types verrouillés
 * plutôt que d'offrir une bascule que le serveur refuserait - jamais une liste de types
 * obligatoires recopiée côté client.
 */
public record NotificationPreferenceResponse(String notificationType, boolean emailEnabled, boolean mandatory) {
}
