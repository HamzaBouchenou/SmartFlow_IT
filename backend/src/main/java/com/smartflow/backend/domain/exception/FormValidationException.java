package com.smartflow.backend.domain.exception;

import java.util.List;

/**
 * §6.3 - "Contrôles de validation... côté serveur" a échoué : au moins un champ soumis ne
 * respecte pas la définition de formulaire (obligation, format, valeur autorisée). Thrown
 * by domain/rule/FormValidationRule - see BusinessException's own javadoc ("raised by
 * domain/rule and application/service"). crosscutting/error/GlobalExceptionHandler maps
 * getFieldErrors() onto the common error format's fieldErrors[] (CLAUDE.md), exactly as it
 * already does for bean-validation failures on a request DTO.
 */
public class FormValidationException extends BusinessException {

    private final List<FieldValidationError> fieldErrors;

    public FormValidationException(List<FieldValidationError> fieldErrors) {
        super("VALIDATION_ERROR", "Le formulaire contient des champs invalides.");
        this.fieldErrors = fieldErrors;
    }

    public List<FieldValidationError> getFieldErrors() {
        return fieldErrors;
    }

    public record FieldValidationError(String field, String message) {
    }
}
