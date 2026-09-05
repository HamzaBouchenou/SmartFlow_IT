package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/**
 * §13.1 - une ligne du journal d'audit : "l'utilisateur, la date, l'action, le type
 * d'objet, l'identifiant concerné, le résultat et un résumé des changements." actorId/
 * actorName sont `null` pour une action système (AuditLog's own javadoc - le balayage
 * d'archivage RG-12, par exemple), jamais pour masquer un acteur réel.
 */
public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        String action,
        String objectType,
        String objectId,
        String result,
        String summary,
        String traceId,
        Instant occurredAt) {
}
