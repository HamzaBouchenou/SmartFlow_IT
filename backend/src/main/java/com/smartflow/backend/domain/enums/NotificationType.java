package com.smartflow.backend.domain.enums;

/**
 * Events that trigger a notification (§6.8) : "soumission, affectation, demande de
 * complément, décision, retard et clôture".
 */
public enum NotificationType {
    SUBMISSION,
    ASSIGNMENT,
    INFO_REQUESTED,
    DECISION,
    SLA_WARNING,
    SLA_BREACH,
    CLOSURE
}
