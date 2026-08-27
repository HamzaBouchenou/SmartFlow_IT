package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.FieldOption;
import com.smartflow.backend.domain.entity.FormField;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestFieldValue;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.exception.FormValidationException;
import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import com.smartflow.backend.domain.rule.FormFieldSpec;
import com.smartflow.backend.domain.rule.FormValidationRule;
import com.smartflow.backend.infrastructure.repository.RequestFieldValueRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.WorkflowDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §6.4 - Gestion des demandes : brouillon, modification, soumission et annulation avant
 * prise en charge. RG-01 (référence), RG-02 (jamais de suppression physique), RG-03
 * (workflow figé), RG-06 (un demandeur ne voit que ses dossiers).
 *
 * Le contrôle d'accès ici n'est PAS canAct : canAct décide des WorkflowAction sur une
 * demande déjà entrée dans son circuit (§5.1's formula requires a current Step). Créer,
 * modifier, soumettre ou annuler son propre brouillon n'est pas un WorkflowAction (§6.4 les
 * énumère séparément des actions de workflow du §6.5) - c'est une garde de propriété simple
 * (RG-06), volontairement plus étroite que canAct : seul le demandeur d'une Request peut
 * agir dessus via ce service. Un agent/manager consultant une demande dans son périmètre
 * (§6.6 - "Mes tâches") est un cas d'usage séparé, non couvert ici.
 */
@Service
public class RequestService {

    private final RequestRepository requestRepository;
    private final RequestFieldValueRepository requestFieldValueRepository;
    private final CatalogService catalogService;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final StepRepository stepRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final SlaSuspensionService slaSuspensionService;
    private final AuthorizationService authorizationService;
    private final FormValidationRule formValidationRule;
    private final Clock clock;

    public RequestService(RequestRepository requestRepository, RequestFieldValueRepository requestFieldValueRepository,
                           CatalogService catalogService, WorkflowDefinitionRepository workflowDefinitionRepository,
                           StepRepository stepRepository, TaskAssignmentRepository taskAssignmentRepository,
                           SlaSuspensionService slaSuspensionService, AuthorizationService authorizationService,
                           FormValidationRule formValidationRule, Clock clock) {
        this.requestRepository = requestRepository;
        this.requestFieldValueRepository = requestFieldValueRepository;
        this.catalogService = catalogService;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.stepRepository = stepRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.slaSuspensionService = slaSuspensionService;
        this.authorizationService = authorizationService;
        this.formValidationRule = formValidationRule;
        this.clock = clock;
    }

    /**
     * §6.4 - "Création" du brouillon. fieldValues (code -> valeur brute) est optionnel et
     * non validé pour l'obligation ("brouillon" veut dire incomplet par nature, §6.4) - seul
     * un code de champ inconnu pour ce type de demande est une erreur, jamais une valeur
     * absente ou mal formée à ce stade (le format n'est vérifié qu'à la soumission).
     */
    @Transactional
    public Request createDraft(User requester, Long requestTypeId, String title, String description,
                                Map<String, String> fieldValues) {
        RequestType requestType = catalogService.getActiveRequestType(requestTypeId);
        Request request = new Request(generateReference(), requestType, requester, title);
        request.setDescription(description);
        request = requestRepository.save(request);
        saveFieldValues(request, requestType, fieldValues);
        return request;
    }

    /** §6.4 - "modification du brouillon" : uniquement par son propre demandeur, uniquement tant que DRAFT. */
    @Transactional
    public Request updateDraft(User actingUser, Long requestId, String title, String description,
                                Map<String, String> fieldValues) {
        Request request = getOwnedDraft(actingUser, requestId);
        request.setTitle(title);
        request.setDescription(description);
        request = requestRepository.save(request);
        saveFieldValues(request, request.getRequestType(), fieldValues);
        return request;
    }

    /**
     * §6.4 - "soumission". RG-01 : la référence existe déjà depuis la création du
     * brouillon (ADR-02), rien à générer ici. RG-03 : gèle le WorkflowDefinition
     * actuellement PUBLISHED pour ce type de demande - une publication ultérieure d'une
     * nouvelle version ne modifiera jamais cette demande (§6.5).
     */
    @Transactional
    public Request submit(User actingUser, Long requestId) {
        Request request = getOwnedDraft(actingUser, requestId);
        RequestType requestType = request.getRequestType();

        CatalogService.FormDefinitionView form = catalogService.getPublishedForm(requestType.getId());
        List<FormFieldSpec> specs = form.fields().stream().map(RequestService::toSpec).toList();
        Map<String, String> values = requestFieldValueRepository.findByRequestId(request.getId()).stream()
                .collect(LinkedHashMap::new, (map, value) -> map.put(value.getFormField().getCode(), value.getValue()), Map::putAll);
        formValidationRule.validate(specs, values);

        WorkflowDefinition workflow = workflowDefinitionRepository
                .findByRequestTypeIdAndStatus(requestType.getId(), PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new InvalidRequestStateException("NO_PUBLISHED_WORKFLOW",
                        "Aucun circuit de validation publié pour ce type de demande."));
        Step firstStep = stepRepository.findByWorkflowDefinitionIdOrderByDisplayOrderAsc(workflow.getId()).stream()
                .findFirst()
                .orElseThrow(() -> new InvalidRequestStateException("EMPTY_WORKFLOW",
                        "Le circuit publié ne définit aucune étape."));

        Instant now = clock.instant();
        request.setWorkflowDefinition(workflow);
        request.setCurrentStep(firstStep);
        request.setStatus(RequestStatus.SUBMITTED);
        request.setSubmittedAt(now);
        request = requestRepository.save(request);
        slaSuspensionService.onStepEntered(request, firstStep, now);
        return request;
    }

    /**
     * §6.4 - "annulation avant prise en charge" : autorisée tant que la demande est encore
     * un brouillon, ou soumise mais sans TaskAssignment actif ("prise en charge" = §6.6's
     * ASSIGN, cf. RolePermissionRule's own comment). RG-02 : statut logique, jamais de
     * suppression physique.
     */
    @Transactional
    public Request cancel(User actingUser, Long requestId) {
        Request request = getOwned(actingUser, requestId);
        if (request.getStatus() != RequestStatus.DRAFT && request.getStatus() != RequestStatus.SUBMITTED) {
            throw new InvalidRequestStateException("ALREADY_TERMINAL",
                    "Cette demande est déjà close, annulée ou archivée.");
        }
        boolean takenCharge = taskAssignmentRepository.findByRequestIdAndActiveTrue(requestId).isPresent();
        if (takenCharge) {
            throw new InvalidRequestStateException("ALREADY_TAKEN_CHARGE",
                    "Cette demande est déjà prise en charge et ne peut plus être annulée.");
        }
        request.setStatus(RequestStatus.CANCELLED);
        // A cancelled-after-submission request must stop offering workflow actions: nulling
        // currentStep is what makes AuthorizationService.canAct (via
        // WorkflowActionAvailabilityRule) deny every WorkflowAction on it from here on,
        // exactly like a closed request (ADR-03) - without this, a stale currentStep would
        // leave it actionable through WorkflowTransitionService after being cancelled.
        request.setCurrentStep(null);
        return requestRepository.save(request);
    }

    /** §6.4 - "son propre dossier" (RG-06) : le seul point d'entrée public pour lire une demande par id. */
    @Transactional(readOnly = true)
    public RequestDetailView getDetail(User actingUser, Long requestId) {
        return toDetailView(actingUser, getOwned(actingUser, requestId));
    }

    /**
     * Construit la vue détail d'une Request déjà résolue et déjà autorisée par l'appelant
     * (typiquement WorkflowTransitionService, juste après un canAct réussi qui n'a rien à
     * voir avec la propriété RG-06 de getDetail ci-dessus) - jamais exposé tel quel par un
     * contrôleur sans passer par un contrôle d'accès en amont.
     */
    public RequestDetailView toDetailView(User actingUser, Request request) {
        Map<String, String> values = requestFieldValueRepository.findByRequestId(request.getId()).stream()
                .collect(LinkedHashMap::new, (map, value) -> map.put(value.getFormField().getCode(), value.getValue()), Map::putAll);
        List<WorkflowAction> availableActions = request.getCurrentStep() == null
                ? List.of()
                : Arrays.stream(WorkflowAction.values())
                        .filter(action -> authorizationService.canAct(actingUser, request, action))
                        .toList();
        TaskAssignment activeAssignment = taskAssignmentRepository.findByRequestIdAndActiveTrue(request.getId()).orElse(null);
        return new RequestDetailView(request, values, availableActions, activeAssignment);
    }

    /**
     * §6.4 - un demandeur ne peut voir/agir que sur ses propres dossiers (RG-06). Une
     * demande absente ou hors périmètre renvoie la même EntityNotFoundException (404 pas
     * 403, CLAUDE.md) pour ne jamais révéler qu'un id appartient à quelqu'un d'autre. Étroit
     * par construction : cf. la javadoc de classe pour la portée volontairement limitée à
     * "son propre dossier".
     */
    private Request getOwned(User actingUser, Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable."));
        if (!request.getRequester().getId().equals(actingUser.getId())) {
            throw new EntityNotFoundException("Demande introuvable.");
        }
        return request;
    }

    private Request getOwnedDraft(User actingUser, Long requestId) {
        Request request = getOwned(actingUser, requestId);
        if (request.getStatus() != RequestStatus.DRAFT) {
            throw new InvalidRequestStateException("NOT_A_DRAFT",
                    "Cette demande n'est plus modifiable à l'état brouillon.");
        }
        return request;
    }

    private void saveFieldValues(Request request, RequestType requestType, Map<String, String> fieldValues) {
        if (fieldValues == null || fieldValues.isEmpty()) {
            return;
        }
        CatalogService.FormDefinitionView form = catalogService.getPublishedForm(requestType.getId());
        Map<String, FormField> fieldsByCode = new LinkedHashMap<>();
        for (CatalogService.FormFieldView fieldView : form.fields()) {
            fieldsByCode.put(fieldView.field().getCode(), fieldView.field());
        }

        List<FormValidationException.FieldValidationError> unknownCodeErrors = new ArrayList<>();
        for (String code : fieldValues.keySet()) {
            if (!fieldsByCode.containsKey(code)) {
                unknownCodeErrors.add(new FormValidationException.FieldValidationError(code,
                        "champ inconnu pour ce type de demande"));
            }
        }
        if (!unknownCodeErrors.isEmpty()) {
            throw new FormValidationException(unknownCodeErrors);
        }

        for (Map.Entry<String, String> entry : fieldValues.entrySet()) {
            FormField field = fieldsByCode.get(entry.getKey());
            RequestFieldValue existing = requestFieldValueRepository
                    .findByRequestIdAndFormFieldId(request.getId(), field.getId())
                    .orElse(null);
            if (existing != null) {
                existing.setValue(entry.getValue());
                requestFieldValueRepository.save(existing);
            } else {
                requestFieldValueRepository.save(new RequestFieldValue(request, field, entry.getValue()));
            }
        }
    }

    /** ADR-02 (docs/DECISIONS.md) - DEM-{année}-{numéro sur 6 chiffres}, numéro jamais réinitialisé par année. */
    private String generateReference() {
        long sequenceValue = requestRepository.nextReferenceSequenceValue();
        int year = Year.now(clock).getValue();
        return "DEM-%d-%06d".formatted(year, sequenceValue);
    }

    private static FormFieldSpec toSpec(CatalogService.FormFieldView fieldView) {
        FormField field = fieldView.field();
        List<String> allowedValues = fieldView.options().stream().map(FieldOption::getValue).toList();
        return new FormFieldSpec(field.getCode(), field.getFieldType(), field.isRequired(), allowedValues,
                field.getVisibleWhenFieldCode(), field.getVisibleWhenValue());
    }

    /**
     * A Request together with its field values (by code), the WorkflowAction set canAct
     * grants actingUser right now, and its active TaskAssignment if any (§6.6) - null when
     * nobody has taken charge yet.
     */
    public record RequestDetailView(Request request, Map<String, String> fieldValues,
                                     List<WorkflowAction> availableActions, TaskAssignment activeAssignment) {
    }
}
