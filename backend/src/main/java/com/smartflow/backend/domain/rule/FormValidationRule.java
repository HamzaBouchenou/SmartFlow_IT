package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.FieldType;
import com.smartflow.backend.domain.exception.FormValidationException;
import com.smartflow.backend.domain.exception.FormValidationException.FieldValidationError;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * §6.3 - "Contrôles de validation... côté serveur" : le pendant serveur de la validation
 * déjà faite côté interface, jamais la seule ligne de défense. Pure - pas de Spring, pas de
 * base : application/service résout les FormFieldSpec depuis le FormDefinition publié
 * (FormField + FieldOption) avant d'appeler validate().
 *
 * Un champ masqué par sa propre règle d'affichage conditionnel (visibleWhenFieldCode/Value,
 * §6.3) n'est jamais obligatoire ni contrôlé en format : on ne peut pas exiger une valeur
 * que l'interface n'a jamais montrée. Les champs FILE sont ignorés entièrement - aucun
 * point d'upload de pièce jointe n'existe encore (§6.4/RG-09, lot ultérieur), donc rien ici
 * ne peut recevoir ni vérifier une valeur de fichier.
 */
public class FormValidationRule {

    public void validate(List<FormFieldSpec> fields, Map<String, String> values) {
        List<FieldValidationError> errors = new ArrayList<>();
        for (FormFieldSpec field : fields) {
            if (field.fieldType() == FieldType.FILE || !isVisible(field, values)) {
                continue;
            }
            String value = values.get(field.code());
            if (value == null || value.isBlank()) {
                if (field.required()) {
                    errors.add(new FieldValidationError(field.code(), "ne doit pas être vide"));
                }
                continue;
            }
            validateFormat(field, value).ifPresent(errors::add);
        }
        if (!errors.isEmpty()) {
            throw new FormValidationException(errors);
        }
    }

    private boolean isVisible(FormFieldSpec field, Map<String, String> values) {
        return field.visibleWhenFieldCode() == null
                || Objects.equals(values.get(field.visibleWhenFieldCode()), field.visibleWhenValue());
    }

    private Optional<FieldValidationError> validateFormat(FormFieldSpec field, String value) {
        String message = switch (field.fieldType()) {
            case NUMBER -> isValidNumber(value) ? null : "doit être un nombre";
            case DATE -> isValidDate(value) ? null : "doit être une date valide (AAAA-MM-JJ)";
            case CHECKBOX -> ("true".equals(value) || "false".equals(value)) ? null : "doit être vrai ou faux";
            case LIST -> field.allowedValues().contains(value) ? null : "valeur non autorisée";
            case USER, DEPARTMENT -> isValidLong(value) ? null : "doit être un identifiant valide";
            case TEXT, FILE -> null;
        };
        return message == null ? Optional.empty() : Optional.of(new FieldValidationError(field.code(), message));
    }

    private boolean isValidNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isValidDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean isValidLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
