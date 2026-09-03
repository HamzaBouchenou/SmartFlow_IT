package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * displayOrder is `Integer`, never a primitive `int`: a JSON body omitting the key would
 * otherwise fail Jackson deserialization of this record with MALFORMED_REQUEST (400)
 * instead of a clean field-level VALIDATION_ERROR - the same trap already documented on
 * UpsertStepRequest/ExecuteTransitionRequest/BulkAssignRequest (CLAUDE.md). displayOrder
 * has no sensible default here (an ordering value can't default to anything meaningful),
 * so it stays `@NotNull` rather than gaining an isXxx()-style accessor.
 */
public record UpsertFieldOptionRequest(@NotBlank String value, @NotBlank String label, @NotNull Integer displayOrder) {
}
