package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** §4.1 - ne touche jamais `active` : voir OrganizationAdminService.activate/deactivate. */
public record UpdateDepartmentRequest(@NotBlank String name, Long parentId, Long leadId) {
}
