package com.smartflow.backend.domain.enums;

/**
 * Coarse-grained lifecycle status of a Request, orthogonal to its current workflow Step.
 * The workflow itself (Step, Transition) stays fully configurable per request type
 * (§6.5) - it must never be hardcoded as a fixed status enum. This enum only carries the
 * handful of states that affect system-wide rules regardless of which workflow is active:
 * RG-02 (no physical delete), RG-08 (reopening window), RG-12 (archived stays readable).
 */
public enum RequestStatus {
    DRAFT,
    SUBMITTED,
    CLOSED,
    CANCELLED,
    ARCHIVED
}
