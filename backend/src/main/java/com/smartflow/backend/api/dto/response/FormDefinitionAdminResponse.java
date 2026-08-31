package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.List;

/** §6.3/§10.1/§6.10 - une version d'un formulaire, vue administration (ADR-17 - DRAFT
 * modifiable, PUBLISHED/ARCHIVED immuable). */
public record FormDefinitionAdminResponse(
        Long id,
        Long requestTypeId,
        int version,
        String status,
        Instant publishedAt,
        Instant createdAt,
        List<FormFieldAdminResponse> fields) {
}
