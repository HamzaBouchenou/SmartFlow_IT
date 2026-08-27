package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.WorkflowAction;

import java.util.EnumSet;
import java.util.Set;

/**
 * The last clause of canAct (CLAUDE.md): "NON (separationDesTaches ET demandeur =
 * utilisateur ET action ∈ {VALIDER, REJETER})". §5.1 - "Un utilisateur ne doit pas valider
 * sa propre demande lorsque la règle de séparation des tâches est activée." Whether the
 * rule is currently enabled is an administrable SystemParameter (§6.10), read by
 * application/security's AuthorizationService and passed in here - this class only
 * evaluates the boolean expression, so it needs neither Spring nor a database (see
 * SeparationOfDutiesRuleTest).
 */
public class SeparationOfDutiesRule {

    private static final Set<WorkflowAction> SELF_SERVICE_ACTIONS = EnumSet.of(WorkflowAction.VALIDATE, WorkflowAction.REJECT);

    public boolean blocks(boolean separationOfDutiesEnabled, Long requesterId, Long actingUserId, WorkflowAction action) {
        return separationOfDutiesEnabled
                && requesterId != null
                && requesterId.equals(actingUserId)
                && SELF_SERVICE_ACTIONS.contains(action);
    }
}
