package com.smartflow.backend.application.service;

import com.smartflow.backend.api.dto.response.AgentHomeResponse;
import com.smartflow.backend.api.dto.response.DashboardResponse;
import com.smartflow.backend.api.dto.response.HomeDashboardResponse;
import com.smartflow.backend.api.dto.response.RequesterHomeResponse;
import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestHistory;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.domain.enums.SlaStatus;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.RequestHistoryRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.SlaEventRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * §6.9 - Tableaux de bord et reporting. "Vue responsable" (buildResponse ci-dessous) reste
 * la seule qui agrège tout un service sur une période ; "vue demandeur"/"vue agent"
 * (getHome) sont chacune bornées à actingUser lui-même - jamais de paramètre serviceId ni
 * de garde canViewDashboard pour elles, exactement comme TaskQueueService.myTasks (§6.6),
 * qu'elles réutilisent pour la partie agent plutôt que de recalculer une deuxième notion de
 * "charge". §11.2 ne nomme qu'une route pour la vue responsable : GET
 * /api/v1/dashboards/service - "service" est un ServiceCatalog id (§6.2), pas un Department
 * id ; le Department auquel il appartient ne sert qu'à decider l'accès (canViewDashboard).
 *
 * "Export CSV des listes filtrées" (§6.9) exports the same filtered request list the
 * indicators above are computed from - one coherent "filtered list" per this endpoint,
 * rather than a second, differently-filtered export route.
 */
@Service
public class DashboardService {

    private static final List<WorkflowAction> DECISION_ACTIONS =
            List.of(WorkflowAction.VALIDATE, WorkflowAction.REJECT, WorkflowAction.RETURN);

    private final ServiceCatalogRepository serviceCatalogRepository;
    private final RequestRepository requestRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final SlaEventRepository slaEventRepository;
    private final RequestHistoryRepository requestHistoryRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final TaskQueueService taskQueueService;
    private final AuthorizationService authorizationService;

    public DashboardService(ServiceCatalogRepository serviceCatalogRepository, RequestRepository requestRepository,
                             TaskAssignmentRepository taskAssignmentRepository, SlaEventRepository slaEventRepository,
                             RequestHistoryRepository requestHistoryRepository,
                             UserRoleAssignmentRepository userRoleAssignmentRepository, TaskQueueService taskQueueService,
                             AuthorizationService authorizationService) {
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.requestRepository = requestRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.slaEventRepository = slaEventRepository;
        this.requestHistoryRepository = requestHistoryRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.taskQueueService = taskQueueService;
        this.authorizationService = authorizationService;
    }

    /**
     * §9.4 (écran Accueil) / §6.9 ("vue demandeur", "vue agent") - un résumé borné à
     * actingUser, jamais à un service ni à une période : voir la javadoc de la classe.
     */
    @Transactional(readOnly = true)
    public HomeDashboardResponse getHome(User actingUser) {
        RequesterHomeResponse requester = buildRequesterHome(actingUser);
        // RolePermissionRule ne grant aucune WorkflowAction à REQUESTER (sa propre javadoc :
        // "absent de la map, donc aucune permission") - une ligne REQUESTER ne porte donc
        // structurellement aucune capacité opérationnelle, qu'elle existe ou non en base. Le
        // jeu de données de démonstration (V5) en pose deux à titre illustratif
        // ("un rôle par compte suffit à illustrer chaque périmètre de ScopeRule") : filtrer
        // explicitement REQUESTER, plutôt que supposer son absence, reste correct dans les
        // deux cas.
        boolean hasOperationalRole = userRoleAssignmentRepository.findByUserId(actingUser.getId()).stream()
                .anyMatch(assignment -> assignment.getRole() != Role.REQUESTER);
        AgentHomeResponse agent = hasOperationalRole ? buildAgentHome(actingUser) : null;
        return new HomeDashboardResponse(requester, agent);
    }

    private RequesterHomeResponse buildRequesterHome(User actingUser) {
        List<Request> mine = requestRepository.findAll(requesterSpec(actingUser.getId()));
        List<Request> inProgress = mine.stream()
                .filter(r -> r.getStatus() == RequestStatus.SUBMITTED)
                .sorted(Comparator.comparing(Request::getSubmittedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        List<RequesterHomeResponse.RequesterRequestItem> requestItems = inProgress.stream()
                .limit(5)
                .map(DashboardService::toRequesterItem)
                .toList();

        List<Long> mineIds = mine.stream().map(Request::getId).toList();
        List<RequesterHomeResponse.RequesterDecisionItem> decisions = mineIds.isEmpty() ? List.of()
                : requestHistoryRepository.findByRequestIdInAndActionIn(mineIds, DECISION_ACTIONS).stream()
                        .sorted(Comparator.comparing(RequestHistory::getOccurredAt).reversed())
                        .limit(5)
                        .map(DashboardService::toDecisionItem)
                        .toList();

        return new RequesterHomeResponse(inProgress.size(), requestItems, decisions);
    }

    private AgentHomeResponse buildAgentHome(User actingUser) {
        List<Request> assigned = taskQueueService.myTasks(actingUser, TaskQueueFilter.none(), Pageable.unpaged()).getContent();
        List<AgentHomeResponse.AgentTaskItem> overdue = assigned.stream()
                .filter(r -> r.getSlaStatus() == SlaStatus.OVERDUE)
                .sorted(Comparator.comparing(Request::getSlaDueAtResolution, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(5)
                .map(DashboardService::toAgentItem)
                .toList();
        List<AgentHomeResponse.AgentTaskItem> highPriority = assigned.stream()
                .filter(r -> r.getPriority() == Priority.HIGH || r.getPriority() == Priority.CRITICAL)
                .sorted(Comparator.comparing(Request::getPriority, Comparator.nullsFirst(Comparator.naturalOrder())).reversed())
                .limit(5)
                .map(DashboardService::toAgentItem)
                .toList();
        return new AgentHomeResponse(assigned.size(), overdue, highPriority);
    }

    private Specification<Request> requesterSpec(Long requesterId) {
        return (root, query, cb) -> cb.equal(root.get("requester").get("id"), requesterId);
    }

    private static RequesterHomeResponse.RequesterRequestItem toRequesterItem(Request r) {
        return new RequesterHomeResponse.RequesterRequestItem(r.getId(), r.getReference(), r.getTitle(),
                r.getStatus().name(), r.getPriority() != null ? r.getPriority().name() : null,
                r.getSlaStatus() != null ? r.getSlaStatus().name() : null, r.getSlaDueAtResolution());
    }

    private static RequesterHomeResponse.RequesterDecisionItem toDecisionItem(RequestHistory h) {
        return new RequesterHomeResponse.RequesterDecisionItem(h.getRequest().getId(), h.getRequest().getReference(),
                h.getAction().name(), h.getOccurredAt(), h.getComment());
    }

    private static AgentHomeResponse.AgentTaskItem toAgentItem(Request r) {
        return new AgentHomeResponse.AgentTaskItem(r.getId(), r.getReference(), r.getTitle(),
                r.getPriority() != null ? r.getPriority().name() : null,
                r.getSlaStatus() != null ? r.getSlaStatus().name() : null, r.getSlaDueAtResolution());
    }

    @Transactional(readOnly = true)
    public DashboardResponse getServiceDashboard(User actingUser, Long serviceId, Instant from, Instant to) {
        ServiceCatalog serviceCatalog = getViewableService(actingUser, serviceId);
        List<Request> requests = matchingRequests(serviceId, from, to);
        return buildResponse(serviceCatalog, from, to, requests);
    }

    /** §6.9 - "Export CSV des listes filtrées" : mêmes filtres (service, période) que le tableau de bord. */
    @Transactional(readOnly = true)
    public byte[] exportServiceRequestsCsv(User actingUser, Long serviceId, Instant from, Instant to) {
        getViewableService(actingUser, serviceId);
        List<Request> requests = matchingRequests(serviceId, from, to);
        return toCsv(requests);
    }

    private ServiceCatalog getViewableService(User actingUser, Long serviceId) {
        ServiceCatalog serviceCatalog = serviceCatalogRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service introuvable."));
        if (!authorizationService.canViewDashboard(actingUser, serviceCatalog.getDepartment())) {
            throw new EntityNotFoundException("Service introuvable.");
        }
        return serviceCatalog;
    }

    private List<Request> matchingRequests(Long serviceId, Instant from, Instant to) {
        List<Specification<Request>> specs = new ArrayList<>();
        specs.add((root, query, cb) -> cb.equal(root.get("requestType").get("serviceCatalog").get("id"), serviceId));
        // Un brouillon n'a pas encore de statut/délai à indiquer (§6.4 - reste privé avant soumission).
        specs.add((root, query, cb) -> cb.notEqual(root.get("status"), RequestStatus.DRAFT));
        if (from != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("submittedAt"), from));
        }
        if (to != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("submittedAt"), to));
        }
        return requestRepository.findAll(Specification.allOf(specs));
    }

    private DashboardResponse buildResponse(ServiceCatalog serviceCatalog, Instant from, Instant to, List<Request> requests) {
        Map<String, Long> byStatus = requests.stream()
                .collect(Collectors.groupingBy(r -> r.getStatus().name(), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byCategory = requests.stream()
                .collect(Collectors.groupingBy(DashboardService::categoryLabel, LinkedHashMap::new, Collectors.counting()));

        List<Long> requestIds = requests.stream().map(Request::getId).toList();
        Map<Long, List<TaskAssignment>> assignmentsByRequest = requestIds.isEmpty() ? Map.of()
                : taskAssignmentRepository.findByRequestIdIn(requestIds).stream()
                        .collect(Collectors.groupingBy(a -> a.getRequest().getId()));

        Map<String, Long> byAgent = new LinkedHashMap<>();
        for (Request request : requests) {
            String label = assignmentsByRequest.getOrDefault(request.getId(), List.of()).stream()
                    .filter(TaskAssignment::isActive)
                    .findFirst()
                    .map(TaskAssignment::getAssignedUser)
                    .map(u -> u.getFirstName() + " " + u.getLastName())
                    .orElse("Non affecté");
            byAgent.merge(label, 1L, Long::sum);
        }

        // §6.9 - "délai moyen de prise en charge" : soumission -> première affectation
        // (active ou non, la plus ancienne), pas seulement l'affectation active actuelle.
        List<Double> firstResponseMinutes = new ArrayList<>();
        for (Request request : requests) {
            if (request.getSubmittedAt() == null) {
                continue;
            }
            Optional<Instant> firstAssignedAt = assignmentsByRequest.getOrDefault(request.getId(), List.of()).stream()
                    .map(TaskAssignment::getAssignedAt)
                    .min(Comparator.naturalOrder());
            firstAssignedAt.ifPresent(instant ->
                    firstResponseMinutes.add((double) Duration.between(request.getSubmittedAt(), instant).toMinutes()));
        }

        List<Double> resolutionMinutes = requests.stream()
                .filter(r -> r.getStatus() == RequestStatus.CLOSED && r.getClosedAt() != null && r.getSubmittedAt() != null)
                .map(r -> (double) Duration.between(r.getSubmittedAt(), r.getClosedAt()).toMinutes())
                .toList();

        List<Request> closedRequests = requests.stream().filter(r -> r.getStatus() == RequestStatus.CLOSED).toList();
        Double slaCompliance = null;
        if (!closedRequests.isEmpty()) {
            List<Long> closedIds = closedRequests.stream().map(Request::getId).toList();
            Set<Long> breachedIds = slaEventRepository.findByRequestIdInAndEventType(closedIds, SlaEventType.BREACHED).stream()
                    .map(event -> event.getRequest().getId())
                    .collect(Collectors.toSet());
            long compliant = closedRequests.stream().filter(r -> !breachedIds.contains(r.getId())).count();
            slaCompliance = (compliant * 100.0) / closedRequests.size();
        }

        // §6.9 "taux de réouverture" - RG-08/ADR-14 : parmi les demandes déjà clôturées au
        // moins une fois sur la période (closedAt != null, indépendamment de leur statut
        // courant - une demande rouverte redevient SUBMITTED), combien portent au moins une
        // ligne d'historique REOPEN. Jamais recalculé depuis un compteur mutable (CLAUDE.md) -
        // seulement une agrégation sur l'historique déjà écrit, comme slaCompliance ci-dessus.
        List<Request> everClosedRequests = requests.stream().filter(r -> r.getClosedAt() != null).toList();
        Double reopenRate = null;
        if (!everClosedRequests.isEmpty()) {
            List<Long> everClosedIds = everClosedRequests.stream().map(Request::getId).toList();
            long reopenedCount = requestHistoryRepository.findByRequestIdInAndAction(everClosedIds, WorkflowAction.REOPEN)
                    .stream().map(history -> history.getRequest().getId()).distinct().count();
            reopenRate = (reopenedCount * 100.0) / everClosedRequests.size();
        }

        return new DashboardResponse(serviceCatalog.getId(), serviceCatalog.getName(), from, to, byStatus, byCategory,
                byAgent, average(firstResponseMinutes), average(resolutionMinutes), slaCompliance, reopenRate);
    }

    private byte[] toCsv(List<Request> requests) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(buffer, false, StandardCharsets.UTF_8)) {
            writer.println("reference;title;status;priority;requester;submittedAt;closedAt;slaStatus");
            for (Request request : requests) {
                writer.println(String.join(";",
                        csvField(request.getReference()),
                        csvField(request.getTitle()),
                        csvField(request.getStatus().name()),
                        csvField(request.getPriority() != null ? request.getPriority().name() : ""),
                        csvField(request.getRequester().getFirstName() + " " + request.getRequester().getLastName()),
                        csvField(request.getSubmittedAt() != null ? request.getSubmittedAt().toString() : ""),
                        csvField(request.getClosedAt() != null ? request.getClosedAt().toString() : ""),
                        csvField(request.getSlaStatus() != null ? request.getSlaStatus().name() : "")));
            }
        }
        return buffer.toByteArray();
    }

    private static String csvField(String value) {
        // §11.1 format normalisé, minimal ici : point-virgule séparateur (convention FR
        // usuelle pour Excel), guillemets échappés pour toute valeur qui en contiendrait.
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static String categoryLabel(Request request) {
        String category = request.getRequestType().getServiceCatalog().getCategory();
        return category != null && !category.isBlank() ? category : "Non catégorisé";
    }

    private static Double average(List<Double> values) {
        return values.isEmpty() ? null
                : values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
