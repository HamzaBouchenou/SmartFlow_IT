package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.WorkflowAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests RolePermissionRule.grants - the "roleAccordePermission" clause of canAct
 * (CLAUDE.md, application/security).
 *
 * The matrix below is read directly off §5's acteurs table: only the actions a role's
 * "Responsabilités principales" cell actually lists are granted for that role.
 */
class RolePermissionRuleTest {

    private final RolePermissionRule rule = new RolePermissionRule();

    @Test
    @DisplayName("§5 - Manager/valideur: accepter, rejeter ou retourner une demande")
    void managerGrantsValidateRejectAndReturn() {
        assertThat(rule.grants(Role.MANAGER, WorkflowAction.VALIDATE)).isTrue();
        assertThat(rule.grants(Role.MANAGER, WorkflowAction.REJECT)).isTrue();
        assertThat(rule.grants(Role.MANAGER, WorkflowAction.RETURN)).isTrue();
        assertThat(rule.grants(Role.MANAGER, WorkflowAction.ASSIGN)).isFalse();
        assertThat(rule.grants(Role.MANAGER, WorkflowAction.CLOSE)).isFalse();
    }

    @Test
    @DisplayName("§5 - Agent de traitement: prendre en charge (ASSIGN), demander un complément et résoudre (CLOSE)")
    void agentGrantsAssignRequestInfoAndClose() {
        assertThat(rule.grants(Role.AGENT, WorkflowAction.ASSIGN)).isTrue();
        assertThat(rule.grants(Role.AGENT, WorkflowAction.REQUEST_INFO)).isTrue();
        assertThat(rule.grants(Role.AGENT, WorkflowAction.CLOSE)).isTrue();
        assertThat(rule.grants(Role.AGENT, WorkflowAction.VALIDATE)).isFalse();
        assertThat(rule.grants(Role.AGENT, WorkflowAction.REJECT)).isFalse();
    }

    @Test
    @DisplayName("ADR-14/RG-08 - REOPEN mirrors CLOSE eligibility: AGENT and SERVICE_MANAGER, never MANAGER")
    void reopenMirrorsCloseEligibility() {
        assertThat(rule.grants(Role.AGENT, WorkflowAction.REOPEN)).isTrue();
        assertThat(rule.grants(Role.SERVICE_MANAGER, WorkflowAction.REOPEN)).isTrue();
        assertThat(rule.grants(Role.MANAGER, WorkflowAction.REOPEN)).isFalse();
    }

    @Test
    @DisplayName("§5 - Responsable de service pilote toutes les demandes de son service: toutes les actions de workflow sauf SUBMIT")
    void serviceManagerGrantsEveryWorkflowActionButSubmit() {
        for (WorkflowAction action : WorkflowAction.values()) {
            if (action == WorkflowAction.SUBMIT) {
                continue;
            }
            assertThat(rule.grants(Role.SERVICE_MANAGER, action))
                    .as("SERVICE_MANAGER should be granted %s", action)
                    .isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("ADR-23 - SUBMIT ne passe pas par canAct: aucun rôle ne se la voit accorder")
    void submitIsGrantedToNoRole(Role role) {
        assertThat(rule.grants(role, WorkflowAction.SUBMIT)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(WorkflowAction.class)
    @DisplayName("§5 - le demandeur ne figure dans aucune cellule d'action de workflow: aucune action de workflow ne lui est accordée")
    void requesterGrantsNoWorkflowAction(WorkflowAction action) {
        assertThat(rule.grants(Role.REQUESTER, action)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(WorkflowAction.class)
    @DisplayName("§5 - Auditeur: lecture seule sur son périmètre - aucune action de workflow")
    void auditorGrantsNoWorkflowAction(WorkflowAction action) {
        assertThat(rule.grants(Role.AUDITOR, action)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"FUNCTIONAL_ADMIN", "TECHNICAL_ADMIN"})
    @DisplayName("§5 - les administrateurs fonctionnel/technique agissent sur le paramétrage, pas sur le workflow d'une demande")
    void adminRolesGrantNoWorkflowAction(Role role) {
        for (WorkflowAction action : WorkflowAction.values()) {
            assertThat(rule.grants(role, action)).isFalse();
        }
    }
}
