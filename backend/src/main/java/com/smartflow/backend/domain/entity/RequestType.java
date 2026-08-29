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
 * A request type offered under a ServiceCatalog entry (§6.2 - "Activation, désactivation
 * et ordre d'affichage des types de demande"). Carries the informational content shown
 * before submission ("délai cible, pièces nécessaires et contacts").
 */
@Entity
@Table(name = "request_types")
public class RequestType extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_catalog_id", nullable = false)
    private ServiceCatalog serviceCatalog;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "target_delay_description")
    private String targetDelayDescription;

    @Column(name = "required_documents", columnDefinition = "text")
    private String requiredDocuments;

    @Column(name = "contact_info", columnDefinition = "text")
    private String contactInfo;

    @Column(nullable = false)
    private boolean active = true;

    // ADR-14 (docs/DECISIONS.md) - RG-08 "si le service le permet" : ancré à ce grain
    // (comme SLA/WorkflowDefinition/FormDefinition), pas à ServiceCatalog ni à un paramètre
    // global.
    @Column(name = "reopen_allowed", nullable = false)
    private boolean reopenAllowed = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    protected RequestType() {
    }

    public RequestType(ServiceCatalog serviceCatalog, String name) {
        this.serviceCatalog = serviceCatalog;
        this.name = name;
    }

    public ServiceCatalog getServiceCatalog() {
        return serviceCatalog;
    }

    public void setServiceCatalog(ServiceCatalog serviceCatalog) {
        this.serviceCatalog = serviceCatalog;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTargetDelayDescription() {
        return targetDelayDescription;
    }

    public void setTargetDelayDescription(String targetDelayDescription) {
        this.targetDelayDescription = targetDelayDescription;
    }

    public String getRequiredDocuments() {
        return requiredDocuments;
    }

    public void setRequiredDocuments(String requiredDocuments) {
        this.requiredDocuments = requiredDocuments;
    }

    public String getContactInfo() {
        return contactInfo;
    }

    public void setContactInfo(String contactInfo) {
        this.contactInfo = contactInfo;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isReopenAllowed() {
        return reopenAllowed;
    }

    public void setReopenAllowed(boolean reopenAllowed) {
        this.reopenAllowed = reopenAllowed;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
