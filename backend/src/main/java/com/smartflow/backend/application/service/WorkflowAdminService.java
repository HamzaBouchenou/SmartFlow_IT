package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.Transition;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.exception.AdministrationValidationException;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
import com.smartflow.backend.infrastructure.repository.TransitionRepository;
import com.smartflow.backend.infrastructure.repository.WorkflowDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * §6.5/§10.1/§6.10 - administration versionnée des workflows (ADR-17, docs/DECISIONS.md).
 * Un DRAFT à la fois par RequestType, librement modifiable (Step/Transition) tant qu'il
 * reste DRAFT ; publier archive l'ancien PUBLISHED (jamais supprimé, RG-12) et rend ce
 * brouillon PUBLISHED et immuable. RG-03 ("workflow figé à la soumission") tient sans
 * garde supplémentaire ici : Request.workflowDefinitionId reste gelé par
 * RequestService.submit, et WorkflowTransitionService ne résout jamais "le workflow
 * actuellement publié" mais toujours le graphe Step/Transition de la version déjà
 * référencée - publier une nouvelle version n'y touche jamais (ADR-17's own "Raisons").
 */
@Service
public class WorkflowAdminService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final StepRepository stepRepository;
    private final TransitionRepository transitionRepository;
    private final TeamRepository teamRepository;
    private final DepartmentRepository departmentRepository;
    private final CatalogAdminService catalogAdminService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public WorkflowAdminService(WorkflowDefinitionRepository workflowDefinitionRepository, StepRepository stepRepository,
                                 TransitionRepository transitionRepository, TeamRepository teamRepository,
                                 DepartmentRepository departmentRepository, CatalogAdminService catalogAdminService,
                                 AuthorizationService authorizationService, AuditService auditService, Clock clock) {
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.stepRepository = stepRepository;
        this.transitionRepository = transitionRepository;
        this.teamRepository = teamRepository;
        this.departmentRepository = departmentRepository;
        this.catalogAdminService = catalogAdminService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WorkflowDefinition> listVersions(User actingUser, Long requestTypeId) {
        requireFunctionalAdmin(actingUser);
        catalogAdminService.getRequestType(requestTypeId);
        return workflowDefinitionRepository.findByRequestTypeIdOrderByVersionDesc(requestTypeId);
    }

    @Transactional(readOnly = true)
    public WorkflowDefinitionView getVersion(User actingUser, Long workflowDefinitionId) {
        requireFunctionalAdmin(actingUser);
        return toView(getDefinition(workflowDefinitionId));
    }

    /** ADR-17 - un seul DRAFT à la fois par RequestType. */
    @Transactional
    public WorkflowDefinition createDraft(User actingUser, Long requestTypeId) {
        requireFunctionalAdmin(actingUser);
        RequestType requestType = catalogAdminService.getRequestType(requestTypeId);
        List<WorkflowDefinition> existing = workflowDefinitionRepository.findByRequestTypeIdOrderByVersionDesc(requestTypeId);
        if (existing.stream().anyMatch(d -> d.getStatus() == PublicationStatus.DRAFT)) {
            throw new AdministrationValidationException("DRAFT_ALREADY_EXISTS",
                    "Un brouillon de workflow existe déjà pour ce type de demande.");
        }
        int nextVersion = existing.stream().mapToInt(WorkflowDefinition::getVersion).max().orElse(0) + 1;
        WorkflowDefinition draft = workflowDefinitionRepository.save(new WorkflowDefinition(requestType, nextVersion));
        auditService.record(actingUser, "CREATE_DRAFT", "WorkflowDefinition", draft.getId().toString(),
                "requestTypeId=" + requestTypeId + ", version=" + nextVersion);
        return draft;
    }

    /** ADR-17 - jamais publié ni référencé par une demande : suppression physique admise. */
    @Transactional
    public void deleteDraft(User actingUser, Long workflowDefinitionId) {
        requireFunctionalAdmin(actingUser);
        WorkflowDefinition definition = getDefinition(workflowDefinitionId);
        requireDraft(definition);
        List<Step> steps = stepRepository.findByWorkflowDefinitionIdOrderByDisplayOrderAsc(definition.getId());
        transitionRepository.deleteAll(transitionRepository.findByFromStepIdIn(steps.stream().map(Step::getId).toList()));
        stepRepository.deleteAll(steps);
        workflowDefinitionRepository.delete(definition);
        auditService.record(actingUser, "DELETE_DRAFT", "WorkflowDefinition", workflowDefinitionId.toString(), null);
    }

    /**
     * ADR-17/RG-03 - archive l'actuel PUBLISHED (RG-12, jamais supprimé) puis publie ce
     * brouillon. Refuse un workflow sans aucune Step (même contrôle que RequestService.
     * submit fait déjà à la soumission - EMPTY_WORKFLOW -, ici pour un retour immédiat).
     * N'affecte jamais Request.workflowDefinitionId d'une demande déjà en cours (ADR-17).
     */
    @Transactional
    public WorkflowDefinition publish(User actingUser, Long workflowDefinitionId) {
        requireFunctionalAdmin(actingUser);
        WorkflowDefinition draft = getDefinition(workflowDefinitionId);
        requireDraft(draft);
        List<Step> steps = stepRepository.findByWorkflowDefinitionIdOrderByDisplayOrderAsc(draft.getId());
        if (steps.isEmpty()) {
            throw new AdministrationValidationException("EMPTY_WORKFLOW",
                    "Un workflow sans aucune étape ne peut pas être publié.");
        }

        workflowDefinitionRepository.findByRequestTypeIdAndStatus(draft.getRequestType().getId(), PublicationStatus.PUBLISHED)
                .ifPresent(previous -> {
                    previous.setStatus(PublicationStatus.ARCHIVED);
                    workflowDefinitionRepository.save(previous);
                });

        draft.setStatus(PublicationStatus.PUBLISHED);
        draft.setPublishedAt(clock.instant());
        draft = workflowDefinitionRepository.save(draft);
        auditService.record(actingUser, "PUBLISH", "WorkflowDefinition", draft.getId().toString(),
                "requestTypeId=" + draft.getRequestType().getId() + ", version=" + draft.getVersion());
        return draft;
    }

    // --- Step ------------------------------------------------------------------------------

    @Transactional
    public Step addStep(User actingUser, Long workflowDefinitionId, String code, String name, int displayOrder,
                         Role responsibleRole, Long responsibleTeamId, boolean suspendSla) {
        requireFunctionalAdmin(actingUser);
        WorkflowDefinition definition = getDefinition(workflowDefinitionId);
        requireDraft(definition);
        Step step = new Step(definition, code, name);
        applyStepAttributes(step, displayOrder, responsibleRole, responsibleTeamId, suspendSla);
        step = stepRepository.save(step);
        auditService.record(actingUser, "ADD_STEP", "Step", step.getId().toString(), "code=" + code);
        return step;
    }

    @Transactional
    public Step updateStep(User actingUser, Long stepId, String code, String name, int displayOrder,
                            Role responsibleRole, Long responsibleTeamId, boolean suspendSla) {
        requireFunctionalAdmin(actingUser);
        Step step = getStep(stepId);
        requireDraft(step.getWorkflowDefinition());
        step.setCode(code);
        step.setName(name);
        applyStepAttributes(step, displayOrder, responsibleRole, responsibleTeamId, suspendSla);
        step = stepRepository.save(step);
        auditService.record(actingUser, "UPDATE_STEP", "Step", step.getId().toString(), "code=" + code);
        return step;
    }

    @Transactional
    public void deleteStep(User actingUser, Long stepId) {
        requireFunctionalAdmin(actingUser);
        Step step = getStep(stepId);
        requireDraft(step.getWorkflowDefinition());
        transitionRepository.deleteAll(transitionRepository.findByFromStepId(step.getId()));
        stepRepository.delete(step);
        auditService.record(actingUser, "DELETE_STEP", "Step", stepId.toString(), null);
    }

    // --- Transition --------------------------------------------------------------------------

    @Transactional
    public Transition addTransition(User actingUser, Long fromStepId, WorkflowAction action, Long toStepId,
                                     Priority conditionPriority, Long conditionDepartmentId, String conditionFieldCode,
                                     String conditionFieldValue) {
        requireFunctionalAdmin(actingUser);
        Step fromStep = getStep(fromStepId);
        requireDraft(fromStep.getWorkflowDefinition());
        Step toStep = resolveToStep(toStepId, fromStep.getWorkflowDefinition());

        Transition transition = new Transition(fromStep, action, toStep);
        applyTransitionAttributes(transition, conditionPriority, conditionDepartmentId, conditionFieldCode, conditionFieldValue);
        transition = transitionRepository.save(transition);
        auditService.record(actingUser, "ADD_TRANSITION", "Transition", transition.getId().toString(),
                "fromStepId=" + fromStepId + ", action=" + action);
        return transition;
    }

    @Transactional
    public Transition updateTransition(User actingUser, Long transitionId, WorkflowAction action, Long toStepId,
                                        Priority conditionPriority, Long conditionDepartmentId, String conditionFieldCode,
                                        String conditionFieldValue) {
        requireFunctionalAdmin(actingUser);
        Transition transition = getTransition(transitionId);
        requireDraft(transition.getFromStep().getWorkflowDefinition());
        transition.setAction(action);
        transition.setToStep(resolveToStep(toStepId, transition.getFromStep().getWorkflowDefinition()));
        applyTransitionAttributes(transition, conditionPriority, conditionDepartmentId, conditionFieldCode, conditionFieldValue);
        transition = transitionRepository.save(transition);
        auditService.record(actingUser, "UPDATE_TRANSITION", "Transition", transition.getId().toString(), "action=" + action);
        return transition;
    }

    @Transactional
    public void deleteTransition(User actingUser, Long transitionId) {
        requireFunctionalAdmin(actingUser);
        Transition transition = getTransition(transitionId);
        requireDraft(transition.getFromStep().getWorkflowDefinition());
        transitionRepository.delete(transition);
        auditService.record(actingUser, "DELETE_TRANSITION", "Transition", transitionId.toString(), null);
    }

    // --- shared --------------------------------------------------------------------------

    private void applyStepAttributes(Step step, int displayOrder, Role responsibleRole, Long responsibleTeamId,
                                      boolean suspendSla) {
        step.setDisplayOrder(displayOrder);
        step.setResponsibleRole(responsibleRole);
        step.setResponsibleTeam(responsibleTeamId != null
                ? teamRepository.findById(responsibleTeamId).orElseThrow(() -> new EntityNotFoundException("Équipe introuvable."))
                : null);
        step.setSuspendSla(suspendSla);
    }

    private void applyTransitionAttributes(Transition transition, Priority conditionPriority, Long conditionDepartmentId,
                                            String conditionFieldCode, String conditionFieldValue) {
        transition.setConditionPriority(conditionPriority);
        transition.setConditionDepartment(conditionDepartmentId != null
                ? departmentRepository.findById(conditionDepartmentId)
                        .orElseThrow(() -> new EntityNotFoundException("Direction/service introuvable."))
                : null);
        transition.setConditionFieldCode(conditionFieldCode);
        transition.setConditionFieldValue(conditionFieldValue);
    }

    /** §6.4 - CLOSE (et seulement CLOSE) est terminale, sans étape cible ; toute autre
     * action exige une cible appartenant au même WorkflowDefinition que fromStep -
     * jamais un câblage accidentel vers le graphe d'une autre version. */
    private Step resolveToStep(Long toStepId, WorkflowDefinition workflowDefinition) {
        if (toStepId == null) {
            return null;
        }
        Step toStep = getStep(toStepId);
        if (!toStep.getWorkflowDefinition().getId().equals(workflowDefinition.getId())) {
            throw new AdministrationValidationException("CROSS_WORKFLOW_TRANSITION",
                    "L'étape cible doit appartenir à la même version de workflow.");
        }
        return toStep;
    }

    private WorkflowDefinitionView toView(WorkflowDefinition definition) {
        List<Step> steps = stepRepository.findByWorkflowDefinitionIdOrderByDisplayOrderAsc(definition.getId());
        List<Transition> transitions = transitionRepository.findByFromStepIdIn(steps.stream().map(Step::getId).toList());
        return new WorkflowDefinitionView(definition, steps, transitions);
    }

    private void requireDraft(WorkflowDefinition definition) {
        if (definition.getStatus() != PublicationStatus.DRAFT) {
            throw new AdministrationValidationException("NOT_A_DRAFT",
                    "Une version publiée ou archivée n'est plus modifiable (ADR-17).");
        }
    }

    WorkflowDefinition getDefinition(Long workflowDefinitionId) {
        return workflowDefinitionRepository.findById(workflowDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Définition de workflow introuvable."));
    }

    private Step getStep(Long stepId) {
        return stepRepository.findById(stepId).orElseThrow(() -> new EntityNotFoundException("Étape introuvable."));
    }

    private Transition getTransition(Long transitionId) {
        return transitionRepository.findById(transitionId)
                .orElseThrow(() -> new EntityNotFoundException("Transition introuvable."));
    }

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }

    public record WorkflowDefinitionView(WorkflowDefinition definition, List<Step> steps, List<Transition> transitions) {
    }
}
