package com.smartflow.backend.domain.enums;

/**
 * Events that trigger a notification (§6.8) : "soumission, affectation, demande de
 * complément, décision, retard et clôture", plus MENTION (§6.4 - "Ajout de commentaires,
 * mentions et pièces jointes", ADR-24). MENTION reste facultative au sens d'ADR-12 : une
 * sollicitation entre collègues n'est pas un engagement de service.
 */
public enum NotificationType {
    SUBMISSION,
    ASSIGNMENT,
    INFO_REQUESTED,
    DECISION,
    SLA_WARNING,
    SLA_BREACH,
    CLOSURE,
    MENTION
}
