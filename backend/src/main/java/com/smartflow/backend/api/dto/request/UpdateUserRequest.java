package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Ne touche jamais le mot de passe ni `active` : voir UserAdminService.resetPassword/
 * activate/deactivate. */
public record UpdateUserRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        Long departmentId,
        Long managerId) {
}
