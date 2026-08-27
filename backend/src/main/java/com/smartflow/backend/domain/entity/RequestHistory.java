package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.WorkflowAction;
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
 * One row per workflow transition actually executed on a Request. RG-04: every decision is
 * tied to an authorized user and timestamped. RG-05: comment is mandatory when
 * action = REJECT - enforced in the service layer / domain rule, not by a blanket NOT NULL
 * here, since it is only required for that one action. §3.4 target: 100% of state changes
 * traced, so every Transition executed must produce exactly one row here, never zero, never
 * more than one.
 */
@Entity
@Table(name = "request_history")
public class RequestHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_step_id")
    private Step fromStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkflowAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_step_id")
    private Step toStep;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Column(columnDefinition = "text")
    private String comment;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @PrePersist
    protected void onCreate() {
        occurredAt = Instant.now();
    }

    protected RequestHistory() {
    }

    public RequestHistory(Request request, Step fromStep, WorkflowAction action, Step toStep, User actor) {
        this.request = request;
        this.fromStep = fromStep;
        this.action = action;
        this.toStep = toStep;
        this.actor = actor;
    }

    public Request getRequest() {
        return request;
    }

    public Step getFromStep() {
        return fromStep;
    }

    public WorkflowAction getAction() {
        return action;
    }

    public Step getToStep() {
        return toStep;
    }

    public User getActor() {
        return actor;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
