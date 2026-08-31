package com.smartflow.backend.domain.exception;

/**
 * §6.10 - une valeur soumise sur un écran d'administration (paramètres généraux, SLA...)
 * ne respecte pas le format ou la contrainte attendue pour ce champ, ou désigne une clé/
 * combinaison qui n'est pas administrable. Distinct de FormValidationException, qui est
 * spécifique aux formulaires dynamiques d'une demande (§6.3) - un écran d'administration
 * n'a ni RequestFieldValue ni FormField.
 */
public class AdministrationValidationException extends BusinessException {

    public AdministrationValidationException(String code, String message) {
        super(code, message);
    }
}
