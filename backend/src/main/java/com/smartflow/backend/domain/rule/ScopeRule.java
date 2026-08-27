package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.ScopeType;

import java.util.List;

/**
 * The "perimetreCouvre" clause of canAct (CLAUDE.md, application/security): does a
 * UserRoleAssignment's (scopeType, scopeId) cover a given request? No Spring, no database
 * access - application/security's AuthorizationService resolves the ScopeContext once per
 * decision (walking Department.parent, reading the active TaskAssignment, ...) and this
 * class only compares already-resolved ids, so it stays unit-testable (see ScopeRuleTest).
 *
 * §5.1 defines four non-OWN levels: "limités à un service ou une direction" plus TEAM
 * (§6.6's team work queue) and the unrestricted GLOBAL level administration needs.
 */
public class ScopeRule {

    /**
     * @param actingUserId               the user canAct is deciding for
     * @param requesterId                the request's requester - PROPRE/OWN compares against this
     * @param serviceDepartmentId        the Department that owns the request's RequestType's ServiceCatalog entry ("son service")
     * @param serviceDepartmentAncestorIds the chain of parent Department ids above serviceDepartmentId (closest first) - a DIRECTION is any Department with no parent, reached by walking this chain
     * @param currentStepTeamId          the request's current Step.responsibleTeam id, or null
     * @param assignedTeamId             the request's active TaskAssignment.assignedTeam id, or null
     */
    public record ScopeContext(Long actingUserId, Long requesterId, Long serviceDepartmentId,
                                List<Long> serviceDepartmentAncestorIds, Long currentStepTeamId, Long assignedTeamId) {
    }

    public boolean covers(ScopeType scopeType, Long scopeId, ScopeContext ctx) {
        return switch (scopeType) {
            case OWN -> ctx.requesterId() != null && ctx.requesterId().equals(ctx.actingUserId());
            case TEAM -> scopeId != null && (scopeId.equals(ctx.currentStepTeamId()) || scopeId.equals(ctx.assignedTeamId()));
            case DEPARTMENT -> scopeId != null && scopeId.equals(ctx.serviceDepartmentId());
            case DIRECTION -> scopeId != null && ctx.serviceDepartmentAncestorIds().contains(scopeId);
            case GLOBAL -> true;
        };
    }
}
