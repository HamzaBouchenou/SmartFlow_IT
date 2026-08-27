package com.smartflow.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Administrable e-mail template (§6.10 - "Gestion... des modèles d'e-mail").
 */
@Entity
@Table(name = "email_templates")
public class EmailTemplate extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false)
    private String subject;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onChange() {
        updatedAt = Instant.now();
    }

    protected EmailTemplate() {
    }

    public EmailTemplate(String code, String subject, String bodyHtml) {
        this.code = code;
        this.subject = subject;
        this.bodyHtml = bodyHtml;
    }

    public String getCode() {
        return code;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getBodyHtml() {
        return bodyHtml;
    }

    public void setBodyHtml(String bodyHtml) {
        this.bodyHtml = bodyHtml;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
