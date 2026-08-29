package com.smartflow.backend.application.service;

import com.smartflow.backend.api.dto.response.DashboardResponse;
import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.SlaEventRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
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
 * §6.9 - Tableaux de bord et reporting. Only the "vue responsable" this session builds -
 * "vue demandeur"/"vue agent" (§6.9's other two bullets) are already substantially covered
 * by the existing per-user/team task queue (§6.6, TaskQueueController) and are not
 * duplicated here. §11.2 names exactly one route for this: GET /api/v1/dashboards/service -
 * "service" is a ServiceCatalog id (§6.2's own vocabulary), not a Department id; the
 * Department it belongs to is only used to decide access (canViewDashboard).
 *
 * "Export CSV des listes filtrées" (§6.9) exports the same filtered request list the
 * indicators above are computed from - one coherent "filtered list" per this endpoint,
 * rather than a second, differently-filtered export route.
 */
@Service
public class DashboardService {

    private final ServiceCatalogRepository serviceCatalogRepository;
    private final RequestRepository requestRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final SlaEventRepository slaEventRepository;
    private final AuthorizationService authorizationService;

    public DashboardService(ServiceCatalogRepository serviceCatalogRepository, RequestRepository requestRepository,
                             TaskAssignmentRepository taskAssignmentRepository, SlaEventRepository slaEventRepository,
                             AuthorizationService authorizationService) {
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.requestRepository = requestRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.slaEventRepository = slaEventRepository;
        this.authorizationService = authorizationService;
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

        return new DashboardResponse(serviceCatalog.getId(), serviceCatalog.getName(), from, to, byStatus, byCategory,
                byAgent, average(firstResponseMinutes), average(resolutionMinutes), slaCompliance,
                // RG-08 (réouverture) n'est pas encore implémentée - voir la javadoc de DashboardResponse.
                0.0);
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
