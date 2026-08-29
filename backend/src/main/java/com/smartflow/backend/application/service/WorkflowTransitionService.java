package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestHistory;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.Transition;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import com.smartflow.backend.domain.rule.CommentRequirementRule;
import com.smartflow.backend.domain.rule.TransitionResolutionRule;
import com.smartflow.backend.domain.rule.TransitionResolutionRule.CandidateTransition;
import com.smartflow.backend.domain.rule.TransitionResolutionRule.ResolutionContext;
import com.smartflow.backend.infrastructure.repository.RequestFieldValueRepository;
import com.smartflow.backend.infrastructure.repository.RequestHistoryRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
import com.smartflow.backend.infrastructure.repository.TransitionRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §6.5 - exécution d'une action de workflow (VALIDATE, REJECT, RETURN, ASSIGN,
 * REQUEST_INFO, CLOSE) sur une demande déjà soumise. C'est ici, et nulle part ailleurs, que
 * AuthorizationService.canAct, TransitionResolutionRule, CommentRequirementRule,
 * RequestHistory et SlaSuspensionService se rencontrent enfin - chacun d'eux existait déjà
 * pour ce seul appelant.
 *
 * Contrairement à RequestService (brouillon, RG-06 - propriété simple), l'autorisation ici
 * EST canAct dans son intégralité : rôle, périmètre, étape courante et séparation des
 * tâches. Un canAct refusé se traduit en 404 (CLAUDE.md - jamais un 403 qui confirmerait
 * l'existence ou l'état de la demande à un appelant hors périmètre) plutôt qu'en une
 * vérification de statut séparée : RequestService.cancel() et cette même classe (CLOSE)
 * mettent déjà currentStep à null sur une demande non actionnable (ADR-03), ce qui suffit à
 * faire échouer canAct via WorkflowActionAvailabilityRule sans dupliquer la règle ici.
 */
@Service
public class WorkflowTransitionService {

    private final RequestRepository requestRepository;
    private final TransitionRepository transitionRepository;
    private final StepRepository stepRepository;
    private final RequestFieldValueRepository requestFieldValueRepository;
    private final RequestHistoryRepository requestHistoryRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final AuthorizationService authorizationService;
    private final SlaSuspensionService slaSuspensionService;
    private final TransitionResolutionRule transitionResolutionRule;
    private final CommentRequirementRule commentRequirementRule;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final SystemParameterRepository systemParameterRepository;
    private final Clock clock;

    /** RG-08/ADR-14 - §6.10 "durée paramétrable" de réouverture, en jours après clôture. */
    public static final String REOPEN_WINDOW_DAYS_KEY = "requests.reopen-window-days";
    private static final int DEFAULT_REOPEN_WINDOW_DAYS = 30;

    public WorkflowTransitionService(RequestRepository requestRepository, TransitionRepository transitionRepository,
                                      StepRepository stepRepository, RequestFieldValueRepository requestFieldValueRepository,
                                      RequestHistoryRepository requestHistoryRepository,
                                      TaskAssignmentRepository taskAssignmentRepository, UserRepository userRepository,
                                      TeamRepository teamRepository, AuthorizationService authorizationService,
                                      SlaSuspensionService slaSuspensionService,
                                      TransitionResolutionRule transitionResolutionRule,
                                      CommentRequirementRule commentRequirementRule, NotificationService notificationService,
                                      AuditService auditService, SystemParameterRepository systemParameterRepository, Clock clock) {
        this.requestRepository = requestRepository;
        this.transitionRepository = transitionRepository;
        this.stepRepository = stepRepository;
        this.requestFieldValueRepository = requestFieldValueRepository;
        this.requestHistoryRepository = requestHistoryRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
        this.authorizationService = authorizationService;
        this.slaSuspensionService = slaSuspensionService;
        this.transitionResolutionRule = transitionResolutionRule;
        this.commentRequirementRule = commentRequirementRule;
        this.notificationService = notificationService;
        this.systemParameterRepository = systemParameterRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * §6.5/RG-04 : exécute action sur requestId pour actingUser. closureReason/
     * closureSolution ne sont lus que pour action = CLOSE (§6.4 - "Clôture avec motif,
     * solution apportée..."), ignorés sinon. assignedUserId/assignedTeamId (§6.6) ne sont
     * lus que pour ASSIGN, voir ExecuteTransitionRequest.
     */
    @Transactional
    public Request execute(User actingUser, Long requestId, WorkflowAction action, String comment,
                            String closureReason, String closureSolution, Long assignedUserId, Long assignedTeamId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable."));
        if (!authorizationService.canAct(actingUser, request, action)) {
            throw new EntityNotFoundException("Demande introuvable.");
        }
        commentRequirementRule.validate(action, comment);
        if (action != WorkflowAction.ASSIGN && (assignedUserId != null || assignedTeamId != null)) {
            throw new InvalidRequestStateException("ASSIGNEE_NOT_APPLICABLE",
                    "assignedUserId/assignedTeamId ne s'appliquent qu'à l'action ASSIGN.");
        }

        // canAct returning true already guarantees a non-null currentStep with at least one
        // matching Transition (WorkflowActionAvailabilityRule) - see this class's javadoc.
        Step fromStep = request.getCurrentStep();
        CandidateTransition resolved = resolveTransition(request, fromStep, action);

        Instant now = clock.instant();
        Step toStep;
        User newlyAssignedUser = null;
        if (action == WorkflowAction.CLOSE) {
            toStep = null;
            closeRequest(request, closureReason, closureSolution, now);
        } else {
            toStep = requireTargetStep(resolved);
            request.setCurrentStep(toStep);
            if (action == WorkflowAction.ASSIGN) {
                newlyAssignedUser = assign(request, actingUser, assignedUserId, assignedTeamId);
            }
        }
        request = requestRepository.save(request);

        // RG-04/§3.4 - exactement une ligne d'historique par transition exécutée.
        RequestHistory history = new RequestHistory(request, fromStep, action, toStep, actingUser);
        history.setComment(comment);
        requestHistoryRepository.save(history);

        if (toStep != null) {
            slaSuspensionService.onStepEntered(request, toStep, now);
        }
        notifyForAction(request, action, actingUser, newlyAssignedUser);
        // RG-11 - "statut" pour CLOSE (ADR-03 : seule cette action clôture réellement la
        // demande) et "affectation" pour ASSIGN ; les autres actions ne changent ni l'un ni
        // l'autre au sens de RG-11 (seule l'étape courante bouge, ADR-03), mais restent
        // journalisées : elles relèvent de la même décision auditable (RG-04 le demande déjà
        // pour l'historique métier, RG-11 l'étend au journal d'audit).
        String summary = switch (action) {
            case CLOSE -> "status: SUBMITTED -> CLOSED, reference=" + request.getReference();
            case ASSIGN -> "assignedUserId=" + (newlyAssignedUser != null ? newlyAssignedUser.getId() : null)
                    + ", assignedTeamId=" + assignedTeamId + ", reference=" + request.getReference();
            default -> "step: " + (fromStep != null ? fromStep.getCode() : null) + " -> "
                    + (toStep != null ? toStep.getCode() : null) + ", reference=" + request.getReference();
        };
        auditService.record(actingUser, action.name(), "Request", request.getId().toString(), summary);
        return request;
    }

    /**
     * §6.8 - "affectation, demande de complément, décision... et clôture" font partie des
     * "événements importants" à notifier ; VALIDATE/REJECT/RETURN partagent tous les trois
     * la lecture "décision" du CDC plutôt que trois types distincts (aucun n'existe dans
     * NotificationType au-delà de DECISION). ASSIGN ne notifie que quand quelqu'un d'autre
     * que actingUser a été chargé (une "prise en charge" auto-affectée n'a personne à
     * prévenir : l'agent qui vient d'agir sait déjà ce qu'il a fait), et seulement quand un
     * individu précis a été résolu (une simple dépose en file d'équipe n'a encore personne
     * à notifier).
     */
    private void notifyForAction(Request request, WorkflowAction action, User actingUser, User newlyAssignedUser) {
        Map<String, String> variables = Map.of("reference", request.getReference(), "title", request.getTitle());
        switch (action) {
            case ASSIGN -> {
                if (newlyAssignedUser != null && !newlyAssignedUser.getId().equals(actingUser.getId())) {
                    notificationService.notify(newlyAssignedUser, NotificationType.ASSIGNMENT, request,
                            "La demande " + request.getReference() + " vous a été affectée", null, variables);
                }
            }
            case REQUEST_INFO -> notificationService.notify(request.getRequester(), NotificationType.INFO_REQUESTED,
                    request, "Complément demandé pour " + request.getReference(), null, variables);
            case VALIDATE, REJECT, RETURN -> notificationService.notify(request.getRequester(), NotificationType.DECISION,
                    request, "Décision sur votre demande " + request.getReference(), null, variables);
            case CLOSE -> notificationService.notify(request.getRequester(), NotificationType.CLOSURE, request,
                    "Votre demande " + request.getReference() + " a été clôturée", null, variables);
        }
    }

    private CandidateTransition resolveTransition(Request request, Step fromStep, WorkflowAction action) {
        List<Transition> candidates = transitionRepository.findByFromStepIdAndAction(fromStep.getId(), action);
        ResolutionContext context = buildContext(request);
        return transitionResolutionRule.resolve(candidates.stream().map(WorkflowTransitionService::toCandidate).toList(), context)
                .orElseThrow(() -> new InvalidRequestStateException("NO_MATCHING_TRANSITION",
                        "Aucune transition configurée ne s'applique à l'état actuel de cette demande."));
    }

    /** ADR-03 - une action non terminale doit toujours désigner une étape suivante réelle. */
    private Step requireTargetStep(CandidateTransition resolved) {
        Long toStepId = resolved.toStepId();
        if (toStepId == null) {
            throw new InvalidRequestStateException("MISCONFIGURED_TRANSITION",
                    "Cette transition ne définit pas d'étape suivante.");
        }
        return stepRepository.findById(toStepId)
                .orElseThrow(() -> new InvalidRequestStateException("MISCONFIGURED_TRANSITION",
                        "L'étape cible configurée pour cette transition n'existe plus."));
    }

    /** ADR-03 - seule CLOSE clôture réellement la demande ; §6.4 - motif obligatoire, solution facultative. */
    private void closeRequest(Request request, String closureReason, String closureSolution, Instant now) {
        if (closureReason == null || closureReason.isBlank()) {
            throw new InvalidRequestStateException("CLOSURE_REASON_REQUIRED",
                    "Un motif de clôture est obligatoire (§6.4).");
        }
        request.setStatus(RequestStatus.CLOSED);
        request.setClosedAt(now);
        request.setClosureReason(closureReason);
        request.setClosureSolution(closureSolution);
        request.setCurrentStep(null);
        // RG-08/ADR-14 - fenêtre de réouverture, posée à chaque clôture qu'elle finisse par
        // servir ou non (RequestService.reopen vérifie reopenAllowed séparément).
        request.setReopenDeadline(now.plus(Duration.ofDays(configuredReopenWindowDays())));
    }

    /** RG-08/ADR-14 - §6.10 "durée paramétrable", administrable via SystemParameter. */
    private int configuredReopenWindowDays() {
        return systemParameterRepository.findByKey(REOPEN_WINDOW_DAYS_KEY)
                .map(parameter -> Integer.parseInt(parameter.getValue()))
                .orElse(DEFAULT_REOPEN_WINDOW_DAYS);
    }

    /**
     * §6.6 - trois façons d'exécuter ASSIGN : ni l'un ni l'autre fourni = "prendre en
     * charge" (auto-affectation, RolePermissionRule's own comment maps ASSIGN to exactly
     * this) ; assignedUserId = "affectation manuelle à un agent habilité" ; assignedTeamId =
     * dépose la demande dans la file d'équipe (assignedUser reste null jusqu'à ce que
     * quelqu'un de l'équipe la prenne à son tour). "Habilité" est vérifié en rejouant
     * canAct pour la cible sur CETTE demande, déjà déplacée sur sa nouvelle étape : la même
     * décision que celle qui vient d'autoriser actingUser, appliquée cette fois à la
     * personne qu'on choisit de charger, séparation des tâches comprise (RG - un demandeur
     * ne devrait pas plus se retrouver assigné à sa propre validation qu'y être autorisé
     * directement).
     */
    private User assign(Request request, User actingUser, Long assignedUserId, Long assignedTeamId) {
        if (assignedUserId != null && assignedTeamId != null) {
            throw new InvalidRequestStateException("AMBIGUOUS_ASSIGNMENT",
                    "Choisir un agent précis ou une équipe, pas les deux.");
        }

        User assignedUser = actingUser;
        Team assignedTeam = null;
        if (assignedUserId != null) {
            User target = userRepository.findById(assignedUserId)
                    .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable."));
            boolean eligible = Arrays.stream(WorkflowAction.values())
                    .anyMatch(action -> authorizationService.canAct(target, request, action));
            if (!eligible) {
                throw new InvalidRequestStateException("USER_NOT_ELIGIBLE",
                        "Cet utilisateur n'est pas habilité pour cette demande.");
            }
            assignedUser = target;
        } else if (assignedTeamId != null) {
            assignedTeam = teamRepository.findById(assignedTeamId)
                    .orElseThrow(() -> new EntityNotFoundException("Équipe introuvable."));
            Long serviceDepartmentId = request.getRequestType().getServiceCatalog().getDepartment().getId();
            if (!assignedTeam.getDepartment().getId().equals(serviceDepartmentId)) {
                throw new InvalidRequestStateException("TEAM_NOT_ELIGIBLE",
                        "Cette équipe n'appartient pas au service de cette demande.");
            }
            assignedUser = null;
        }

        // saveAndFlush, not save: uq_task_assignments_active_per_request is a partial unique
        // index on active=true, and Hibernate's default flush ordering runs every queued
        // INSERT before any UPDATE regardless of call order - without an explicit flush
        // here, the new row's INSERT would hit the database while the old row is still
        // active=true, violating that constraint instead of cleanly replacing it.
        taskAssignmentRepository.findByRequestIdAndActiveTrue(request.getId())
                .ifPresent(existing -> {
                    existing.setActive(false);
                    taskAssignmentRepository.saveAndFlush(existing);
                });
        taskAssignmentRepository.save(new TaskAssignment(request, assignedUser, assignedTeam, actingUser));
        return assignedUser;
    }

    private ResolutionContext buildContext(Request request) {
        Department department = request.getRequestType().getServiceCatalog().getDepartment();
        Map<String, String> values = requestFieldValueRepository.findByRequestId(request.getId()).stream()
                .collect(LinkedHashMap::new, (map, value) -> map.put(value.getFormField().getCode(), value.getValue()), Map::putAll);
        return new ResolutionContext(request.getPriority(), department.getId(), values);
    }

    private static CandidateTransition toCandidate(Transition transition) {
        Department conditionDepartment = transition.getConditionDepartment();
        return new CandidateTransition(transition.getId(),
                transition.getToStep() != null ? transition.getToStep().getId() : null,
                transition.getConditionPriority(),
                conditionDepartment != null ? conditionDepartment.getId() : null,
                transition.getConditionFieldCode(), transition.getConditionFieldValue());
    }
}
