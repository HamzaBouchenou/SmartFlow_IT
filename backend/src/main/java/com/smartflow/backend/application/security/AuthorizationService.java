package com.smartflow.backend.application.security;

import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestHistory;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.Transition;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.rule.RolePermissionRule;
import com.smartflow.backend.domain.rule.ScopeRule;
import com.smartflow.backend.domain.rule.SeparationOfDutiesRule;
import com.smartflow.backend.domain.rule.WorkflowActionAvailabilityRule;
import com.smartflow.backend.infrastructure.repository.RequestHistoryRepository;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.TransitionRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
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
    private final RequestHistoryRepository requestHistoryRepository;
    private final SystemParameterRepository systemParameterRepository;
    private final RolePermissionRule rolePermissionRule;
    private final ScopeRule scopeRule;
    private final WorkflowActionAvailabilityRule workflowActionAvailabilityRule;
    private final SeparationOfDutiesRule separationOfDutiesRule;

    public AuthorizationService(UserRoleAssignmentRepository userRoleAssignmentRepository,
                                 TaskAssignmentRepository taskAssignmentRepository,
                                 TransitionRepository transitionRepository,
                                 RequestHistoryRepository requestHistoryRepository,
                                 SystemParameterRepository systemParameterRepository,
                                 RolePermissionRule rolePermissionRule,
                                 ScopeRule scopeRule,
                                 WorkflowActionAvailabilityRule workflowActionAvailabilityRule,
                                 SeparationOfDutiesRule separationOfDutiesRule) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.transitionRepository = transitionRepository;
        this.requestHistoryRepository = requestHistoryRepository;
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

    /**
     * ADR-10 (docs/DECISIONS.md) - RG-06's own carve-out ("sauf rôle complémentaire prévu
     * par l'organisation") for READING a request outside its own requester. Distinct from
     * canAct : it reuses only perimetreCouvre (ScopeRule), never roleAccordePermission,
     * etapeAutoriseAction nor separationDesTaches, which govern the legality of an ACTION,
     * not the visibility of a record - AUDITOR, for instance, grants zero WorkflowAction in
     * RolePermissionRule yet must see everything its GLOBAL scope covers (§5 - "lecture
     * seule sur périmètre autorisé").
     *
     * A DRAFT request is never visible through this path, regardless of scope, checked here
     * rather than trusted to callers : DEPARTMENT/DIRECTION scope resolves from the
     * request's service alone (set at draft creation, before any Step or TaskAssignment
     * exists), so without this guard a service manager would see a colleague's unsubmitted
     * brouillon, which no chapter of the CDC asks for. This method is never called by
     * RequestService.getOwned/getOwnedDraft (updateDraft/submit/cancel stay strictly
     * requester-only, unaffected by this method) - only by getDetail's read path.
     */
    @Transactional(readOnly = true)
    public boolean canView(User actingUser, Request request) {
        if (request.getStatus() == RequestStatus.DRAFT) {
            return false;
        }
        ScopeRule.ScopeContext context = resolveScopeContext(actingUser, request);
        return userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> scopeRule.covers(assignment.getScopeType(), assignment.getScopeId(), context));
    }

    /**
     * ADR-11 (docs/DECISIONS.md) - can actingUser add a comment or attachment to request?
     * The requester always can, at any status including DRAFT (they manage their own
     * dossier). Anyone else follows canView (§9.4 - comments/attachments share the request
     * detail screen's read access) except AUDITOR, whom §5 restricts to reading "sans
     * modifier les données" - annotating is a modification, so AUDITOR is excluded here even
     * though canView alone would admit it.
     */
    @Transactional(readOnly = true)
    public boolean canAnnotate(User actingUser, Request request) {
        if (request.getRequester().getId().equals(actingUser.getId())) {
            return true;
        }
        if (request.getStatus() == RequestStatus.DRAFT) {
            return false;
        }
        ScopeRule.ScopeContext context = resolveScopeContext(actingUser, request);
        return userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> assignment.getRole() != Role.AUDITOR
                        && scopeRule.covers(assignment.getScopeType(), assignment.getScopeId(), context));
    }

    /**
     * §5/RG-07 - "qualifier" une demande soumise : poser sa Priority. RolePermissionRule's
     * own comment already flags "qualifier" as one of the AGENT's four responsibilities that
     * is not itself a WorkflowAction (only "prendre en charge"/ASSIGN and "résoudre"/CLOSE
     * are), so it needs its own decision here rather than a fifth WorkflowAction with no
     * Transition to ever resolve it. Reuses exactly the eligibility canAct already computes
     * for this request's current step: whoever could legally perform at least one
     * WorkflowAction here is exactly who should be trusted to set its priority - a request
     * with no current step (DRAFT/CLOSED/CANCELLED/ARCHIVED) has nobody canAct would ever
     * admit anyway, so this naturally also gates qualify() to a SUBMITTED, in-flight request
     * without a separate status check. Also the single point of truth
     * WorkflowTransitionService.assign/pickAutoAssignedUserId reuse for "is this candidate a
     * legitimate target of ASSIGN" (ADR-18) - one definition of "eligible on this request",
     * never two that could drift apart.
     */
    @Transactional(readOnly = true)
    public boolean canQualify(User actingUser, Request request) {
        return Arrays.stream(WorkflowAction.values()).anyMatch(action -> canAct(actingUser, request, action));
    }

    /**
     * §6.7 - "escalade au responsable en cas de dépassement" : every user holding a
     * SERVICE_MANAGER assignment whose scope covers this request's department - the same
     * "piloter la charge, les délais et les règles de son service" responsibility §5 gives
     * this role, applied to perimetreCouvre exactly as canAct/canView already do. Reuses
     * ScopeRule.covers with a context that only needs the department side (OWN/TEAM never
     * apply to a manager's own escalation scope, so actingUserId/requesterId/team ids are
     * left null on purpose).
     */
    @Transactional(readOnly = true)
    public List<User> findResponsibleManagers(Request request) {
        Department serviceDepartment = request.getRequestType().getServiceCatalog().getDepartment();
        ScopeRule.ScopeContext context = new ScopeRule.ScopeContext(null, null, serviceDepartment.getId(),
                ancestorIdsOf(serviceDepartment), null, null);
        return userRoleAssignmentRepository.findByRole(Role.SERVICE_MANAGER).stream()
                .filter(assignment -> scopeRule.covers(assignment.getScopeType(), assignment.getScopeId(), context))
                .map(UserRoleAssignment::getUser)
                .distinct()
                .toList();
    }

    /**
     * ADR-14 (docs/DECISIONS.md) - RG-08 : qui peut rouvrir une demande clôturée. The
     * status/deadline/reopenAllowed state guards live in RequestService.reopen (they are
     * not a permission question) - this method only decides role+scope, exactly the
     * "roleAccordePermission ET perimetreCouvre" half of canAct's formula, deliberately
     * without etapeAutoriseAction: a CLOSED request has no currentStep to resolve one from
     * (ADR-03), and REOPEN is not itself a Step Transition (WorkflowAction's own javadoc).
     * The requester may always reopen their own request, symmetric with ADR-11's
     * canAnnotate; anyone else needs the same role+scope that would already let them CLOSE
     * this request (RolePermissionRule grants REOPEN exactly where it grants CLOSE).
     *
     * @param stepBeforeClose the Step CLOSE's own RequestHistory row recorded as fromStep -
     *                        TEAM scope resolves against it instead of
     *                        request.getCurrentStep() (always null once CLOSED), so this
     *                        reproduces exactly the perimeter CLOSE itself was legal under.
     */
    @Transactional(readOnly = true)
    public boolean canReopen(User actingUser, Request request, Step stepBeforeClose) {
        if (request.getRequester().getId().equals(actingUser.getId())) {
            return true;
        }
        ScopeRule.ScopeContext context = resolveScopeContext(actingUser, request, stepBeforeClose);
        return userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> rolePermissionRule.grants(assignment.getRole(), WorkflowAction.REOPEN)
                        && scopeRule.covers(assignment.getScopeType(), assignment.getScopeId(), context));
    }

    /**
     * §6.9/§5 - "Responsable de service... Toutes les demandes et indicateurs de son
     * service" : a dashboard is department-level aggregate data, never tied to a single
     * request, so only the three organization-wide perimeters apply - OWN and TEAM
     * (canView/canAct's other two) have no meaning for "indicateurs de son service" and are
     * deliberately excluded here, unlike canView.
     */
    @Transactional(readOnly = true)
    public boolean canViewDashboard(User actingUser, Department department) {
        List<Long> ancestorIds = ancestorIdsOf(department);
        return userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> switch (assignment.getScopeType()) {
                    case DEPARTMENT -> department.getId().equals(assignment.getScopeId());
                    case DIRECTION -> assignment.getScopeId() != null && ancestorIds.contains(assignment.getScopeId());
                    case GLOBAL -> true;
                    case OWN, TEAM -> false;
                });
    }

    /**
     * §13.1/§5 - "Journal d'audit consultable" ; l'"Auditeur / direction" du tableau des
     * acteurs a explicitement "lecture seule sur périmètre autorisé" pour toute la
     * plateforme, et les deux administrateurs doivent pouvoir relire les changements de
     * configuration qu'eux-mêmes ou d'autres administrateurs ont produits (RG-11).
     * Contrairement à canViewDashboard (périmètre organisationnel d'un Department), une
     * ligne d'audit n'a pas de département : c'est l'appartenance au rôle elle-même,
     * quel que soit le périmètre de l'affectation, qui ouvre ce journal - il n'existe pas de
     * lecture partielle "sur son propre service" pour une trace de changement de rôle ou de
     * configuration.
     */
    @Transactional(readOnly = true)
    public boolean canViewAuditLog(User actingUser) {
        return userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> assignment.getRole() == Role.FUNCTIONAL_ADMIN
                        || assignment.getRole() == Role.TECHNICAL_ADMIN
                        || assignment.getRole() == Role.AUDITOR);
    }

    /**
     * §6.10/§5 - "Administrateur fonctionnel... Accès attendu : Paramétrage fonctionnel
     * global." La seule décision derrière chaque écran d'administration fonctionnelle
     * (§6.10 : paramètres généraux, catalogue, formulaires, workflows, SLA, équipes,
     * gabarits d'e-mail) - tous des règles de gestion globales, jamais départementales,
     * gouvernées par le même rôle. Même raisonnement que canViewAuditLog ci-dessus :
     * l'appartenance au rôle suffit, sans notion de périmètre.
     */
    @Transactional(readOnly = true)
    public boolean isFunctionalAdmin(User actingUser) {
        return userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> assignment.getRole() == Role.FUNCTIONAL_ADMIN);
    }

    /**
     * §6.10/§15.3/§5 - "Administrateur technique... Accès attendu : Configuration
     * technique et supervision." La page de diagnostic (état des services techniques) est
     * sa supervision, pas le "paramétrage fonctionnel global" d'isFunctionalAdmin -
     * délibérément un rôle distinct, jamais élargi à FUNCTIONAL_ADMIN.
     */
    @Transactional(readOnly = true)
    public boolean isTechnicalAdmin(User actingUser) {
        return userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> assignment.getRole() == Role.TECHNICAL_ADMIN);
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

    /**
     * ADR-19 (docs/DECISIONS.md) - generalizes ADR-14's fix for canReopen to every other
     * reader of this 1-arg overload (canView, canAnnotate; canAct calls it too, but never
     * actually reaches the fallback below - see teamScopeStepForReading's own javadoc).
     * request.getCurrentStep() is always null once CLOSED (ADR-03), and ScopeRule.TEAM only
     * ever compares against currentStepTeamId/assignedTeamId, neither of which survives for
     * an individually-assigned agent (assignedTeamId only covers an unclaimed team-queue
     * drop - WorkflowTransitionService.assign leaves it null for a named assignee). Without
     * this, the very agent who resolved and closed a request individually could no longer
     * even read it afterwards - found in recette against a real docker stack, not a test.
     */
    private ScopeRule.ScopeContext resolveScopeContext(User actingUser, Request request) {
        return resolveScopeContext(actingUser, request, teamScopeStepForReading(request));
    }

    /**
     * The step whose responsibleTeam should still count for TEAM scope once a request has
     * left the workflow. A live request simply uses its own currentStep. A CLOSED (or later
     * ARCHIVED, which only ever comes from CLOSED/CANCELLED) request has none (ADR-03), so
     * this falls back to the step CLOSE itself left - the same fromStep ADR-14 already
     * passes explicitly for canReopen, generalized here for every other caller of the 1-arg
     * resolveScopeContext. A CANCELLED request needs no such fallback: RequestService.cancel
     * already refuses cancellation the moment any TaskAssignment exists, so a cancelled
     * request never has an individual assignee whose visibility this would need to preserve.
     * canAct itself never reaches this fallback in practice - workflowActionAvailabilityRule
     * already denies every WorkflowAction once currentStep is null, before scope is ever
     * resolved - so this only changes behaviour for canView/canAnnotate.
     */
    private Step teamScopeStepForReading(Request request) {
        if (request.getCurrentStep() != null) {
            return request.getCurrentStep();
        }
        if (request.getStatus() == RequestStatus.CLOSED || request.getStatus() == RequestStatus.ARCHIVED) {
            return requestHistoryRepository.findFirstByRequestIdAndActionOrderByOccurredAtDesc(request.getId(), WorkflowAction.CLOSE)
                    .map(RequestHistory::getFromStep)
                    .orElse(null);
        }
        return null;
    }

    /**
     * ADR-14 - canReopen passes the Step CLOSE left from (request.getCurrentStep() is
     * always null once CLOSED, ADR-03) instead of the (absent) current one, so TEAM scope
     * still resolves via that step's responsibleTeam exactly as it did when CLOSE itself
     * was legal - "le même périmètre que CLOSE" (ADR-14) would otherwise be unreachable for
     * a TEAM-scoped agent whose active TaskAssignment is to them individually, not their
     * team (assignedTeamId only covers an unclaimed team-queue drop, never an individual
     * assignment - see WorkflowTransitionService.assign).
     */
    private ScopeRule.ScopeContext resolveScopeContext(User actingUser, Request request, Step stepForTeamScope) {
        Department serviceDepartment = request.getRequestType().getServiceCatalog().getDepartment();
        Long currentStepTeamId = stepForTeamScope != null && stepForTeamScope.getResponsibleTeam() != null
                ? stepForTeamScope.getResponsibleTeam().getId() : null;
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
