package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** §6.1 - "Connexion par identifiant et mot de passe" ; l'identifiant est l'e-mail. */
public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {
}
