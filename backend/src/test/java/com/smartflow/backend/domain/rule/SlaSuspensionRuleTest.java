package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.SlaEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests SlaSuspensionRule, which does not exist yet (TDD, as elsewhere in this module).
 *
 * Two responsibilities, kept together because both are about interpreting the SlaEvent
 * timeline (RG-07), and both must stay pure - no Spring, no database:
 *  - replay: turns a request's SlaEvent history into closed suspension periods plus an
 *    optional still-open one, reusable by both the scheduled sweep and the transition
 *    writer below (avoids the two disagreeing about what "currently suspended" means).
 *  - decideTransitionEvent: given whether the request is currently suspended and whether
 *    the step it is entering is configured to suspend the SLA (Step.suspendSla), decides
 *    which event (if any) a workflow transition should append to the log.
 *
 * SlaEventOccurrence (not SlaEvent, the JPA entity) is the input shape: SlaEvent.occurredAt
 * is only set by @PrePersist on an actual flush, so it cannot be constructed meaningfully
 * outside a persistence context. Callers map their loaded SlaEvent rows to
 * SlaEventOccurrence before calling replay.
 */
class SlaSuspensionRuleTest {

    private final SlaSuspensionRule rule = new SlaSuspensionRule();

    @Test
    @DisplayName("no events: not suspended, nothing closed")
    void replay_withNoEvents_isNotSuspendedAndHasNoClosedPeriods() {
        SlaSuspensionRule.SuspensionSnapshot snapshot = rule.replay(List.of(), Instant.parse("2026-08-25T09:00:00Z"));

        assertThat(snapshot.isSuspended()).isFalse();
        assertThat(snapshot.closedPeriods()).isEmpty();
    }

    @Test
    @DisplayName("RG-07 - a closed SUSPENDED/RESUMED pair becomes one closed period")
    void replay_withClosedSuspendResumePair_returnsOneClosedPeriod() {
        Instant suspendedAt = Instant.parse("2026-08-25T09:10:00Z");
        Instant resumedAt = suspendedAt.plus(Duration.ofMinutes(30));
        List<SlaEventOccurrence> events = List.of(
                new SlaEventOccurrence(SlaEventType.SUSPENDED, suspendedAt),
                new SlaEventOccurrence(SlaEventType.RESUMED, resumedAt));

        SlaSuspensionRule.SuspensionSnapshot snapshot = rule.replay(events, resumedAt.plus(Duration.ofHours(1)));

        assertThat(snapshot.isSuspended()).isFalse();
        assertThat(snapshot.closedPeriods()).containsExactly(new SuspensionPeriod(suspendedAt, resumedAt));
    }

    @Test
    @DisplayName("RG-07 - a trailing SUSPENDED with no RESUMED is still open")
    void replay_withTrailingSuspendedAndNoResume_isStillOpen() {
        Instant suspendedAt = Instant.parse("2026-08-25T09:10:00Z");
        List<SlaEventOccurrence> events = List.of(new SlaEventOccurrence(SlaEventType.SUSPENDED, suspendedAt));

        SlaSuspensionRule.SuspensionSnapshot snapshot = rule.replay(events, suspendedAt.plus(Duration.ofMinutes(5)));

        assertThat(snapshot.isSuspended()).isTrue();
        assertThat(snapshot.openSince()).isEqualTo(suspendedAt);
        assertThat(snapshot.closedPeriods()).isEmpty();
    }

    @Test
    @DisplayName("RG-07 - two successive closed pairs both count, in order")
    void replay_withTwoSuccessivePairs_returnsBothClosedPeriods() {
        Instant start = Instant.parse("2026-08-25T09:00:00Z");
        List<SlaEventOccurrence> events = List.of(
                new SlaEventOccurrence(SlaEventType.SUSPENDED, start.plus(Duration.ofMinutes(10))),
                new SlaEventOccurrence(SlaEventType.RESUMED, start.plus(Duration.ofMinutes(20))),
                new SlaEventOccurrence(SlaEventType.SUSPENDED, start.plus(Duration.ofMinutes(50))),
                new SlaEventOccurrence(SlaEventType.RESUMED, start.plus(Duration.ofMinutes(75))));

        SlaSuspensionRule.SuspensionSnapshot snapshot = rule.replay(events, start.plus(Duration.ofHours(2)));

        assertThat(snapshot.closedPeriods()).containsExactly(
                new SuspensionPeriod(start.plus(Duration.ofMinutes(10)), start.plus(Duration.ofMinutes(20))),
                new SuspensionPeriod(start.plus(Duration.ofMinutes(50)), start.plus(Duration.ofMinutes(75))));
    }

    @Test
    @DisplayName("entering a suspend-SLA step while not suspended triggers SUSPENDED")
    void decideTransitionEvent_enteringSuspendStepWhileNotSuspended_isSuspended() {
        Optional<SlaEventType> event = rule.decideTransitionEvent(false, true);

        assertThat(event).contains(SlaEventType.SUSPENDED);
    }

    @Test
    @DisplayName("leaving a suspend-SLA step while suspended triggers RESUMED")
    void decideTransitionEvent_leavingSuspendStepWhileSuspended_isResumed() {
        Optional<SlaEventType> event = rule.decideTransitionEvent(true, false);

        assertThat(event).contains(SlaEventType.RESUMED);
    }

    @Test
    @DisplayName("moving between two non-suspending steps triggers nothing")
    void decideTransitionEvent_betweenTwoNonSuspendingSteps_isEmpty() {
        assertThat(rule.decideTransitionEvent(false, false)).isEmpty();
    }

    @Test
    @DisplayName("moving between two suspending steps (still suspended) triggers nothing again")
    void decideTransitionEvent_betweenTwoSuspendingSteps_isEmpty() {
        assertThat(rule.decideTransitionEvent(true, true)).isEmpty();
    }
}
