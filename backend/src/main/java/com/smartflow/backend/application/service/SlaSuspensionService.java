package com.smartflow.backend.application.service;

import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.SlaEvent;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.domain.rule.SlaEventOccurrence;
import com.smartflow.backend.domain.rule.SlaSuspensionRule;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Appends a SUSPENDED or RESUMED SlaEvent when a workflow transition changes whether the
 * request sits in a Step configured to suspend the SLA (Step.suspendSla, RG-07). Meant to
 * be called by the future "execute workflow transition" use case once currentStep has been
 * moved to enteredStep - it does not itself update Request.currentStep, write
 * RequestHistory, or check canAct; those remain that use case's job.
 *
 * Only appends to Request.getSlaEvents() (see Request's cascade) - never writes
 * Request.slaSuspendedSince/slaSuspendedMinutes/slaStatus/slaDueAt*, which stay the
 * scheduled SLA sweep's exclusive responsibility (Request's class javadoc). The explicit
 * save() re-attaches request if the caller passed it in detached, so this method behaves
 * correctly whether or not it is sharing the caller's transaction.
 */
@Service
public class SlaSuspensionService {

    private final SlaSuspensionRule slaSuspensionRule;
    private final RequestRepository requestRepository;

    public SlaSuspensionService(SlaSuspensionRule slaSuspensionRule, RequestRepository requestRepository) {
        this.slaSuspensionRule = slaSuspensionRule;
        this.requestRepository = requestRepository;
    }

    @Transactional
    public void onStepEntered(Request request, Step enteredStep, Instant now) {
        List<SlaEventOccurrence> occurrences = request.getSlaEvents().stream()
                .map(event -> new SlaEventOccurrence(event.getEventType(), event.getOccurredAt()))
                .toList();
        boolean currentlySuspended = slaSuspensionRule.replay(occurrences, now).isSuspended();

        Optional<SlaEventType> eventType = slaSuspensionRule.decideTransitionEvent(
                currentlySuspended, enteredStep.isSuspendSla());

        if (eventType.isPresent()) {
            request.addSlaEvent(new SlaEvent(request, eventType.get()));
            requestRepository.save(request);
        }
    }
}
