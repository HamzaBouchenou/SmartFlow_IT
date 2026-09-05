package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** §6.7 - "délai de prise en charge et délai de résolution", en minutes. useBusinessCalendar
 * reste hors socle (ADR-09) - jamais posé depuis cet écran, toujours `false`. */
public record UpsertSlaRequest(
        @NotNull @Min(1) Integer firstResponseMinutes,
        @NotNull @Min(1) Integer resolutionMinutes) {
}
