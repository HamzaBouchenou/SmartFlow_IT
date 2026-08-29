package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.NotificationType;

import java.util.EnumSet;
import java.util.Set;

/**
 * ADR-12 (docs/DECISIONS.md) - which NotificationType ignores NotificationPreference and is
 * always e-mailed, regardless of the recipient's own choice. §6.8 - "Préférences de
 * notification limitées pour éviter la désactivation des alertes obligatoires" ;
 * §6.7 - "escalade au responsable en cas de dépassement" is the one commitment the CDC
 * makes non-negotiable.
 */
public class MandatoryNotificationRule {

    private static final Set<NotificationType> MANDATORY =
            EnumSet.of(NotificationType.SLA_WARNING, NotificationType.SLA_BREACH);

    public boolean isMandatory(NotificationType type) {
        return MANDATORY.contains(type);
    }
}
