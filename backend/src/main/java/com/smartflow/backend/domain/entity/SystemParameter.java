package com.smartflow.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Generic key/value store for administrable global settings (§6.10 - "formats acceptés,
 * taille maximale des fichiers, durée des sessions et seuils d'alerte") and for RG-08's
 * configurable reopening window duration.
 */
@Entity
@Table(name = "system_parameters")
public class SystemParameter extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String key;

    @Column(nullable = false, columnDefinition = "text")
    private String value;

    @Column
    private String description;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    protected void onChange() {
        updatedAt = Instant.now();
    }

    protected SystemParameter() {
    }

    public SystemParameter(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
