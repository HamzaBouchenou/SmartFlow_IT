package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.PublicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A published version of the form attached to a RequestType (§10.1 - versioning). Once
 * PUBLISHED, its FormField rows are immutable; a change publishes a new FormDefinition.
 */
@Entity
@Table(name = "form_definitions")
public class FormDefinition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_type_id", nullable = false)
    private RequestType requestType;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PublicationStatus status = PublicationStatus.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    protected FormDefinition() {
    }

    public FormDefinition(RequestType requestType, int version) {
        this.requestType = requestType;
        this.version = version;
    }

    public RequestType getRequestType() {
        return requestType;
    }

    public int getVersion() {
        return version;
    }

    public PublicationStatus getStatus() {
        return status;
    }

    public void setStatus(PublicationStatus status) {
        this.status = status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
