package com.smartflow.backend.domain.enums;

/**
 * Actions a Step can allow (§6.5 - Workflow et validations) :
 * "valider, rejeter, retourner, affecter, demander un complément ou clôturer".
 */
public enum WorkflowAction {
    VALIDATE,
    REJECT,
    RETURN,
    ASSIGN,
    REQUEST_INFO,
    CLOSE
}
