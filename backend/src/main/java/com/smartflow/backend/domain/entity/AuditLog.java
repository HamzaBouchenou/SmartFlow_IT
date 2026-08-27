package com.smartflow.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Sensitive-operation trail (RG-11, §13.1). Minimum content required by §13.1: actor, date,
 * action, object type, object id, result and a summary of the change. traceId ties an
 * entry back to the request-scoped correlation id (§8, crosscutting/logging) so an audit
 * row and its application logs can be cross-referenced. actor is nullable only for actions
 * genuinely triggered by the system itself (e.g. the SLA sweep), never to paper over a
 * missing user.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "object_type", nullable = false, length = 100)
    private String objectType;

    @Column(name = "object_id", length = 100)
    private String objectId;

    @Column(nullable = false, length = 20)
    private String result;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "trace_id", length = 100)
    private String traceId;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        occurredAt = Instant.now();
    }

    protected AuditLog() {
    }

    public AuditLog(User actor, String action, String objectType, String objectId, String result) {
        this.actor = actor;
        this.action = action;
        this.objectType = objectType;
        this.objectId = objectId;
        this.result = result;
    }

    public User getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getObjectType() {
        return objectType;
    }

    public String getObjectId() {
        return objectId;
    }

    public String getResult() {
        return result;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
