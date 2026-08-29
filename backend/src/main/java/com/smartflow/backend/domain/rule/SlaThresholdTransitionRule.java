package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.domain.enums.SlaStatus;

import java.util.ArrayList;
import java.util.List;

/**
 * §6.7 - "Notification avant échéance et escalade au responsable en cas de dépassement".
 * Decides which SlaEvent(s) a single SlaStatus recomputation must append, from the status
 * just before this sweep to the one just computed - never from the raw due dates
 * themselves, so an event fires exactly once per threshold crossed, not on every sweep
 * tick while the request sits at the same status.
 *
 * WARNING_TRIGGERED fires only on the ON_TRACK/unset -> AT_RISK crossing ("avant
 * échéance") ; BREACHED and ESCALATED always fire together the first time OVERDUE is
 * reached, whether or not AT_RISK was observed first (a sweep gap can skip straight past
 * it) - BREACHED records the fact, ESCALATED records that the responsible manager was
 * notified of it (application/service/SlaEscalationService). Nothing fires when the status
 * improves (a suspension, a corrected SLA) or stays the same: only worsening thresholds are
 * events, per RG-07's own "modèle événementiel" - recovery is simply the absence of a new
 * event, not one of its own.
 */
public class SlaThresholdTransitionRule {

    public List<SlaEventType> eventsToEmit(SlaStatus previousStatus, SlaStatus newStatus) {
        List<SlaEventType> events = new ArrayList<>();
        if (newStatus == null) {
            return events;
        }
        boolean wasAtRiskOrWorse = previousStatus == SlaStatus.AT_RISK || previousStatus == SlaStatus.OVERDUE;
        boolean wasOverdue = previousStatus == SlaStatus.OVERDUE;

        if (newStatus == SlaStatus.AT_RISK && !wasAtRiskOrWorse) {
            events.add(SlaEventType.WARNING_TRIGGERED);
        }
        if (newStatus == SlaStatus.OVERDUE && !wasOverdue) {
            events.add(SlaEventType.BREACHED);
            events.add(SlaEventType.ESCALATED);
        }
        return events;
    }
}
