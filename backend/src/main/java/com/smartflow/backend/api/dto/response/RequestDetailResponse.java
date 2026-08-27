package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * §6.4 - le dossier d'une demande. availableActions[] est la seule source dont le
 * front-end doit dériver les boutons d'action (CLAUDE.md - jamais depuis le rôle) : chaque
 * entrée vient de AuthorizationService.canAct, jamais d'une règle recopiée côté client.
 * assignedUserId/assignedTeamId (§6.6) reflètent la seule TaskAssignment active, ou sont
 * tous les deux null si personne n'a encore pris en charge la demande.
 */
public record RequestDetailResponse(
        Long id,
        String reference,
        Long requestTypeId,
        String status,
        String title,
        String description,
        Long currentStepId,
        Instant submittedAt,
        Map<String, String> fieldValues,
        List<String> availableActions,
        Long assignedUserId,
        String assignedUserName,
        Long assignedTeamId,
        String assignedTeamName) {
}
