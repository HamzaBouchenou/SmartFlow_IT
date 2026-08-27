package com.smartflow.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A possible value for a LIST-type FormField (§6.3 - "gestion... des valeurs possibles").
 */
@Entity
@Table(name = "field_options")
public class FieldOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_field_id", nullable = false)
    private FormField formField;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private String label;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected FieldOption() {
    }

    public FieldOption(FormField formField, String value, String label) {
        this.formField = formField;
        this.value = value;
        this.label = label;
    }

    public FormField getFormField() {
        return formField;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
