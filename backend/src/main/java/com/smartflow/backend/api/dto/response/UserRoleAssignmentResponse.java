package com.smartflow.backend.api.dto.response;

/** §5.1 - une affectation de rôle, limitée à un périmètre. `scopeName` résout un id de
 * Team/Department en libellé lisible ; `null` pour OWN/GLOBAL (sans cible) ou si l'entité
 * référencée par `scopeId` a depuis été supprimée. */
public record UserRoleAssignmentResponse(
        Long id,
        Long userId,
        String role,
        String scopeType,
        Long scopeId,
        String scopeName) {
}
