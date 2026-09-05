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
     * §6.7 - "dans le délai / à risque / en retard". The at-risk boundary sits at
     * warningThresholdPercent of the original allowed duration, i.e. the remaining fraction
     * of allowedMinutes before the (suspension-adjusted) due date - a suspension pushes both
     * the due date and the at-risk boundary out by the same amount, since both are derived
     * from the same computeDueAt result.
     *
     * §6.10 lists "seuils d'alerte" among the general parameters an administrator adjusts,
     * so the threshold is an argument rather than the constant 80% it used to be - this rule
     * stays pure (it never reads the SystemParameter itself; infrastructure/scheduler
     * resolves it and passes it in, exactly as it already does for allowedMinutes).
     */
    public SlaStatus computeStatus(Instant now, Instant start, int allowedMinutes, List<SuspensionPeriod> suspensions,
                                    int warningThresholdPercent) {
        Instant dueAt = computeDueAt(start, allowedMinutes, suspensions);
        if (!now.isBefore(dueAt)) {
            return SlaStatus.OVERDUE;
        }
        long remainingMinutesAtThreshold = Math.round(allowedMinutes * (100 - warningThresholdPercent) / 100.0);
        Instant atRiskThreshold = dueAt.minus(Duration.ofMinutes(remainingMinutesAtThreshold));
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
