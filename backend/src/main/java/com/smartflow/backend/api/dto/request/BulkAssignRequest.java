package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * §6.6 - "Actions en masse limitées aux changements ne présentant pas de risque
 * fonctionnel." ASSIGN est la seule action exposée en masse (BulkAssignmentService) : elle
 * ne décide jamais de l'issue d'une demande (contrairement à VALIDATE/REJECT/CLOSE), donc
 * aucun risque fonctionnel à l'appliquer à plusieurs dossiers d'un geste - exactement les
 * mêmes champs, et la même exclusivité assignedUserId/assignedTeamId/autoAssign, que
 * ExecuteTransitionRequest pour une seule demande.
 */
public record BulkAssignRequest(
        @NotEmpty List<Long> requestIds,
        Long assignedUserId,
        Long assignedTeamId,
        Boolean autoAssign) {

    /** Absent dans le JSON signifie `false`, pas une erreur - voir ExecuteTransitionRequest.isAutoAssign. */
    public boolean isAutoAssign() {
        return Boolean.TRUE.equals(autoAssign);
    }
}
