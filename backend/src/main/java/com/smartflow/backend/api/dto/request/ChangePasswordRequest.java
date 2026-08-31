package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * §6.1/§13 - changement de mot de passe par l'utilisateur lui-même. Distinct du geste
 * administratif équivalent (ResetPasswordRequest, UserAdminService.resetPassword) qui ne
 * connaît pas l'ancien mot de passe : ici currentPassword est exigé et vérifié
 * (ProfileService), exactement comme tout changement de mot de passe en libre-service.
 * Même contrainte minimale que CreateUserRequest/ResetPasswordRequest (8 caractères).
 */
public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 8) String newPassword) {
}
