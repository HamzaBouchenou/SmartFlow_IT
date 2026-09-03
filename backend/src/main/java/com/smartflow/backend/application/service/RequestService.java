package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.FieldOption;
import com.smartflow.backend.domain.entity.FormField;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestFieldValue;
import com.smartflow.backend.domain.entity.RequestHistory;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.exception.FormValidationException;
import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import com.smartflow.backend.domain.rule.FormFieldSpec;
import com.smartflow.backend.domain.rule.FormValidationRule;
import com.smartflow.backend.infrastructure.repository.RequestFieldValueRepository;
import com.smartflow.backend.infrastructure.repository.RequestHistoryRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.WorkflowDefinitionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * (RG-06), volontairement plus étroite que canAct pour créer/modifier/soumettre/annuler :
 * seul le demandeur d'une Request agit dessus via ce service (getOwned/getOwnedDraft). La
 * lecture seule (getDetail) suit en revanche ADR-10 (docs/DECISIONS.md) : RG-06 prévoit
 * elle-même "sauf rôle complémentaire prévu par l'organisation", donc un agent/manager dont
 * le périmètre couvre la demande peut la consulter (jamais un simple brouillon) sans pour
 * autant pouvoir y modifier quoi que ce soit par cette voie.
 */
@Service
public class RequestService {

    private final RequestRepository requestRepository;
    private final RequestFieldValueRepository requestFieldValueRepository;
    private final RequestHistoryRepository requestHistoryRepository;
    private final CatalogService catalogService;
    private final WorkflowDefinitionRepository workflowDefinitionRepository;
    private final StepRepository stepRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final SlaSuspensionService slaSuspensionService;
    private final AuthorizationService authorizationService;
    private final FormValidationRule formValidationRule;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final Clock clock;

    public RequestService(RequestRepository requestRepository, RequestFieldValueRepository requestFieldValueRepository,
                           RequestHistoryRepository requestHistoryRepository, CatalogService catalogService,
                           WorkflowDefinitionRepository workflowDefinitionRepository,
                           StepRepository stepRepository, TaskAssignmentRepository taskAssignmentRepository,
                           SlaSuspensionService slaSuspensionService, AuthorizationService authorizationService,
                           FormValidationRule formValidationRule, NotificationService notificationService,
                           AuditService auditService, Clock clock) {
        this.requestRepository = requestRepository;
        this.requestFieldValueRepository = requestFieldValueRepository;
        this.requestHistoryRepository = requestHistoryRepository;
        this.catalogService = catalogService;
        this.workflowDefinitionRepository = workflowDefinitionRepository;
        this.stepRepository = stepRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.slaSuspensionService = slaSuspensionService;
        this.authorizationService = authorizationService;
        this.formValidationRule = formValidationRule;
        this.notificationService = notificationService;
        this.auditService = auditService;
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

        // §6.8 - "soumission" est l'un des "événements importants" à notifier. Ambiguïté du
        // destinataire tranchée pragmatiquement (aucun ADR requis, cf. Step 2 de la session) :
        // à la soumission, personne n'est encore affecté ni n'a de décision à prendre - le
        // seul destinataire certain est la requérante elle-même, à titre de confirmation.
        notificationService.notify(actingUser, NotificationType.SUBMISSION, request,
                "Votre demande " + request.getReference() + " a été soumise", null,
                Map.of("reference", request.getReference(), "title", request.getTitle()));
        // RG-11 - changement de statut.
        auditService.record(actingUser, "SUBMIT", "Request", request.getId().toString(),
                "status: DRAFT -> SUBMITTED, reference=" + request.getReference());
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
        RequestStatus previousStatus = request.getStatus();
        request.setStatus(RequestStatus.CANCELLED);
        // A cancelled-after-submission request must stop offering workflow actions: nulling
        // currentStep is what makes AuthorizationService.canAct (via
        // WorkflowActionAvailabilityRule) deny every WorkflowAction on it from here on,
        // exactly like a closed request (ADR-03) - without this, a stale currentStep would
        // leave it actionable through WorkflowTransitionService after being cancelled.
        request.setCurrentStep(null);
        request = requestRepository.save(request);
        // RG-11 - changement de statut.
        auditService.record(actingUser, "CANCEL", "Request", request.getId().toString(),
                "status: " + previousStatus + " -> CANCELLED, reference=" + request.getReference());
        return request;
    }

    /**
     * RG-08/ADR-14 (docs/DECISIONS.md) - rouvre une demande clôturée. Distinct de
     * WorkflowTransitionService.execute : REOPEN n'est jamais résolue via une Transition de
     * Step (WorkflowAction.REOPEN's own javadoc), donc ce n'est ni canAct ni
     * WorkflowActionAvailabilityRule qui décident ici - seulement les trois gardes d'état de
     * l'ADR (statut, délai, configuration du type de demande) puis
     * AuthorizationService.canReopen (rôle + périmètre). Reprend exactement à l'étape
     * quittée par la dernière CLOSE (RequestHistory), jamais une étape reconfigurée
     * séparément.
     */
    @Transactional
    public Request reopen(User actingUser, Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable."));
        if (request.getStatus() != RequestStatus.CLOSED) {
            throw new InvalidRequestStateException("NOT_CLOSED",
                    "Seule une demande clôturée peut être rouverte (RG-08).");
        }
        if (!request.getRequestType().isReopenAllowed()) {
            throw new InvalidRequestStateException("REOPEN_NOT_ALLOWED",
                    "Ce type de demande ne permet pas la réouverture (RG-08).");
        }
        Instant now = clock.instant();
        if (request.getReopenDeadline() == null || now.isAfter(request.getReopenDeadline())) {
            throw new InvalidRequestStateException("REOPEN_WINDOW_EXPIRED",
                    "Le délai de réouverture est dépassé (RG-08).");
        }
        Step targetStep = lastCloseFromStep(requestId);
        // ADR-14 : un canReopen refusé se traduit en 404, exactement comme un canAct refusé
        // (voir CommentService/AttachmentService pour le même raisonnement).
        if (!authorizationService.canReopen(actingUser, request, targetStep)) {
            throw new EntityNotFoundException("Demande introuvable.");
        }

        request.setStatus(RequestStatus.SUBMITTED);
        request.setCurrentStep(targetStep);
        // La fenêtre de réouverture qui vient d'être consommée n'a plus de sens tant que la
        // demande n'est pas re-close - une nouvelle sera posée à la prochaine CLOSE.
        request.setReopenDeadline(null);
        request = requestRepository.save(request);

        // RG-04/§3.4 - une ligne d'historique par transition, REOPEN y compris ; fromStep
        // null (elle ne part d'aucune étape courante, ADR-03/ADR-14) plutôt que le fromStep
        // de la CLOSE réutilisée ci-dessus pour toStep, afin de ne pas laisser croire que
        // REOPEN elle-même est sortie de cette étape.
        requestHistoryRepository.save(new RequestHistory(request, null, WorkflowAction.REOPEN, targetStep, actingUser));

        if (targetStep != null) {
            // RG-07 : le compteur SLA n'a jamais formellement cessé (submittedAt reste
            // l'original) - seule la suspension éventuelle de la nouvelle étape reprise doit
            // être réévaluée, exactement comme pour toute autre étape entrée.
            slaSuspensionService.onStepEntered(request, targetStep, now);
        }

        // RG-11 - changement de statut.
        auditService.record(actingUser, "REOPEN", "Request", request.getId().toString(),
                "status: CLOSED -> SUBMITTED, reference=" + request.getReference());
        return request;
    }

    /**
     * §5/RG-07 - "qualifier" une demande soumise : poser sa Priority. Sans elle,
     * SlaSweepScheduler ignore la demande en permanence ("not yet qualified") et RG-07 ne
     * calcule jamais d'échéance - ce cas d'usage manquait entièrement avant cette session
     * (voir CLAUDE.md). Volontairement distincte des WorkflowAction du §6.5
     * (AuthorizationService.canQualify's own javadoc) : elle ne déplace jamais currentStep,
     * donc aucune Transition ne la gouverne, et elle reste rejouable tant que la demande est
     * SUBMITTED (une requalification reste une qualification). RG-10 : si l'appelant vient de
     * valider un AiAnalysis de type suggestion de priorité, c'est cette valeur déjà validée
     * par un humain qu'il transmet ici - jamais une écriture directe depuis l'IA elle-même,
     * qui ne touche que AiAnalysis (AiAnalysisService.validate).
     */
    @Transactional
    public Request qualify(User actingUser, Long requestId, Priority priority) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable."));
        if (request.getStatus() != RequestStatus.SUBMITTED) {
            throw new InvalidRequestStateException("NOT_SUBMITTED",
                    "Seule une demande soumise et en cours peut être qualifiée.");
        }
        if (!authorizationService.canQualify(actingUser, request)) {
            throw new EntityNotFoundException("Demande introuvable.");
        }
        Priority previous = request.getPriority();
        request.setPriority(priority);
        request = requestRepository.save(request);
        // RG-11 - la priorité gouverne le calcul SLA (RG-07) au même titre qu'un changement
        // de statut ou d'affectation : une décision auditable, comme le reste de ce service.
        auditService.record(actingUser, "QUALIFY", "Request", request.getId().toString(),
                "priority: " + previous + " -> " + priority + ", reference=" + request.getReference());
        return request;
    }

    /**
     * §6.9/§9.4 - "vue demandeur : demandes en cours, dernières décisions et délais
     * annoncés". Contrairement à getDetail/getViewable (ADR-10, canView), cette liste ne
     * borne jamais l'accès à autre chose que la propriété du dossier (RG-06 "un demandeur
     * ne voit que ses dossiers") - un rôle complémentaire n'a pas sa place ici, il utilisera
     * "Mes tâches" (§6.6) ou un tableau de bord (§6.9), pas cette liste-ci. Inclut donc les
     * brouillons du demandeur, à la différence de getViewable qui les exclut toujours.
     */
    @Transactional(readOnly = true)
    public Page<Request> listMine(User actingUser, RequestStatus status, Pageable pageable) {
        return status != null
                ? requestRepository.findByRequesterIdAndStatus(actingUser.getId(), status, pageable)
                : requestRepository.findByRequesterId(actingUser.getId(), pageable);
    }

    /**
     * §6.4 - "Affichage d'une frise d'avancement et de l'historique complet." Même surface
     * de lecture que getDetail (getViewable : propriétaire ou canView, jamais un simple
     * brouillon pour un tiers) - l'historique d'un dossier n'est pas plus exposé que le
     * dossier lui-même.
     */
    @Transactional(readOnly = true)
    public List<RequestHistory> getHistory(User actingUser, Long requestId) {
        Request request = getViewable(actingUser, requestId);
        return requestHistoryRepository.findByRequestIdOrderByOccurredAtAsc(request.getId());
    }

    /**
     * §6.4/§9.4 - le seul point d'entrée public pour lire une demande par id. RG-06 borne
     * la lecture à "son propre dossier, sauf rôle complémentaire prévu par l'organisation"
     * (ADR-10, docs/DECISIONS.md) : contrairement à updateDraft/submit/cancel ci-dessus, qui
     * restent strictement réservés au demandeur via getOwned, cette méthode admet aussi un
     * manager/agent/service manager/auditeur dont le périmètre couvre la demande
     * (AuthorizationService.canView), jamais un simple brouillon (voir getViewable).
     */
    @Transactional(readOnly = true)
    public RequestDetailView getDetail(User actingUser, Long requestId) {
        return toDetailView(actingUser, getViewable(actingUser, requestId));
    }

    /**
     * ADR-11 (docs/DECISIONS.md) - même décision de lecture que getDetail (isRequester ou
     * canView), exposée pour CommentService/AttachmentService : commentaires et pièces
     * jointes partagent l'accès en lecture de l'écran détail (§9.4) sans dupliquer cette
     * résolution 404/canView à chaque nouvel appelant.
     */
    @Transactional(readOnly = true)
    public Request getViewableRequest(User actingUser, Long requestId) {
        return getViewable(actingUser, requestId);
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
        // REOPEN is never resolved through canAct (WorkflowAction.REOPEN's own javadoc): a
        // CLOSED request has no currentStep, so the loop below would never even reach it -
        // isReopenEligibleNow is the only path that can add it.
        List<WorkflowAction> availableActions = request.getCurrentStep() == null
                ? (isReopenEligibleNow(actingUser, request) ? List.of(WorkflowAction.REOPEN) : List.of())
                : Arrays.stream(WorkflowAction.values())
                        .filter(action -> authorizationService.canAct(actingUser, request, action))
                        .toList();
        TaskAssignment activeAssignment = taskAssignmentRepository.findByRequestIdAndActiveTrue(request.getId()).orElse(null);
        // §5/RG-07 - jamais un bouton "qualifier" posé depuis le rôle côté client
        // (CLAUDE.md) : canQualify n'est vrai que pour une demande SUBMITTED
        // (AuthorizationService.canQualify exige un currentStep non nul).
        boolean canQualify = request.getCurrentStep() != null && authorizationService.canQualify(actingUser, request);
        return new RequestDetailView(request, values, availableActions, activeAssignment, canQualify);
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

    /**
     * ADR-10 - lecture seule (getDetail) : la requérante voit toujours son propre dossier
     * (RG-06 de base), et n'importe quel autre appelant dont AuthorizationService.canView
     * couvre la demande la voit aussi (canView refuse lui-même un simple brouillon, quel
     * que soit le périmètre). Ne remplace getOwned nulle part ailleurs : updateDraft/
     * submit/cancel restent strictement au demandeur.
     */
    private Request getViewable(User actingUser, Long requestId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable."));
        boolean isRequester = request.getRequester().getId().equals(actingUser.getId());
        if (!isRequester && !authorizationService.canView(actingUser, request)) {
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

    /**
     * ADR-14 - la même condition que reopen() exige avant d'appeler
     * AuthorizationService.canReopen, réutilisée ici uniquement pour décider si REOPEN doit
     * apparaître dans availableActions[] (CLAUDE.md - jamais un bouton depuis le rôle) :
     * mêmes gardes d'état, jamais dupliquées, seulement relues.
     */
    private boolean isReopenEligibleNow(User actingUser, Request request) {
        return request.getStatus() == RequestStatus.CLOSED
                && request.getRequestType().isReopenAllowed()
                && request.getReopenDeadline() != null
                && !clock.instant().isAfter(request.getReopenDeadline())
                && authorizationService.canReopen(actingUser, request, lastCloseFromStep(request.getId()));
    }

    /** RG-08/ADR-14 - l'étape que la dernière CLOSE de cette demande a quittée. */
    private Step lastCloseFromStep(Long requestId) {
        return requestHistoryRepository
                .findFirstByRequestIdAndActionOrderByOccurredAtDesc(requestId, WorkflowAction.CLOSE)
                .orElseThrow(() -> new InvalidRequestStateException("NO_CLOSE_HISTORY",
                        "Aucune clôture n'a été retrouvée dans l'historique de cette demande."))
                .getFromStep();
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
                                     List<WorkflowAction> availableActions, TaskAssignment activeAssignment,
                                     boolean canQualify) {
    }
}
