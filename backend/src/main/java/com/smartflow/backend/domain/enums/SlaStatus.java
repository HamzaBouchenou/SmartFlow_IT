package com.smartflow.backend.domain.enums;

/**
 * Materialized SLA indicator shown on a Request (§6.7) : "dans le délai, à risque, en
 * retard". Recomputed by the scheduled SLA sweep (infrastructure/scheduler), never derived
 * on the fly in a dashboard query.
 */
public enum SlaStatus {
    ON_TRACK,
    AT_RISK,
    OVERDUE
}
