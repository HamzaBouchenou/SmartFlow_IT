package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** §6.1/§6.10 - création d'un compte par un administrateur. Pas d'auto-inscription dans
 * le périmètre du PFA : `password` est le mot de passe initial, à faire changer par
 * l'utilisateur via ProfileService/POST /api/v1/profile/password (libre-service). */
public record CreateUserRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        Long departmentId,
        Long managerId) {
}
