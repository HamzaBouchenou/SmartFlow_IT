package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTeamRequest(@NotBlank String name, @NotNull Long departmentId, Long leadId) {
}
