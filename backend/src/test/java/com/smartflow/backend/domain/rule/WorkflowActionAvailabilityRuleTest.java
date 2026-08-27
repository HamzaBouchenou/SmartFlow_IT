package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.WorkflowAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests WorkflowActionAvailabilityRule.isAvailable - the "etapeAutoriseAction" clause of
 * canAct (CLAUDE.md): an action canAct is asked about must actually be a legal outgoing
 * transition from the request's current step (§6.5), not just permitted by role and scope.
 */
class WorkflowActionAvailabilityRuleTest {

    private final WorkflowActionAvailabilityRule rule = new WorkflowActionAvailabilityRule();

    @Test
    @DisplayName("is available when at least one Transition out of the current step carries this action")
    void availableWhenListed() {
        assertThat(rule.isAvailable(WorkflowAction.VALIDATE, List.of(WorkflowAction.VALIDATE, WorkflowAction.REJECT))).isTrue();
    }

    @Test
    @DisplayName("is not available when no Transition out of the current step carries this action")
    void unavailableWhenNotListed() {
        assertThat(rule.isAvailable(WorkflowAction.CLOSE, List.of(WorkflowAction.VALIDATE, WorkflowAction.REJECT))).isFalse();
    }

    @Test
    @DisplayName("is not available from a step with no outgoing transitions at all (e.g. request not yet submitted)")
    void unavailableWithNoTransitions() {
        assertThat(rule.isAvailable(WorkflowAction.VALIDATE, List.of())).isFalse();
    }
}
