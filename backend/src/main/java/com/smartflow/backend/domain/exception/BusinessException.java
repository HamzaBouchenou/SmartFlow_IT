package com.smartflow.backend.domain.exception;

/**
 * Base type for business-rule violations raised by domain/rule and application/service
 * (canAct, workflow legality, SLA...). Carries a stable machine-readable code alongside the
 * human message, matching §11.1's common error format ({ code, message, traceId,
 * fieldErrors[] } - CLAUDE.md). No Spring import here: domain does not depend on the
 * framework that eventually turns an instance of this into an HTTP response
 * (crosscutting/error/GlobalExceptionHandler) - that mapping happens by exception type, one
 * layer up, so this class stays constructible and testable with zero framework context.
 */
public abstract class BusinessException extends RuntimeException {

    private final String code;

    protected BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
