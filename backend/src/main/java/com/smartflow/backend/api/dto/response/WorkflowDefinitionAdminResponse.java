package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.List;

/** §6.5/§10.1/§6.10 - une version d'un workflow, vue administration (ADR-17 - DRAFT
 * modifiable, PUBLISHED/ARCHIVED immuable, RG-03 - une demande déjà soumise reste sur la
 * version qu'elle a gelée). */
public record WorkflowDefinitionAdminResponse(
        Long id,
        Long requestTypeId,
        int version,
        String status,
        Instant publishedAt,
        Instant createdAt,
        List<StepAdminResponse> steps,
        List<TransitionAdminResponse> transitions) {
}
