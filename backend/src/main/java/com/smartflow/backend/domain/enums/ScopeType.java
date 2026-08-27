package com.smartflow.backend.domain.enums;

/**
 * Perimeter levels referenced by canAct (§5.1 - Principes d'autorisation):
 * PROPRE | EQUIPE | SERVICE | DIRECTION | GLOBAL.
 */
public enum ScopeType {
    OWN,
    TEAM,
    DEPARTMENT,
    DIRECTION,
    GLOBAL
}
