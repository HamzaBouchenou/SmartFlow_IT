package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.SlaEventType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Pure rules for interpreting a request's SLA suspension history (RG-07). No Spring, no
 * database access - unit-testable in isolation (see SlaSuspensionRuleTest).
 */
public class SlaSuspensionRule {

    /**
     * Replays a request's SlaEvent occurrences, oldest first, into closed suspension
     * periods plus an optional still-open one. Shared by the scheduled SLA sweep
     * (infrastructure/scheduler) and the workflow-transition code that appends new
     * SUSPENDED/RESUMED events, so both agree on what "currently suspended" means.
     */
    public SuspensionSnapshot replay(List<SlaEventOccurrence> occurrences, Instant now) {
        List<SuspensionPeriod> closed = new ArrayList<>();
        Instant openSince = null;
        for (SlaEventOccurrence occurrence : occurrences) {
            if (occurrence.type() == SlaEventType.SUSPENDED) {
                openSince = occurrence.occurredAt();
            } else if (occurrence.type() == SlaEventType.RESUMED && openSince != null) {
                closed.add(new SuspensionPeriod(openSince, occurrence.occurredAt()));
                openSince = null;
            }
        }
        return new SuspensionSnapshot(closed, openSince, now);
    }

    /**
     * RG-07 - "peut être suspendu uniquement par un statut prévu dans la configuration" :
     * entering a Step flagged Step.suspendSla while not already suspended starts a
     * suspension; leaving one while suspended ends it. Anything else (the flag did not
     * change, or was already in that state) produces no event - a transition must never
     * emit a duplicate SUSPENDED or a RESUMED with nothing open.
     */
    public Optional<SlaEventType> decideTransitionEvent(boolean currentlySuspended, boolean targetStepSuspendsSla) {
        if (!currentlySuspended && targetStepSuspendsSla) {
            return Optional.of(SlaEventType.SUSPENDED);
        }
        if (currentlySuspended && !targetStepSuspendsSla) {
            return Optional.of(SlaEventType.RESUMED);
        }
        return Optional.empty();
    }

    public record SuspensionSnapshot(List<SuspensionPeriod> closedPeriods, Instant openSince, Instant now) {

        public boolean isSuspended() {
            return openSince != null;
        }

        /** All periods, including the still-open one (snapshotted to `now`) if any - the shape SlaCalculator needs. */
        public List<SuspensionPeriod> periodsIncludingOpen() {
            if (openSince == null) {
                return closedPeriods;
            }
            List<SuspensionPeriod> withOpen = new ArrayList<>(closedPeriods);
            withOpen.add(new SuspensionPeriod(openSince, now));
            return withOpen;
        }

        /** Cumulative minutes across CLOSED cycles only - Request.slaSuspendedMinutes' own contract. */
        public long closedMinutes() {
            return closedPeriods.stream()
                    .map(SuspensionPeriod::duration)
                    .reduce(Duration.ZERO, Duration::plus)
                    .toMinutes();
        }
    }
}
