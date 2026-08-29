package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR-12 (docs/DECISIONS.md) - only SLA_WARNING/SLA_BREACH ignore NotificationPreference
 * (§6.7's escalade au responsable must not be silenceable).
 */
class MandatoryNotificationRuleTest {

    private final MandatoryNotificationRule rule = new MandatoryNotificationRule();

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"SLA_WARNING", "SLA_BREACH"})
    @DisplayName("ADR-12 - SLA_WARNING and SLA_BREACH are mandatory")
    void slaTypesAreMandatory(NotificationType type) {
        assertThat(rule.isMandatory(type)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"SLA_WARNING", "SLA_BREACH"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("ADR-12 - every other type respects the user's preference")
    void otherTypesAreNotMandatory(NotificationType type) {
        assertThat(rule.isMandatory(type)).isFalse();
    }
}
