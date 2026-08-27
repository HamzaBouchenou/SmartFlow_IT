package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
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
 * Grants a Role to a User, limited to a perimeter (§5.1 - "Les droits seront attribués par
 * rôle et, lorsque nécessaire, limités à un service ou une direction"). A user can hold
 * several assignments (e.g. Agent on one team, Manager on another).
 *
 * scopeId is interpreted according to scopeType: null for OWN and GLOBAL, a Team id for
 * TEAM, a Department id for DEPARTMENT and DIRECTION.
 */
@Entity
@Table(name = "user_role_assignments")
public class UserRoleAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false, length = 16)
    private ScopeType scopeType;

    @Column(name = "scope_id")
    private Long scopeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    protected UserRoleAssignment() {
    }

    public UserRoleAssignment(User user, Role role, ScopeType scopeType, Long scopeId) {
        this.user = user;
        this.role = role;
        this.scopeType = scopeType;
        this.scopeId = scopeId;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public ScopeType getScopeType() {
        return scopeType;
    }

    public void setScopeType(ScopeType scopeType) {
        this.scopeType = scopeType;
    }

    public Long getScopeId() {
        return scopeId;
    }

    public void setScopeId(Long scopeId) {
        this.scopeId = scopeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
