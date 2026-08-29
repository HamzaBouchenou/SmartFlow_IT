package com.smartflow.backend.infrastructure.scheduler;

import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.SystemParameter;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.List;

/**
 * RG-12/ADR-15 (docs/DECISIONS.md) - "Les données archivées restent consultables selon la
 * politique de conservation retenue par l'entreprise". Moves CLOSED/CANCELLED requests to
 * ARCHIVED past a configurable retention period, the same "recompute a materialized status
 * periodically" shape as SlaSweepScheduler (RG-07) - never a status computed on the fly in
 * a dashboard query (CLAUDE.md).
 *
 * Nothing else changes on archival: AuthorizationService.canView (ADR-10) already admits
 * ARCHIVED exactly like CLOSED (only DRAFT is excluded), and DashboardService already
 * filters only "status != DRAFT" - both already do the right thing for this status by
 * construction, per ADR-15's own reasoning.
 */
@Component
public class RequestArchivalScheduler {

    /** §6.10 - "durée paramétrable" de conservation avant archivage, en mois. */
    public static final String ARCHIVE_AFTER_MONTHS_KEY = "requests.archive-after-months";
    private static final int DEFAULT_ARCHIVE_AFTER_MONTHS = 24;

    private static final List<RequestStatus> ARCHIVABLE_STATUSES = List.of(RequestStatus.CLOSED, RequestStatus.CANCELLED);

    private final RequestRepository requestRepository;
    private final SystemParameterRepository systemParameterRepository;
    private final AuditService auditService;
    private final Clock clock;

    public RequestArchivalScheduler(RequestRepository requestRepository, SystemParameterRepository systemParameterRepository,
                                     AuditService auditService, Clock clock) {
        this.requestRepository = requestRepository;
        this.systemParameterRepository = systemParameterRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smartflow.archival.sweep-interval-ms:3600000}")
    @Transactional
    public void sweep() {
        Instant cutoff = Instant.now(clock).atZone(ZoneOffset.UTC)
                .minus(Period.ofMonths(configuredArchiveAfterMonths()))
                .toInstant();
        for (Request request : requestRepository.findByStatusIn(ARCHIVABLE_STATUSES)) {
            archiveIfPastRetention(request, cutoff);
        }
    }

    private void archiveIfPastRetention(Request request, Instant cutoff) {
        // CLOSED carries closedAt ; CANCELLED never sets it (RequestService.cancel), so
        // updatedAt (the @PreUpdate timestamp from that same cancellation save) is the best
        // available "since when has this been terminal" for it.
        Instant referenceInstant = request.getStatus() == RequestStatus.CLOSED ? request.getClosedAt() : request.getUpdatedAt();
        if (referenceInstant == null || referenceInstant.isAfter(cutoff)) {
            return;
        }
        RequestStatus previousStatus = request.getStatus();
        request.setStatus(RequestStatus.ARCHIVED);
        requestRepository.save(request);
        // RG-11 - changement de statut, système (aucun acteur humain) : AuditLog.actor
        // nullable exactement pour ce cas (voir sa propre javadoc).
        auditService.record(null, "ARCHIVE", "Request", request.getId().toString(),
                "status: " + previousStatus + " -> ARCHIVED, reference=" + request.getReference());
    }

    private int configuredArchiveAfterMonths() {
        return systemParameterRepository.findByKey(ARCHIVE_AFTER_MONTHS_KEY)
                .map(SystemParameter::getValue)
                .map(Integer::parseInt)
                .orElse(DEFAULT_ARCHIVE_AFTER_MONTHS);
    }
}
