package com.smartflow.backend.domain.entity;

import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.WorkflowAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A legal move from one Step to another through an action, with simple conditions (§6.5 -
 * "Conditions simples basées sur la catégorie, la priorité, le service ou une valeur du
 * formulaire"). Legality checking itself belongs in domain/rule as a pure function reading
 * this table, not here. toStep is nullable: CLOSE is a terminal action with no target step.
 */
@Entity
@Table(name = "transitions")
public class Transition extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_step_id", nullable = false)
    private Step fromStep;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WorkflowAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_step_id")
    private Step toStep;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_priority", length = 16)
    private Priority conditionPriority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_department_id")
    private Department conditionDepartment;

    @Column(name = "condition_field_code", length = 100)
    private String conditionFieldCode;

    @Column(name = "condition_field_value")
    private String conditionFieldValue;

    protected Transition() {
    }

    public Transition(Step fromStep, WorkflowAction action, Step toStep) {
        this.fromStep = fromStep;
        this.action = action;
        this.toStep = toStep;
    }

    public Step getFromStep() {
        return fromStep;
    }

    public WorkflowAction getAction() {
        return action;
    }

    public void setAction(WorkflowAction action) {
        this.action = action;
    }

    public Step getToStep() {
        return toStep;
    }

    public void setToStep(Step toStep) {
        this.toStep = toStep;
    }

    public Priority getConditionPriority() {
        return conditionPriority;
    }

    public void setConditionPriority(Priority conditionPriority) {
        this.conditionPriority = conditionPriority;
    }

    public Department getConditionDepartment() {
        return conditionDepartment;
    }

    public void setConditionDepartment(Department conditionDepartment) {
        this.conditionDepartment = conditionDepartment;
    }

    public String getConditionFieldCode() {
        return conditionFieldCode;
    }

    public void setConditionFieldCode(String conditionFieldCode) {
        this.conditionFieldCode = conditionFieldCode;
    }

    public String getConditionFieldValue() {
        return conditionFieldValue;
    }

    public void setConditionFieldValue(String conditionFieldValue) {
        this.conditionFieldValue = conditionFieldValue;
    }
}
