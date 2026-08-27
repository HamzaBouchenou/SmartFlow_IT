package com.smartflow.backend.crosscutting.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable Clock bean rather than direct Instant.now() calls, so components
 * that need "now" (e.g. the SLA sweep, infrastructure/scheduler) stay testable with a
 * fixed or controllable time source instead of the real system clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
