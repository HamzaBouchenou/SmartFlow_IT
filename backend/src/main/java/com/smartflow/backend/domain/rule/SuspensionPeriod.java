package com.smartflow.backend.domain.rule;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A closed interval during which the SLA counter of a request was suspended (RG-07). Both
 * bounds are required: a suspension still open at the moment of calculation is represented
 * by the caller passing that calculation instant as end (a snapshot) - SlaCalculator must
 * not read the clock itself to stay a pure, unit-testable rule (see SlaCalculatorTest).
 */
public record SuspensionPeriod(Instant start, Instant end) {

    public SuspensionPeriod {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end must not be before start");
        }
    }

    public Duration duration() {
        return Duration.between(start, end);
    }
}
