package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.Priority;
import jakarta.validation.constraints.NotNull;

/** §5/RG-07 - poser la Priority d'une demande soumise. Voir RequestService.qualify. */
public record QualifyRequestRequest(@NotNull Priority priority) {
}
