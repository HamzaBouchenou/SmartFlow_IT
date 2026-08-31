package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.WorkflowAction;
import jakarta.validation.constraints.NotNull;

/** §6.5/§6.10 - une transition légale d'une étape d'un WorkflowDefinition DRAFT (ADR-17).
 * `toStepId` est omis pour CLOSE (action terminale). Conditions simples (§6.5 -
 * "catégorie, priorité, service ou une valeur du formulaire"), toutes optionnelles. */
public record UpsertTransitionRequest(
        @NotNull WorkflowAction action,
        Long toStepId,
        Priority conditionPriority,
        Long conditionDepartmentId,
        String conditionFieldCode,
        String conditionFieldValue) {
}
