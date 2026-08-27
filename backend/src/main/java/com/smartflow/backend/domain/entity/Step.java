package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * An ordered step of a WorkflowDefinition, with a responsible role or team (§6.5 -
 * "Définition d'étapes ordonnées avec un rôle ou une équipe responsable").
 */
@Entity
@Table(name = "steps")
public class Step extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(nullable = false, length = 100)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "responsible_role", length = 32)
    private Role responsibleRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_team_id")
    private Team responsibleTeam;

    // RG-07 - "le compteur SLA... peut être suspendu uniquement par un statut prévu dans
    // la configuration". Le workflow reste configurable par type de demande (§6.5) : c'est
    // donc cette marque sur l'étape, pas un statut ou un code d'étape codé en dur, qui
    // désigne un point de suspension légitime (ex. une étape d'attente de complément après
    // REQUEST_INFO). domain/rule s'appuiera dessus pour émettre SUSPENDED/RESUMED en
    // entrant ou sortant d'une telle étape.
    @Column(name = "suspend_sla", nullable = false)
    private boolean suspendSla;

    protected Step() {
    }

    public Step(WorkflowDefinition workflowDefinition, String code, String name) {
        this.workflowDefinition = workflowDefinition;
        this.code = code;
        this.name = name;
    }

    public WorkflowDefinition getWorkflowDefinition() {
        return workflowDefinition;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Role getResponsibleRole() {
        return responsibleRole;
    }

    public void setResponsibleRole(Role responsibleRole) {
        this.responsibleRole = responsibleRole;
    }

    public Team getResponsibleTeam() {
        return responsibleTeam;
    }

    public void setResponsibleTeam(Team responsibleTeam) {
        this.responsibleTeam = responsibleTeam;
    }

    public boolean isSuspendSla() {
        return suspendSla;
    }

    public void setSuspendSla(boolean suspendSla) {
        this.suspendSla = suspendSla;
    }
}
