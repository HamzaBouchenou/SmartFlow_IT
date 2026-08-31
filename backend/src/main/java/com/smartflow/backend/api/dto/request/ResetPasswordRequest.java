package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** §6.1 - "Réinitialisation du mot de passe" par un administrateur. */
public record ResetPasswordRequest(@NotBlank @Size(min = 8) String newPassword) {
}
