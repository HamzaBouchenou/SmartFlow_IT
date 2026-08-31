package com.smartflow.backend.application.service;

import java.time.Instant;

/**
 * §13.1/§6.10 - "Journal d'audit consultable avec filtres par utilisateur, action, objet et
 * période", tous optionnels et combinables - même forme que TaskQueueFilter (§6.6) pour la
 * même raison : chaque filtre devient une Specification composée avec Specification.allOf,
 * jamais une méthode dérivée par combinaison de champs.
 */
public record AuditLogFilter(Long actorId, String action, String objectType, Instant occurredFrom, Instant occurredTo) {

    public static AuditLogFilter none() {
        return new AuditLogFilter(null, null, null, null, null);
    }
}
