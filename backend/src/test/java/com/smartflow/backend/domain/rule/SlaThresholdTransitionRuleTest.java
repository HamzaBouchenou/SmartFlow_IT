package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.domain.enums.SlaStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §6.7 - "Notification avant échéance et escalade au responsable en cas de dépassement" :
 * quels SlaEvent une transition de SlaStatus doit produire, en une seule fois par
 * franchissement de seuil (pas à chaque balayage tant que le statut ne change pas).
 */
class SlaThresholdTransitionRuleTest {

    private final SlaThresholdTransitionRule rule = new SlaThresholdTransitionRule();

    @Test
    @DisplayName("ON_TRACK -> AT_RISK emits exactly WARNING_TRIGGERED")
    void onTrackToAtRiskEmitsWarning() {
        assertThat(rule.eventsToEmit(SlaStatus.ON_TRACK, SlaStatus.AT_RISK)).containsExactly(SlaEventType.WARNING_TRIGGERED);
    }

    @Test
    @DisplayName("a first-ever computation (previous null) landing on AT_RISK still emits WARNING_TRIGGERED")
    void firstComputationAtRiskEmitsWarning() {
        assertThat(rule.eventsToEmit(null, SlaStatus.AT_RISK)).containsExactly(SlaEventType.WARNING_TRIGGERED);
    }

    @Test
    @DisplayName("AT_RISK -> OVERDUE emits BREACHED and ESCALATED, never a second WARNING_TRIGGERED")
    void atRiskToOverdueEmitsBreachedAndEscalated() {
        assertThat(rule.eventsToEmit(SlaStatus.AT_RISK, SlaStatus.OVERDUE))
                .containsExactlyInAnyOrder(SlaEventType.BREACHED, SlaEventType.ESCALATED);
    }

    @Test
    @DisplayName("ON_TRACK -> OVERDUE (a sweep gap skipping AT_RISK) still emits BREACHED and ESCALATED, no stale WARNING")
    void onTrackToOverdueSkipsWarningButEscalates() {
        assertThat(rule.eventsToEmit(SlaStatus.ON_TRACK, SlaStatus.OVERDUE))
                .containsExactlyInAnyOrder(SlaEventType.BREACHED, SlaEventType.ESCALATED);
    }

    @Test
    @DisplayName("staying AT_RISK across sweeps emits nothing more")
    void stayingAtRiskEmitsNothing() {
        assertThat(rule.eventsToEmit(SlaStatus.AT_RISK, SlaStatus.AT_RISK)).isEmpty();
    }

    @Test
    @DisplayName("staying OVERDUE across sweeps emits nothing more")
    void stayingOverdueEmitsNothing() {
        assertThat(rule.eventsToEmit(SlaStatus.OVERDUE, SlaStatus.OVERDUE)).isEmpty();
    }

    @Test
    @DisplayName("improving back to ON_TRACK (suspension, reopened deadline...) emits nothing - only worsening thresholds are events")
    void improvingEmitsNothing() {
        assertThat(rule.eventsToEmit(SlaStatus.OVERDUE, SlaStatus.ON_TRACK)).isEmpty();
        assertThat(rule.eventsToEmit(SlaStatus.AT_RISK, SlaStatus.ON_TRACK)).isEmpty();
    }

    @Test
    @DisplayName("ON_TRACK -> ON_TRACK, or a null newStatus (unconfigured SLA), emits nothing")
    void noStatusChangeOrUnconfiguredEmitsNothing() {
        assertThat(rule.eventsToEmit(SlaStatus.ON_TRACK, SlaStatus.ON_TRACK)).isEmpty();
        assertThat(rule.eventsToEmit(SlaStatus.ON_TRACK, null)).isEmpty();
        assertThat(rule.eventsToEmit(null, null)).isEmpty();
    }
}
