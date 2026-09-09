package com.smartflow.backend.api.dto.response;

/**
 * §5.1 - une habilitation détenue par l'utilisateur : le rôle, le niveau de périmètre et,
 * quand ce périmètre désigne un objet réel (une équipe, un service, une direction), son
 * nom. `scopeLabel` est nul pour OWN et GLOBAL, qui ne désignent aucun objet.
 *
 * Purement informatif, comme `LoginResponse.roles` : aucun écran ne décide d'une action
 * sur cette valeur (§11.1 - une action se lit dans `availableActions[]`, jamais dans un
 * rôle), elle sert seulement à ce que l'utilisateur sache de quoi il est habilité.
 */
public record RoleAssignmentResponse(String role, String scopeType, String scopeLabel) {
}
