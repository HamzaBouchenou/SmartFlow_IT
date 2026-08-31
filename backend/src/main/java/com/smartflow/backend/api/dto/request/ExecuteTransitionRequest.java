package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.WorkflowAction;
import jakarta.validation.constraints.NotNull;

/**
 * §6.5 - exécute une action de workflow sur une demande soumise. comment n'est obligatoire
 * que pour REJECT (RG-05, vérifié par domain/rule/CommentRequirementRule, jamais par une
 * annotation ici - CLAUDE.md : pas de règle de gestion recodée en dehors de domain/rule).
 * closureReason/closureSolution/satisfactionRating ne sont lus que pour CLOSE (§6.4 -
 * "Clôture avec motif, solution apportée et niveau de satisfaction facultatif" : les trois
 * énoncés dans la même phrase, saisis ensemble à la clôture plutôt que dans un second geste
 * séparé). satisfactionRating reste facultatif (peut être `null`) ; 1 à 5 sinon (échelle non
 * précisée par le cahier des charges, choisie ici comme la plus commune).
 *
 * assignedUserId/assignedTeamId (§6.6) ne sont lus que pour ASSIGN, et sont exclusifs l'un
 * de l'autre : ni l'un ni l'autre fourni signifie "prendre en charge" (auto-affectation à
 * l'auteur de l'appel), affecter un agent précis ("affectation manuelle à un agent
 * habilité") ou affecter toute une équipe (file d'équipe) sont les deux alternatives.
 * autoAssign (§6.6 - "affectation automatique... règle de répartition simple") n'a de sens
 * qu'avec assignedTeamId seul (jamais assignedUserId) : le système choisit alors lui-même,
 * parmi les membres habilités de cette équipe, celui qui porte le moins de charge actuelle
 * (WorkflowTransitionService/AutoAssignmentRule) - jamais une simple dépose en file d'équipe.
 */
public record ExecuteTransitionRequest(
        @NotNull WorkflowAction action,
        String comment,
        String closureReason,
        String closureSolution,
        Integer satisfactionRating,
        Long assignedUserId,
        Long assignedTeamId,
        Boolean autoAssign) {

    /** Absent dans le JSON (comme tout le reste de ce record hors `action`) signifie `false`, pas une erreur. */
    public boolean isAutoAssign() {
        return Boolean.TRUE.equals(autoAssign);
    }
}
