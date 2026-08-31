package com.smartflow.backend.domain.exception;

/**
 * §6.1 - libre-service : l'utilisateur authentifié tente de modifier son propre compte
 * (mot de passe, informations de profil) avec une donnée invalide - typiquement l'ancien
 * mot de passe fourni à ProfileService.changePassword qui ne correspond pas au hash
 * actuel. Distinct d'AdministrationValidationException (réservée aux écrans §6.10, réservés
 * à un rôle d'administration) : ici l'appelant agit sur lui-même, jamais sur un tiers.
 */
public class ProfileValidationException extends BusinessException {

    public ProfileValidationException(String code, String message) {
        super(code, message);
    }
}
