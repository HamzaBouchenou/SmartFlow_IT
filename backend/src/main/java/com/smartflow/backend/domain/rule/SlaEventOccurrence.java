package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.SlaEventType;

import java.time.Instant;

/**
 * A plain (type, occurredAt) pair, decoupled from the SlaEvent JPA entity so
 * SlaSuspensionRule stays constructible in a unit test with no persistence context:
 * SlaEvent.occurredAt is only set by @PrePersist on an actual flush. Callers map their
 * loaded SlaEvent rows to this before calling SlaSuspensionRule.replay.
 */
public record SlaEventOccurrence(SlaEventType type, Instant occurredAt) {
}
