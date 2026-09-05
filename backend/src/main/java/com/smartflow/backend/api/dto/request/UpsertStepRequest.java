package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * §6.5/§6.10 - une étape d'un WorkflowDefinition DRAFT (ADR-17).
 *
 * displayOrder/suspendSla sont tous deux passés en type non-primitif (`Integer`/`Boolean`),
 * jamais `int`/`boolean` : un champ primitif absent du JSON fait échouer la désérialisation
 * Jackson d'un `record` en MALFORMED_REQUEST (400) plutôt que de recevoir sa valeur par
 * défaut - constaté ici même en recette (docs/CAHIER_DE_RECETTE.md, REC-SCN-16) avant
 * correction, le même piège déjà documenté sur ExecuteTransitionRequest/BulkAssignRequest
 * (CLAUDE.md, "état actuel du dépôt" - S10). L'écran d'administration (AdminWorkflowsPage)
 * envoie toujours les deux clés, donc invisible depuis le SPA - mais atteignable par tout
 * autre client, comme le prouve cette même recette exécutée en API. displayOrder reste
 * cependant réellement obligatoire (aucune valeur par défaut sensée pour un ordre
 * d'affichage) : `@NotNull` plutôt qu'un accesseur `isXxx()` qui traiterait l'absence comme
 * une valeur - une erreur `VALIDATION_ERROR` propre, comme `code`/`name` déjà.
 */
public record UpsertStepRequest(
        @NotBlank String code,
        @NotBlank String name,
        @NotNull Integer displayOrder,
        Role responsibleRole,
        Long responsibleTeamId,
        Boolean suspendSla) {

    /** Absent dans le JSON signifie `false`, pas une erreur. */
    public boolean isSuspendSla() {
        return Boolean.TRUE.equals(suspendSla);
    }
}
