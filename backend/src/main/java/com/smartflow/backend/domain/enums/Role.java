package com.smartflow.backend.domain.enums;

/**
 * Fixed set of actors defined in the cahier des charges (§5 - Acteurs et droits d'accès).
 * Kept as an enum, not a database table: canAct's roleGrantsPermission(...) must stay a
 * pure, unit-testable function over a closed, known set of roles.
 */
public enum Role {
    REQUESTER,
    MANAGER,
    AGENT,
    SERVICE_MANAGER,
    FUNCTIONAL_ADMIN,
    TECHNICAL_ADMIN,
    AUDITOR
}
