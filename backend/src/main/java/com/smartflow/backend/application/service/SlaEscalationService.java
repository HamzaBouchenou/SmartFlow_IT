package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.SlaEvent;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.domain.enums.SlaStatus;
import com.smartflow.backend.domain.rule.SlaThresholdTransitionRule;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * §6.7 - "Notification avant échéance et escalade au responsable en cas de dépassement".
 * Called once per Request by infrastructure/scheduler/SlaSweepScheduler right after it
 * recomputes the materialized SlaStatus, with the status just before and just after that
 * recomputation - domain/rule/SlaThresholdTransitionRule decides whether that change
 * crosses a threshold worth an SlaEvent, so a request sitting at the same status sweep
 * after sweep never re-fires the same notification.
 */
@Service
public class SlaEscalationService {

    private final RequestRepository requestRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final SlaThresholdTransitionRule slaThresholdTransitionRule;

    public SlaEscalationService(RequestRepository requestRepository, TaskAssignmentRepository taskAssignmentRepository,
                                 AuthorizationService authorizationService, NotificationService notificationService,
                                 SlaThresholdTransitionRule slaThresholdTransitionRule) {
        this.requestRepository = requestRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.slaThresholdTransitionRule = slaThresholdTransitionRule;
    }

    @Transactional
    public void onStatusRecomputed(Request request, SlaStatus previousStatus, SlaStatus newStatus) {
        List<SlaEventType> events = slaThresholdTransitionRule.eventsToEmit(previousStatus, newStatus);
        if (events.isEmpty()) {
            return;
        }
        for (SlaEventType type : events) {
            request.addSlaEvent(new SlaEvent(request, type));
        }
        requestRepository.save(request);

        Map<String, String> variables = Map.of("reference", request.getReference(), "title", request.getTitle());
        if (events.contains(SlaEventType.WARNING_TRIGGERED)) {
            // "avant échéance" : le seul destinataire évident est qui traite déjà le
            // dossier ; sans affectation active, personne n'a encore ce dossier en charge -
            // l'événement reste tracé (dashboards) mais rien à notifier.
            taskAssignmentRepository.findByRequestIdAndActiveTrue(request.getId())
                    .map(TaskAssignment::getAssignedUser)
                    .ifPresent(assignedUser -> notificationService.notify(assignedUser, NotificationType.SLA_WARNING, request,
                            "Échéance proche pour " + request.getReference(), null, variables));
        }
        if (events.contains(SlaEventType.BREACHED)) {
            for (User manager : authorizationService.findResponsibleManagers(request)) {
                notificationService.notify(manager, NotificationType.SLA_BREACH, request,
                        "Échéance dépassée pour " + request.getReference(), null, variables);
            }
        }
    }
}
