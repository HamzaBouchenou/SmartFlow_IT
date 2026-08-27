package com.smartflow.backend.crosscutting.error;

import java.util.List;

/**
 * The single error body shape every API error uses (CLAUDE.md: "Format d'erreur unique :
 * { code, message, traceId, fieldErrors[] }", §11.1: "format d'erreur commun contenant un
 * code métier, un message et un identifiant de trace"). Produced only by
 * GlobalExceptionHandler, never assembled ad hoc in a controller.
 */
public record ErrorResponse(String code, String message, String traceId, List<FieldErrorDetail> fieldErrors) {

    public static ErrorResponse of(String code, String message, String traceId) {
        return new ErrorResponse(code, message, traceId, List.of());
    }

    public static ErrorResponse of(String code, String message, String traceId, List<FieldErrorDetail> fieldErrors) {
        return new ErrorResponse(code, message, traceId, fieldErrors);
    }
}
