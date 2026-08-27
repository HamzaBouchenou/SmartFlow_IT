package com.smartflow.backend.application.security;

import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.Transition;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.rule.RolePermissionRule;
import com.smartflow.backend.domain.rule.ScopeRule;
import com.smartflow.backend.domain.rule.SeparationOfDutiesRule;
import com.smartflow.backend.domain.rule.WorkflowActionAvailabilityRule;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.TransitionRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * The single canAct decision function CLAUDE.md requires ("Une seule fonction de décision,
 * dans application/security, appelée par chaque cas d'usage") - no use case may reimplement
 * or shortcut any part of this, and none may fall back to a scattered @PreAuthorize.
 *
 * Implements exactly CLAUDE.md's formula:
 * <pre>
 * canAct(utilisateur, demande, action) =
 *         roleAccordePermission(...)
 *     ET  perimetreCouvre(...)              PROPRE | EQUIPE | SERVICE | DIRECTION | GLOBAL
 *     ET  etapeAutoriseAction(...)
 *     ET  NON (separationDesTaches ET demandeur = utilisateur ET action ∈ {VALIDER, REJETER})
 * </pre>
 * Each clause is a separately unit-tested pure rule in domain/rule (RolePermissionRule,
 * ScopeRule, WorkflowActionAvailabilityRule, SeparationOfDutiesRule); this service's own
 * job is only to resolve the ids and lists those rules need from the persisted graph
 * (a user's role assignments, the current step's legal actions, the department ancestor
 * chain, the active task assignment) and combine the results.
 *
 * canAct returns a plain boolean, never throws. CLAUDE.md - "404 et non 403 pour une
 * ressource hors périmètre" : a caller that gets false must translate that into
 * EntityNotFoundException (domain/exception), exactly as it would for a request that does
 * not exist, never into a 403 - a 403 would confirm the resource's existence to a caller
 * outside its perimeter.
 */
@Service
public class AuthorizationService {

    /**
     * §5.1 - "Un utilisateur ne doit pas valider sa propre demande lorsque la règle de
     * séparation des tâches est activée" : the rule is administrable (§6.10), stored as a
     * SystemParameter. Absent (unconfigured) defaults to enabled - the secure default.
     */
    public static final String SEPARATION_OF_DUTIES_KEY = "security.separation-of-duties.enabled";

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final TransitionRepository transitionRepository;
    private final SystemParameterRepository systemParameterRepository;
    private final RolePermissionRule rolePermissionRule;
    private final ScopeRule scopeRule;
    private final WorkflowActionAvailabilityRule workflowActionAvailabilityRule;
    private final SeparationOfDutiesRule separationOfDutiesRule;

    public AuthorizationService(UserRoleAssignmentRepository userRoleAssignmentRepository,
                                 TaskAssignmentRepository taskAssignmentRepository,
                                 TransitionRepository transitionRepository,
                                 SystemParameterRepository systemParameterRepository,
                                 RolePermissionRule rolePermissionRule,
                                 ScopeRule scopeRule,
                                 WorkflowActionAvailabilityRule workflowActionAvailabilityRule,
                                 SeparationOfDutiesRule separationOfDutiesRule) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.transitionRepository = transitionRepository;
        this.systemParameterRepository = systemParameterRepository;
        this.rolePermissionRule = rolePermissionRule;
        this.scopeRule = scopeRule;
        this.workflowActionAvailabilityRule = workflowActionAvailabilityRule;
        this.separationOfDutiesRule = separationOfDutiesRule;
    }

    @Transactional(readOnly = true)
    public boolean canAct(User actingUser, Request request, WorkflowAction action) {
        // étapeAutoriseAction first: cheapest check, and it does not depend on who is
        // asking - failing it makes the scope/role resolution below pointless.
        if (!workflowActionAvailabilityRule.isAvailable(action, availableActionsFromCurrentStep(request))) {
            return false;
        }

        ScopeRule.ScopeContext context = resolveScopeContext(actingUser, request);
        boolean granted = userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> rolePermissionRule.grants(assignment.getRole(), action)
                        && scopeRule.covers(assignment.getScopeType(), assignment.getScopeId(), context));
        if (!granted) {
            return false;
        }

        Long requesterId = request.getRequester().getId();
        return !separationOfDutiesRule.blocks(isSeparationOfDutiesEnabled(), requesterId, actingUser.getId(), action);
    }

    private List<WorkflowAction> availableActionsFromCurrentStep(Request request) {
        Step currentStep = request.getCurrentStep();
        if (currentStep == null) {
            // Not yet submitted (or defensively stepless) - no workflow action is legal.
            return List.of();
        }
        return transitionRepository.findByFromStepId(currentStep.getId()).stream()
                .map(Transition::getAction)
                .toList();
    }

    private ScopeRule.ScopeContext resolveScopeContext(User actingUser, Request request) {
        Department serviceDepartment = request.getRequestType().getServiceCatalog().getDepartment();
        Step currentStep = request.getCurrentStep();
        Long currentStepTeamId = currentStep != null && currentStep.getResponsibleTeam() != null
                ? currentStep.getResponsibleTeam().getId() : null;
        Long assignedTeamId = taskAssignmentRepository.findByRequestIdAndActiveTrue(request.getId())
                .map(TaskAssignment::getAssignedTeam)
                .map(Team::getId)
                .orElse(null);

        return new ScopeRule.ScopeContext(actingUser.getId(), request.getRequester().getId(),
                serviceDepartment.getId(), ancestorIdsOf(serviceDepartment), currentStepTeamId, assignedTeamId);
    }

    /** Walks Department.parent up to the DIRECTION (parent == null) - see Department's class javadoc. */
    private List<Long> ancestorIdsOf(Department department) {
        List<Long> ancestorIds = new ArrayList<>();
        Department current = department.getParent();
        // §4.1 organisation is direction -> service, two levels in practice; the bound is
        // only a defensive guard against a misconfigured parent cycle, not an expected depth.
        int guard = 0;
        while (current != null && guard++ < 10) {
            ancestorIds.add(current.getId());
            current = current.getParent();
        }
        return ancestorIds;
    }

    private boolean isSeparationOfDutiesEnabled() {
        return systemParameterRepository.findByKey(SEPARATION_OF_DUTIES_KEY)
                .map(parameter -> Boolean.parseBoolean(parameter.getValue()))
                .orElse(true);
    }
}
