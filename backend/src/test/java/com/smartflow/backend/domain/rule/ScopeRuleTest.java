package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.ScopeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests ScopeRule.covers - the "perimetreCouvre" clause of canAct (CLAUDE.md,
 * application/security), one case per ScopeType level: PROPRE (OWN), EQUIPE (TEAM),
 * SERVICE (DEPARTMENT), DIRECTION, GLOBAL.
 */
class ScopeRuleTest {

    private final ScopeRule rule = new ScopeRule();

    @Test
    @DisplayName("OWN covers only when the acting user is the requester, regardless of scopeId")
    void ownCoversOnlyTheRequester() {
        ScopeRule.ScopeContext ctx = new ScopeRule.ScopeContext(1L, 1L, 10L, List.of(), null, null);
        ScopeRule.ScopeContext ctxOther = new ScopeRule.ScopeContext(2L, 1L, 10L, List.of(), null, null);

        assertThat(rule.covers(ScopeType.OWN, null, ctx)).isTrue();
        assertThat(rule.covers(ScopeType.OWN, null, ctxOther)).isFalse();
    }

    @Test
    @DisplayName("TEAM covers when scopeId matches the current step's responsible team")
    void teamCoversViaCurrentStepTeam() {
        ScopeRule.ScopeContext ctx = new ScopeRule.ScopeContext(2L, 1L, 10L, List.of(), 55L, null);

        assertThat(rule.covers(ScopeType.TEAM, 55L, ctx)).isTrue();
        assertThat(rule.covers(ScopeType.TEAM, 99L, ctx)).isFalse();
    }

    @Test
    @DisplayName("TEAM also covers when scopeId matches the active task assignment's team, even if the step's own team differs")
    void teamCoversViaActiveAssignment() {
        ScopeRule.ScopeContext ctx = new ScopeRule.ScopeContext(2L, 1L, 10L, List.of(), 55L, 77L);

        assertThat(rule.covers(ScopeType.TEAM, 77L, ctx)).isTrue();
    }

    @Test
    @DisplayName("DEPARTMENT (service) covers when scopeId matches the service owning the request's request type")
    void departmentCoversTheOwningService() {
        ScopeRule.ScopeContext ctx = new ScopeRule.ScopeContext(2L, 1L, 10L, List.of(3L), null, null);

        assertThat(rule.covers(ScopeType.DEPARTMENT, 10L, ctx)).isTrue();
        assertThat(rule.covers(ScopeType.DEPARTMENT, 3L, ctx))
                .as("a DIRECTION-level ancestor id must not satisfy a DEPARTMENT scope")
                .isFalse();
    }

    @Test
    @DisplayName("DIRECTION covers when scopeId is anywhere in the service's ancestor chain")
    void directionCoversAnAncestor() {
        ScopeRule.ScopeContext ctx = new ScopeRule.ScopeContext(2L, 1L, 10L, List.of(3L, 1L), null, null);

        assertThat(rule.covers(ScopeType.DIRECTION, 3L, ctx)).isTrue();
        assertThat(rule.covers(ScopeType.DIRECTION, 1L, ctx)).isTrue();
        assertThat(rule.covers(ScopeType.DIRECTION, 999L, ctx)).isFalse();
    }

    @Test
    @DisplayName("GLOBAL always covers, whatever the request or the acting user")
    void globalAlwaysCovers() {
        ScopeRule.ScopeContext ctx = new ScopeRule.ScopeContext(2L, 1L, 10L, List.of(), null, null);

        assertThat(rule.covers(ScopeType.GLOBAL, null, ctx)).isTrue();
    }
}
