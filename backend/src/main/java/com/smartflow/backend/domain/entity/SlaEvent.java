package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.SlaEventType;
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
 * Event-sourced SLA timeline for a Request (RG-07 - "Modèle événementiel, pas de compteur
 * mutable"). This table is authoritative: Request.slaDueAtFirstResponse,
 * slaDueAtResolution, slaStatus, slaSuspendedSince and slaSuspendedMinutes are a
 * materialized read model recomputed from these rows by the scheduled SLA sweep, never the
 * other way round (see Request's class javadoc for the full rule).
 */
@Entity
@Table(name = "sla_events")
public class SlaEvent extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private SlaEventType eventType;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column
    private String note;

    @PrePersist
    protected void onCreate() {
        occurredAt = Instant.now();
    }

    protected SlaEvent() {
    }

    public SlaEvent(Request request, SlaEventType eventType) {
        this.request = request;
        this.eventType = eventType;
    }

    public Request getRequest() {
        return request;
    }

    public SlaEventType getEventType() {
        return eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
