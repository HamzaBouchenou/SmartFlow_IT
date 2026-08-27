package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.SlaStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Pure SLA due-date arithmetic (RG-07, §6.7). No Spring, no database access: it only
 * combines the values it is given, so it is unit-testable in isolation (see
 * SlaCalculatorTest) and safe to call repeatedly from the scheduled SLA sweep
 * (infrastructure/scheduler) without side effects.
 *
 * Time spent suspended never counts against the allowed duration (RG-07 - "le compteur SLA
 * démarre à la soumission et peut être suspendu"): the due date is start + allowedMinutes,
 * pushed out by the sum of every suspension's duration. Suspensions are summed, never just
 * the latest one, so a second suspension does not erase the first's elapsed time.
 */
public class SlaCalculator {

    public Instant computeDueAt(Instant start, int allowedMinutes, List<SuspensionPeriod> suspensions) {
        return start.plus(Duration.ofMinutes(allowedMinutes)).plus(sumOf(suspensions));
    }

    /**
     * §6.7 - "dans le délai / à risque / en retard". The at-risk boundary sits at 80% of
     * the original allowed duration, i.e. 20% of allowedMinutes before the (suspension-
     * adjusted) due date - a suspension pushes both the due date and the at-risk boundary
     * out by the same amount, since both are derived from the same computeDueAt result.
     */
    public SlaStatus computeStatus(Instant now, Instant start, int allowedMinutes, List<SuspensionPeriod> suspensions) {
        Instant dueAt = computeDueAt(start, allowedMinutes, suspensions);
        if (!now.isBefore(dueAt)) {
            return SlaStatus.OVERDUE;
        }
        Instant atRiskThreshold = dueAt.minus(Duration.ofMinutes(allowedMinutes).dividedBy(5));
        if (!now.isBefore(atRiskThreshold)) {
            return SlaStatus.AT_RISK;
        }
        return SlaStatus.ON_TRACK;
    }

    private Duration sumOf(List<SuspensionPeriod> suspensions) {
        return suspensions.stream()
                .map(SuspensionPeriod::duration)
                .reduce(Duration.ZERO, Duration::plus);
    }
}
