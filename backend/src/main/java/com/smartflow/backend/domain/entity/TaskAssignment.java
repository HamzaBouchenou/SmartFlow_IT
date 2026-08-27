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
 * Who currently owns a Request (§6.6 - "File personnelle « Mes tâches » et file
 * d'équipe"). Only the active row per Request matters for "Mes tâches"; the full
 * reassignment trail is already captured by RequestHistory (action = ASSIGN), so this
 * table is not itself an append-only log.
 */
@Entity
@Table(name = "task_assignments")
public class TaskAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_team_id")
    private Team assignedTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assigned_by_id", nullable = false)
    private User assignedBy;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @PrePersist
    protected void onCreate() {
        assignedAt = Instant.now();
    }

    protected TaskAssignment() {
    }

    public TaskAssignment(Request request, User assignedUser, Team assignedTeam, User assignedBy) {
        this.request = request;
        this.assignedUser = assignedUser;
        this.assignedTeam = assignedTeam;
        this.assignedBy = assignedBy;
    }

    public Request getRequest() {
        return request;
    }

    public User getAssignedUser() {
        return assignedUser;
    }

    public Team getAssignedTeam() {
        return assignedTeam;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
