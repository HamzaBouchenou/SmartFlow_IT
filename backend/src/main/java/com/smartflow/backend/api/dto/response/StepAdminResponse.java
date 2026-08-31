package com.smartflow.backend.api.dto.response;

/** §6.5/§6.10 - une étape d'un WorkflowDefinition, vue administration. */
public record StepAdminResponse(
        Long id,
        String code,
        String name,
        int displayOrder,
        String responsibleRole,
        Long responsibleTeamId,
        String responsibleTeamName,
        boolean suspendSla) {
}
