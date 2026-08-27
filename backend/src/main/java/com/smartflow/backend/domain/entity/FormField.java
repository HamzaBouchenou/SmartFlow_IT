package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.FieldType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A configurable field of a FormDefinition (§6.3). visibleWhenFieldCode/Value implement
 * the simple "afficher un champ selon la valeur d'un autre champ" conditional display.
 */
@Entity
@Table(name = "form_fields")
public class FormField extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_definition_id", nullable = false)
    private FormDefinition formDefinition;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, length = 16)
    private FieldType fieldType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "help_text")
    private String helpText;

    @Column(name = "visible_when_field_code", length = 100)
    private String visibleWhenFieldCode;

    @Column(name = "visible_when_value")
    private String visibleWhenValue;

    protected FormField() {
    }

    public FormField(FormDefinition formDefinition, String code, String label, FieldType fieldType) {
        this.formDefinition = formDefinition;
        this.code = code;
        this.label = label;
        this.fieldType = fieldType;
    }

    public FormDefinition getFormDefinition() {
        return formDefinition;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public String getVisibleWhenFieldCode() {
        return visibleWhenFieldCode;
    }

    public void setVisibleWhenFieldCode(String visibleWhenFieldCode) {
        this.visibleWhenFieldCode = visibleWhenFieldCode;
    }

    public String getVisibleWhenValue() {
        return visibleWhenValue;
    }

    public void setVisibleWhenValue(String visibleWhenValue) {
        this.visibleWhenValue = visibleWhenValue;
    }
}
