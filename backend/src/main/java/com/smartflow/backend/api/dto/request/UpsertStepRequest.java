package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.Role;
import jakarta.validation.constraints.NotBlank;

/** §6.5/§6.10 - une étape d'un WorkflowDefinition DRAFT (ADR-17). */
public record UpsertStepRequest(
        @NotBlank String code,
        @NotBlank String name,
        int displayOrder,
        Role responsibleRole,
        Long responsibleTeamId,
        boolean suspendSla) {
}
