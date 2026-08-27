package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.WorkflowAction;

import java.util.List;

/**
 * The "etapeAutoriseAction" clause of canAct (CLAUDE.md): an action is only ever available
 * if the request's current Step actually offers it as an outgoing Transition (§6.5). No
 * Spring, no database access - application/security's AuthorizationService loads the
 * Transitions for the current step and extracts their actions before calling this.
 *
 * Deliberately narrow for now: it only checks that the action exists among the step's
 * transitions, not a Transition's simple conditions (condition_priority,
 * condition_department_id, condition_field_code/value, §6.5) - those select which target
 * step a transition leads to once the action has been decided legal, which is the future
 * "execute workflow transition" use case's job, not canAct's. canAct only needs to know
 * whether the button may be offered at all.
 */
public class WorkflowActionAvailabilityRule {

    public boolean isAvailable(WorkflowAction action, List<WorkflowAction> actionsFromCurrentStep) {
        return actionsFromCurrentStep.contains(action);
    }
}
