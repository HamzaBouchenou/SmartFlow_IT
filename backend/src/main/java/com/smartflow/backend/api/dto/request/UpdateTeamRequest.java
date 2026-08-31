package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Ne touche jamais `active` : voir OrganizationAdminService.activate/deactivate. */
public record UpdateTeamRequest(@NotBlank String name, @NotNull Long departmentId, Long leadId) {
}
