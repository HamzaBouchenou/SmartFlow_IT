package com.smartflow.backend.domain.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-23/ADR-14 - SUBMIT et REOPEN écrivent une ligne d'historique sans être un arc du
 * graphe de workflow. Cette distinction est lue par WorkflowAdminService (qui refuse de les
 * câbler comme Transition) : elle mérite d'être fixée par un test plutôt que de reposer sur
 * la lecture d'un commentaire.
 */
class WorkflowActionTest {

    @ParameterizedTest
    @EnumSource(value = WorkflowAction.class, names = {"SUBMIT", "REOPEN"})
    @DisplayName("ADR-23/ADR-14 - les actions exécutées directement par l'application ne sont pas configurables")
    void applicationExecutedActionsAreNotConfigurable(WorkflowAction action) {
        assertThat(action.isConfigurable()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = WorkflowAction.class, names = {"SUBMIT", "REOPEN"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("§6.5 - les six actions que le CDC énumère restent câblables sur une étape")
    void workflowActionsRemainConfigurable(WorkflowAction action) {
        assertThat(action.isConfigurable()).isTrue();
    }

    @Test
    @DisplayName("§6.5 - l'énumération porte exactement les six actions du CDC plus les deux exceptions documentées")
    void enumerationHoldsOnlyTheDocumentedValues() {
        assertThat(WorkflowAction.values()).containsExactlyInAnyOrder(
                WorkflowAction.SUBMIT, WorkflowAction.VALIDATE, WorkflowAction.REJECT, WorkflowAction.RETURN,
                WorkflowAction.ASSIGN, WorkflowAction.REQUEST_INFO, WorkflowAction.CLOSE, WorkflowAction.REOPEN);
    }
}
