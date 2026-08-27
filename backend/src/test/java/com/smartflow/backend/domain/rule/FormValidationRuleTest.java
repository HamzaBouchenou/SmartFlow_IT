package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.FieldType;
import com.smartflow.backend.domain.exception.FormValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §6.3 - "Contrôles de validation... côté serveur". Pure: no Spring, no database - the
 * FormFieldSpec inputs stand in for what application/service resolves from the persisted
 * FormField/FieldOption graph before calling validate().
 */
class FormValidationRuleTest {

    private final FormValidationRule rule = new FormValidationRule();

    @Test
    @DisplayName("a required field left blank fails with 'ne doit pas être vide'")
    void requiredBlankFieldFails() {
        List<FormFieldSpec> fields = List.of(
                new FormFieldSpec("title", FieldType.TEXT, true, List.of(), null, null));

        assertThatThrownBy(() -> rule.validate(fields, Map.of()))
                .isInstanceOf(FormValidationException.class)
                .satisfies(ex -> assertThat(((FormValidationException) ex).getFieldErrors())
                        .containsExactly(new FormValidationException.FieldValidationError("title", "ne doit pas être vide")));
    }

    @Test
    @DisplayName("an optional field left blank passes")
    void optionalBlankFieldPasses() {
        List<FormFieldSpec> fields = List.of(
                new FormFieldSpec("comment", FieldType.TEXT, false, List.of(), null, null));

        assertThatCode(() -> rule.validate(fields, Map.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a required field hidden by a conditional-display rule is not required (§6.3 - the UI never showed it)")
    void requiredButHiddenFieldPasses() {
        List<FormFieldSpec> fields = List.of(
                new FormFieldSpec("urgency", FieldType.LIST, true, List.of("Normal", "Urgent"), null, null),
                new FormFieldSpec("urgent_justification", FieldType.TEXT, true, List.of(), "urgency", "Urgent"));

        assertThatCode(() -> rule.validate(fields, Map.of("urgency", "Normal"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a required field shown by its conditional-display rule is required")
    void requiredAndVisibleFieldFailsWhenBlank() {
        List<FormFieldSpec> fields = List.of(
                new FormFieldSpec("urgency", FieldType.LIST, true, List.of("Normal", "Urgent"), null, null),
                new FormFieldSpec("urgent_justification", FieldType.TEXT, true, List.of(), "urgency", "Urgent"));

        assertThatThrownBy(() -> rule.validate(fields, Map.of("urgency", "Urgent")))
                .isInstanceOf(FormValidationException.class)
                .satisfies(ex -> assertThat(((FormValidationException) ex).getFieldErrors())
                        .containsExactly(new FormValidationException.FieldValidationError("urgent_justification", "ne doit pas être vide")));
    }

    @Test
    @DisplayName("NUMBER rejects a non-numeric value")
    void numberRejectsNonNumericValue() {
        List<FormFieldSpec> fields = List.of(new FormFieldSpec("quantity", FieldType.NUMBER, true, List.of(), null, null));

        assertThatThrownBy(() -> rule.validate(fields, Map.of("quantity", "abc")))
                .isInstanceOf(FormValidationException.class)
                .satisfies(ex -> assertThat(((FormValidationException) ex).getFieldErrors())
                        .containsExactly(new FormValidationException.FieldValidationError("quantity", "doit être un nombre")));
    }

    @Test
    @DisplayName("NUMBER accepts a numeric value")
    void numberAcceptsNumericValue() {
        List<FormFieldSpec> fields = List.of(new FormFieldSpec("quantity", FieldType.NUMBER, true, List.of(), null, null));

        assertThatCode(() -> rule.validate(fields, Map.of("quantity", "3"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("DATE rejects a non-ISO value and accepts an ISO one")
    void dateFormatIsChecked() {
        List<FormFieldSpec> fields = List.of(new FormFieldSpec("neededBy", FieldType.DATE, true, List.of(), null, null));

        assertThatThrownBy(() -> rule.validate(fields, Map.of("neededBy", "31/12/2026")))
                .isInstanceOf(FormValidationException.class);
        assertThatCode(() -> rule.validate(fields, Map.of("neededBy", "2026-12-31"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CHECKBOX only accepts the literal strings 'true' or 'false'")
    void checkboxOnlyAcceptsTrueOrFalse() {
        List<FormFieldSpec> fields = List.of(new FormFieldSpec("approved", FieldType.CHECKBOX, true, List.of(), null, null));

        assertThatThrownBy(() -> rule.validate(fields, Map.of("approved", "yes")))
                .isInstanceOf(FormValidationException.class);
        assertThatCode(() -> rule.validate(fields, Map.of("approved", "true"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("LIST only accepts one of the field's configured allowed values")
    void listOnlyAcceptsAllowedValues() {
        List<FormFieldSpec> fields = List.of(new FormFieldSpec("urgency", FieldType.LIST, true, List.of("Normal", "Urgent"), null, null));

        assertThatThrownBy(() -> rule.validate(fields, Map.of("urgency", "Faible")))
                .isInstanceOf(FormValidationException.class)
                .satisfies(ex -> assertThat(((FormValidationException) ex).getFieldErrors())
                        .containsExactly(new FormValidationException.FieldValidationError("urgency", "valeur non autorisée")));
        assertThatCode(() -> rule.validate(fields, Map.of("urgency", "Urgent"))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("USER and DEPARTMENT fields require a parsable numeric id")
    void userAndDepartmentRequireNumericId() {
        List<FormFieldSpec> fields = List.of(
                new FormFieldSpec("delegate", FieldType.USER, true, List.of(), null, null),
                new FormFieldSpec("targetDept", FieldType.DEPARTMENT, true, List.of(), null, null));

        assertThatThrownBy(() -> rule.validate(fields, Map.of("delegate", "not-an-id", "targetDept", "3")))
                .isInstanceOf(FormValidationException.class)
                .satisfies(ex -> assertThat(((FormValidationException) ex).getFieldErrors())
                        .containsExactly(new FormValidationException.FieldValidationError("delegate", "doit être un identifiant valide")));
    }

    @Test
    @DisplayName("FILE fields are never validated - no attachment upload endpoint exists yet")
    void fileFieldsAreSkipped() {
        List<FormFieldSpec> fields = List.of(new FormFieldSpec("supportingDoc", FieldType.FILE, true, List.of(), null, null));

        assertThatCode(() -> rule.validate(fields, Map.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("multiple invalid fields are all reported together, not just the first")
    void reportsAllInvalidFieldsTogether() {
        List<FormFieldSpec> fields = List.of(
                new FormFieldSpec("title", FieldType.TEXT, true, List.of(), null, null),
                new FormFieldSpec("quantity", FieldType.NUMBER, true, List.of(), null, null));

        assertThatThrownBy(() -> rule.validate(fields, Map.of("quantity", "abc")))
                .isInstanceOf(FormValidationException.class)
                .satisfies(ex -> assertThat(((FormValidationException) ex).getFieldErrors()).hasSize(2));
    }
}
