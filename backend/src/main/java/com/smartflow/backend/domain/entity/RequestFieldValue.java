package com.smartflow.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Value entered for one FormField on one Request. Stored as text; type-specific parsing
 * and validation happens against the FieldType at the application layer, not here (§6.3 -
 * "Conservation des valeurs saisies lorsque la demande est sauvegardée en brouillon").
 */
@Entity
@Table(name = "request_field_values")
public class RequestFieldValue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "form_field_id", nullable = false)
    private FormField formField;

    @Column(columnDefinition = "text")
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onChange() {
        updatedAt = Instant.now();
    }

    protected RequestFieldValue() {
    }

    public RequestFieldValue(Request request, FormField formField, String value) {
        this.request = request;
        this.formField = formField;
        this.value = value;
    }

    public Request getRequest() {
        return request;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
