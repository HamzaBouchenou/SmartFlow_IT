package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.SlaStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests SlaCalculator.computeDueAt and computeStatus.
 *
 * Target signatures:
 *   Instant computeDueAt(Instant start, int allowedMinutes, List&lt;SuspensionPeriod&gt; suspensions)
 *   SlaStatus computeStatus(Instant now, Instant start, int allowedMinutes, List&lt;SuspensionPeriod&gt; suspensions)
 *
 * Purity requirement (CLAUDE.md - domain/rule must be testable without Spring or a
 * database): neither method reads the system clock; "now" is always passed in by the
 * caller (the scheduled SLA sweep, infrastructure/scheduler). A suspension still open at
 * the moment of calculation is represented the same way: the caller passes that
 * calculation instant as the SuspensionPeriod's end - a snapshot, not a live read.
 * SuspensionPeriod is a simple closed interval: record SuspensionPeriod(Instant start,
 * Instant end), both non-null.
 */
class SlaCalculatorTest {

    /** §6.10 - le seuil est désormais administrable (SlaSweepScheduler's own key) ; 80 % reste
     * le défaut, et la valeur avec laquelle les cas historiques ci-dessous ont été écrits. */
    private static final int DEFAULT_THRESHOLD_PERCENT = 80;

    private final SlaCalculator calculator = new SlaCalculator();

    @Test
    @DisplayName("RG-07 - the due date is anchored to the given start (submission time), not to a fixed clock")
    void computeDueAt_anchorsToTheGivenStart() {
        int allowedMinutes = 60;
        Instant firstStart = Instant.parse("2026-08-25T09:00:00Z");
        Instant secondStart = firstStart.plus(Duration.ofHours(3));

        Instant firstDueAt = calculator.computeDueAt(firstStart, allowedMinutes, List.of());
        Instant secondDueAt = calculator.computeDueAt(secondStart, allowedMinutes, List.of());

        // Shifting the submission instant shifts the due date by exactly the same amount -
        // computeDueAt has no hidden dependency on when the test itself runs.
        assertThat(Duration.between(firstStart, secondStart))
                .isEqualTo(Duration.between(firstDueAt, secondDueAt));
    }

    @Test
    @DisplayName("no suspension: due date is exactly start + allowedMinutes")
    void computeDueAt_withNoSuspensions_equalsStartPlusAllowedMinutes() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 120;

        Instant dueAt = calculator.computeDueAt(start, allowedMinutes, List.of());

        assertThat(dueAt).isEqualTo(start.plus(Duration.ofMinutes(allowedMinutes)));
    }

    @Test
    @DisplayName("RG-07 - one closed suspension pushes the due date out by its exact duration")
    void computeDueAt_withOneClosedSuspension_extendsDueDateBySuspensionDuration() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 120;
        SuspensionPeriod suspension = new SuspensionPeriod(
                start.plus(Duration.ofMinutes(10)),
                start.plus(Duration.ofMinutes(40))); // 30 minutes suspended

        Instant dueAt = calculator.computeDueAt(start, allowedMinutes, List.of(suspension));

        assertThat(dueAt).isEqualTo(start.plus(Duration.ofMinutes(allowedMinutes + 30)));
    }

    @Test
    @DisplayName("RG-07 - two successive suspensions cumulate; the second must not erase the first's elapsed time")
    void computeDueAt_withTwoSuccessiveSuspensions_cumulatesBothDurations() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 120;
        SuspensionPeriod firstSuspension = new SuspensionPeriod(
                start.plus(Duration.ofMinutes(10)),
                start.plus(Duration.ofMinutes(20))); // 10 minutes
        SuspensionPeriod secondSuspension = new SuspensionPeriod(
                start.plus(Duration.ofMinutes(50)),
                start.plus(Duration.ofMinutes(75))); // 25 minutes

        Instant dueAt = calculator.computeDueAt(
                start, allowedMinutes, List.of(firstSuspension, secondSuspension));

        // A calculator that only remembers the latest suspension (the bug this test
        // guards against) would yield start + allowedMinutes + 25, losing the first 10.
        assertThat(dueAt).isEqualTo(start.plus(Duration.ofMinutes(allowedMinutes + 10 + 25)));
    }

    @Test
    @DisplayName("RG-07 - a suspension still open at calculation time counts its elapsed portion, via a caller-supplied snapshot end")
    void computeDueAt_withSuspensionOpenAtCalculationTime_countsElapsedPortionOnly() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 120;
        Instant suspensionStart = start.plus(Duration.ofMinutes(10));
        Instant calculationInstant = suspensionStart.plus(Duration.ofMinutes(7)); // still open

        // The caller (the scheduled SLA sweep) snapshots "now" as this period's end for
        // this calculation pass - computeDueAt itself never reads the clock.
        SuspensionPeriod stillOpenSuspension = new SuspensionPeriod(suspensionStart, calculationInstant);

        Instant dueAt = calculator.computeDueAt(start, allowedMinutes, List.of(stillOpenSuspension));

        assertThat(dueAt).isEqualTo(start.plus(Duration.ofMinutes(allowedMinutes + 7)));
    }

    @Test
    @DisplayName("§6.7 - the \"at risk\" boundary sits at 80% of the allowed duration, i.e. 20% before the due date")
    void computeDueAt_atRiskThreshold_isTwentyPercentOfAllowedMinutesBeforeDueDate() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 100; // round number so the 80/20 split is exact in minutes

        Instant dueAt = calculator.computeDueAt(start, allowedMinutes, List.of());

        // §6.7 defines three SLA states (dans le délai / à risque / en retard). This test
        // pins the arithmetic invariant a future status calculation depends on: 80% of the
        // allowed budget consumed = dueAt minus 20% of allowedMinutes. It does not assert
        // a status, since computeDueAt returns only the due date.
        Instant atRiskThreshold = start.plus(Duration.ofMinutes(80));
        assertThat(dueAt.minus(Duration.ofMinutes(20))).isEqualTo(atRiskThreshold);
    }

    @Test
    @DisplayName("§6.7 - well before the 80% threshold, the request is on track")
    void computeStatus_beforeEightyPercentElapsed_isOnTrack() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 100;
        Instant now = start.plus(Duration.ofMinutes(50)); // 50% elapsed

        SlaStatus status = calculator.computeStatus(now, start, allowedMinutes, List.of(), DEFAULT_THRESHOLD_PERCENT);

        assertThat(status).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    @DisplayName("§6.7 - at or past 80% elapsed but before the due date, the request is at risk")
    void computeStatus_atOrAfterEightyPercentElapsed_isAtRisk() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 100;
        Instant now = start.plus(Duration.ofMinutes(85)); // past the 80% boundary

        SlaStatus status = calculator.computeStatus(now, start, allowedMinutes, List.of(), DEFAULT_THRESHOLD_PERCENT);

        assertThat(status).isEqualTo(SlaStatus.AT_RISK);
    }

    @Test
    @DisplayName("§6.7 - at or past the due date, the request is overdue")
    void computeStatus_atOrAfterDueDate_isOverdue() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 100;
        Instant now = start.plus(Duration.ofMinutes(100)); // exactly the due date

        SlaStatus status = calculator.computeStatus(now, start, allowedMinutes, List.of(), DEFAULT_THRESHOLD_PERCENT);

        assertThat(status).isEqualTo(SlaStatus.OVERDUE);
    }

    @Test
    @DisplayName("RG-07 - status is computed against the suspension-adjusted due date, not the naive one")
    void computeStatus_withSuspension_usesSuspensionAdjustedDueDate() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 100;
        // 30 minutes suspended early on: naive due date (start+100) would already be past,
        // but the real (extended) due date is start+130, and "now" sits well within it.
        SuspensionPeriod suspension = new SuspensionPeriod(
                start.plus(Duration.ofMinutes(10)),
                start.plus(Duration.ofMinutes(40)));
        Instant now = start.plus(Duration.ofMinutes(105));

        SlaStatus status = calculator.computeStatus(now, start, allowedMinutes, List.of(suspension), DEFAULT_THRESHOLD_PERCENT);

        assertThat(status).isEqualTo(SlaStatus.ON_TRACK);
    }

    @Test
    @DisplayName("§6.10 - le seuil d'alerte est administrable : à 50 %, la même demande bascule AT_RISK bien plus tôt")
    void computeStatus_honoursAConfiguredWarningThreshold() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 100;
        Instant now = start.plus(Duration.ofMinutes(60)); // 60 % consommés

        // Au seuil par défaut (80 %), 60 % de délai consommé reste "dans le délai"...
        assertThat(calculator.computeStatus(now, start, allowedMinutes, List.of(), DEFAULT_THRESHOLD_PERCENT))
                .isEqualTo(SlaStatus.ON_TRACK);
        // ...alors qu'un seuil administré à 50 % fait basculer la même demande "à risque".
        assertThat(calculator.computeStatus(now, start, allowedMinutes, List.of(), 50))
                .isEqualTo(SlaStatus.AT_RISK);
    }

    @Test
    @DisplayName("§6.10 - un seuil à 100 % ne laisse aucune fenêtre « à risque » : on passe directement en retard")
    void computeStatus_withHundredPercentThreshold_neverReportsAtRisk() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        int allowedMinutes = 100;

        assertThat(calculator.computeStatus(start.plus(Duration.ofMinutes(99)), start, allowedMinutes, List.of(), 100))
                .isEqualTo(SlaStatus.ON_TRACK);
        assertThat(calculator.computeStatus(start.plus(Duration.ofMinutes(100)), start, allowedMinutes, List.of(), 100))
                .isEqualTo(SlaStatus.OVERDUE);
    }
}
