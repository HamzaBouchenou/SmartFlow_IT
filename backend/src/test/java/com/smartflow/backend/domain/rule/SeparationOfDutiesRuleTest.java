package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.WorkflowAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests SeparationOfDutiesRule.blocks - the last clause of canAct: "NON (separationDesTaches
 * ET demandeur = utilisateur ET action ∈ {VALIDER, REJETER})" (CLAUDE.md).
 *
 * §5.1 - "Un utilisateur ne doit pas valider sa propre demande lorsque la règle de
 * séparation des tâches est activée." blocks() returns true exactly when canAct must
 * refuse regardless of what role/scope/step would otherwise allow.
 */
class SeparationOfDutiesRuleTest {

    private final SeparationOfDutiesRule rule = new SeparationOfDutiesRule();

    @Test
    @DisplayName("blocks a requester validating their own request while the rule is enabled")
    void blocksSelfValidationWhenEnabled() {
        assertThat(rule.blocks(true, 1L, 1L, WorkflowAction.VALIDATE)).isTrue();
    }

    @Test
    @DisplayName("blocks a requester rejecting their own request while the rule is enabled")
    void blocksSelfRejectionWhenEnabled() {
        assertThat(rule.blocks(true, 1L, 1L, WorkflowAction.REJECT)).isTrue();
    }

    @Test
    @DisplayName("does not block when the rule is disabled, even for self-validation")
    void doesNotBlockWhenDisabled() {
        assertThat(rule.blocks(false, 1L, 1L, WorkflowAction.VALIDATE)).isFalse();
    }

    @Test
    @DisplayName("does not block when the acting user is not the requester")
    void doesNotBlockWhenActingUserIsNotRequester() {
        assertThat(rule.blocks(true, 1L, 2L, WorkflowAction.VALIDATE)).isFalse();
    }

    @Test
    @DisplayName("does not block actions outside {VALIDATE, REJECT}, even for self-service on your own request")
    void doesNotBlockOtherActions() {
        assertThat(rule.blocks(true, 1L, 1L, WorkflowAction.RETURN)).isFalse();
        assertThat(rule.blocks(true, 1L, 1L, WorkflowAction.ASSIGN)).isFalse();
        assertThat(rule.blocks(true, 1L, 1L, WorkflowAction.REQUEST_INFO)).isFalse();
        assertThat(rule.blocks(true, 1L, 1L, WorkflowAction.CLOSE)).isFalse();
    }

    @Test
    @DisplayName("does not block when requesterId is null (defensive - should not happen for a submitted request)")
    void doesNotBlockWhenRequesterIdIsNull() {
        assertThat(rule.blocks(true, null, 1L, WorkflowAction.VALIDATE)).isFalse();
    }
}
