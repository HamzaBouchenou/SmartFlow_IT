package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * §6.4 - le dossier d'une demande. availableActions[] est la seule source dont le
 * front-end doit dériver les boutons d'action (CLAUDE.md - jamais depuis le rôle) : chaque
 * entrée vient de AuthorizationService.canAct, jamais d'une règle recopiée côté client.
 * canQualify suit exactement le même principe pour le bouton "qualifier" (§5/RG-07) :
 * AuthorizationService.canQualify, jamais une règle de rôle recopiée côté client - "qualifier"
 * n'est pas un WorkflowAction (voir sa javadoc), donc jamais dans availableActions[] lui-même.
 * assignedUserId/assignedTeamId (§6.6) reflètent la seule TaskAssignment active, ou sont
 * tous les deux null si personne n'a encore pris en charge la demande.
 *
 * §6.7 - slaStatus/slaDueAtFirstResponse/slaDueAtResolution sont lus tels quels depuis le
 * modèle matérialisé de Request (RG-07 - jamais recalculés ici, CLAUDE.md "ce qu'il ne faut
 * jamais faire - calculer un statut SLA à la volée"). reopenDeadline (RG-08/ADR-14) n'a de
 * sens que pour une demande CLOSED dont le type autorise la réouverture ; `null` sinon.
 * closureReason/closureSolution/satisfactionRating (§6.4) ne sont renseignés qu'après CLOSE ;
 * `null` avant.
 */
public record RequestDetailResponse(
        Long id,
        String reference,
        Long requestTypeId,
        String status,
        String priority,
        String title,
        String description,
        Long currentStepId,
        Instant submittedAt,
        Map<String, String> fieldValues,
        List<String> availableActions,
        boolean canQualify,
        Long assignedUserId,
        String assignedUserName,
        Long assignedTeamId,
        String assignedTeamName,
        String slaStatus,
        Instant slaDueAtFirstResponse,
        Instant slaDueAtResolution,
        Instant reopenDeadline,
        String closureReason,
        String closureSolution,
        Integer satisfactionRating) {
}
