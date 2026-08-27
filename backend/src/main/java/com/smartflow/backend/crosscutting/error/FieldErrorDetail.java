package com.smartflow.backend.crosscutting.error;

/**
 * One entry of ErrorResponse.fieldErrors - the { field, message } CLAUDE.md's common error
 * format calls for per invalid field, so a form can highlight the right input instead of
 * just showing one global message.
 */
public record FieldErrorDetail(String field, String message) {
}
