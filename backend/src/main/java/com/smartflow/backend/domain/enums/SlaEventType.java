package com.smartflow.backend.domain.enums;

/**
 * Events of the SLA timeline (RG-07 - "modèle événementiel, pas de compteur mutable").
 */
public enum SlaEventType {
    STARTED,
    SUSPENDED,
    RESUMED,
    WARNING_TRIGGERED,
    ESCALATED,
    BREACHED
}
