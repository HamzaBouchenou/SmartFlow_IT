package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.WorkflowAction;
import jakarta.validation.constraints.NotNull;

/**
 * §6.5 - exécute une action de workflow sur une demande soumise. comment n'est obligatoire
 * que pour REJECT (RG-05, vérifié par domain/rule/CommentRequirementRule, jamais par une
 * annotation ici - CLAUDE.md : pas de règle de gestion recodée en dehors de domain/rule).
 * closureReason/closureSolution ne sont lus que pour CLOSE (§6.4).
 *
 * assignedUserId/assignedTeamId (§6.6) ne sont lus que pour ASSIGN, et sont exclusifs l'un
 * de l'autre : ni l'un ni l'autre fourni signifie "prendre en charge" (auto-affectation à
 * l'auteur de l'appel), affecter un agent précis ("affectation manuelle à un agent
 * habilité") ou affecter toute une équipe (file d'équipe) sont les deux alternatives.
 */
public record ExecuteTransitionRequest(
        @NotNull WorkflowAction action,
        String comment,
        String closureReason,
        String closureSolution,
        Long assignedUserId,
        Long assignedTeamId) {
}
