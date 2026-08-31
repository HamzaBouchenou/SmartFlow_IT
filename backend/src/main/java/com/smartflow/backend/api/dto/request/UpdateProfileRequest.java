package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * §6.1 - "Consultation et mise à jour des informations de profil autorisées", par
 * l'utilisateur lui-même. Volontairement plus étroit qu'UpdateUserRequest (réservé à
 * FUNCTIONAL_ADMIN, UserAdminService) : ni e-mail (identité de connexion), ni service de
 * rattachement, ni responsable hiérarchique - la ligne suivante du même §6.1 ("Gestion des
 * rôles, du service de rattachement et du responsable hiérarchique") les réserve
 * explicitement à un rôle d'administration distinct.
 */
public record UpdateProfileRequest(@NotBlank String firstName, @NotBlank String lastName) {
}
