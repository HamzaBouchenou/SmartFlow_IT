package com.smartflow.backend.domain.enums;

/**
 * Actions a Step can allow (§6.5 - Workflow et validations) :
 * "valider, rejeter, retourner, affecter, demander un complément ou clôturer".
 *
 * REOPEN (RG-08, ADR-14 - docs/DECISIONS.md) is the one exception: unlike every other
 * value here, it is never resolved through a Step's outgoing Transition
 * (WorkflowActionAvailabilityRule) - a CLOSED request has no currentStep (ADR-03) to carry
 * one. AuthorizationService.canReopen decides it instead of canAct, and
 * RequestService.reopen executes it instead of WorkflowTransitionService.execute.
 */
public enum WorkflowAction {
    VALIDATE,
    REJECT,
    RETURN,
    ASSIGN,
    REQUEST_INFO,
    CLOSE,
    REOPEN
}
