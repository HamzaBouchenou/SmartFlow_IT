package com.smartflow.backend.infrastructure.scheduler;

import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.Sla;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.domain.rule.SlaCalculator;
import com.smartflow.backend.domain.rule.SlaEventOccurrence;
import com.smartflow.backend.domain.rule.SlaSuspensionRule;
import com.smartflow.backend.domain.rule.SuspensionPeriod;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.SlaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Periodically recomputes the materialized SLA read model on every in-progress Request
 * (RG-07, §6.7) by replaying its SlaEvent history (SlaSuspensionRule) through
 * SlaCalculator - see Request's class javadoc for the authority rule this enforces
 * (SlaEvent wins; these columns are only ever a cache of it). It never writes a new
 * SlaEvent itself: SUSPENDED/RESUMED events are written by
 * application/service/SlaSuspensionService when a workflow transition enters or leaves a
 * Step flagged Step.suspendSla. Detecting and logging WARNING_TRIGGERED/ESCALATED/BREACHED
 * SlaEvent rows, and the notification/escalation they should trigger (§6.7 - "Notification
 * avant échéance et escalade au responsable"), are not part of this pass.
 */
@Component
public class SlaSweepScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaSweepScheduler.class);

    private final RequestRepository requestRepository;
    private final SlaRepository slaRepository;
    private final SlaCalculator slaCalculator;
    private final SlaSuspensionRule slaSuspensionRule;
    private final Clock clock;

    public SlaSweepScheduler(RequestRepository requestRepository, SlaRepository slaRepository,
                              SlaCalculator slaCalculator, SlaSuspensionRule slaSuspensionRule, Clock clock) {
        this.requestRepository = requestRepository;
        this.slaRepository = slaRepository;
        this.slaCalculator = slaCalculator;
        this.slaSuspensionRule = slaSuspensionRule;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${smartflow.sla.sweep-interval-ms:60000}")
    @Transactional
    public void sweep() {
        Instant now = clock.instant();
        for (Request request : requestRepository.findByStatus(RequestStatus.SUBMITTED)) {
            recompute(request, now);
        }
    }

    private void recompute(Request request, Instant now) {
        if (request.getPriority() == null || request.getSubmittedAt() == null) {
            // Not yet qualified (no priority), or defensively not really submitted:
            // nothing to compute a deadline against.
            return;
        }
        Optional<Sla> configured = slaRepository.findByRequestTypeIdAndPriority(
                request.getRequestType().getId(), request.getPriority());
        if (configured.isEmpty()) {
            log.warn("No SLA configured for requestType={} priority={}; skipping request {}",
                    request.getRequestType().getId(), request.getPriority(), request.getReference());
            return;
        }
        Sla sla = configured.get();

        List<SlaEventOccurrence> occurrences = request.getSlaEvents().stream()
                .map(event -> new SlaEventOccurrence(event.getEventType(), event.getOccurredAt()))
                .toList();
        SlaSuspensionRule.SuspensionSnapshot suspensions = slaSuspensionRule.replay(occurrences, now);
        List<SuspensionPeriod> allPeriods = suspensions.periodsIncludingOpen();
        Instant submittedAt = request.getSubmittedAt();

        request.setSlaDueAtFirstResponse(
                slaCalculator.computeDueAt(submittedAt, sla.getFirstResponseMinutes(), allPeriods));
        request.setSlaDueAtResolution(
                slaCalculator.computeDueAt(submittedAt, sla.getResolutionMinutes(), allPeriods));
        // The resolution deadline is the overall SLA commitment; this does not yet
        // distinguish a "before first response" phase from a "before resolution" phase -
        // Request has no firstRespondedAt field to tell them apart.
        request.setSlaStatus(
                slaCalculator.computeStatus(now, submittedAt, sla.getResolutionMinutes(), allPeriods));
        request.setSlaSuspendedSince(suspensions.openSince());
        request.setSlaSuspendedMinutes((int) suspensions.closedMinutes());

        requestRepository.save(request);
    }
}
