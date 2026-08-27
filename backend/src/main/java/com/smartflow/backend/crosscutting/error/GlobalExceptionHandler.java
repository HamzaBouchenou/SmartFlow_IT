package com.smartflow.backend.crosscutting.error;

import com.smartflow.backend.crosscutting.logging.TraceIdFilter;
import com.smartflow.backend.domain.exception.BusinessException;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.exception.FormValidationException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Turns every exception that escapes a controller into the one error body CLAUDE.md fixes:
 * { code, message, traceId, fieldErrors[] } (also §11.1: "codes HTTP cohérents et format
 * d'erreur commun"). No controller assembles ErrorResponse itself.
 *
 * traceId always comes from the MDC entry TraceIdFilter (crosscutting/logging) put there
 * for this request, so the value in the response body is the same one printed on every log
 * line for the request (§8) - CLAUDE.md's "jamais attraper une exception en perdant le
 * traceId" holds for every handler here, including the unanticipated-exception fallback.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        log.warn("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "La requête contient des champs invalides.",
                        currentTraceId(), fieldErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDetail> fieldErrors = ex.getConstraintViolations().stream()
                .map(cv -> new FieldErrorDetail(cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();
        log.warn("Constraint violation: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "La requête contient des champs invalides.",
                        currentTraceId(), fieldErrors));
    }

    /**
     * DispatcherServlet's own default for an unmapped route (no controller and no static
     * resource matches), superseding the older NoHandlerFoundException in recent Spring
     * versions - without this handler it would fall through to handleUnexpected below and
     * report a 500 for what is simply "no such route" (§11.1 - "codes HTTP cohérents").
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("NOT_FOUND", "Ressource introuvable.", currentTraceId()));
    }

    /**
     * A path/query parameter that cannot bind to its declared type - e.g. GET
     * /api/v1/request-types/abc for a Long {id}. Without this handler it falls through to
     * handleUnexpected below and reports a 500 for what is simply a malformed request
     * (§11.1 - "codes HTTP cohérents"), exactly the class of bug handleNoResourceFound
     * already fixes for an unmapped route.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parameter type mismatch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Paramètre '" + ex.getName() + "' invalide.", currentTraceId()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("MALFORMED_REQUEST", "Le corps de la requête est illisible.", currentTraceId()));
    }

    /**
     * EntityNotFoundException is also caught by handleBusiness below (it is a
     * BusinessException) - Spring resolves to this more specific handler first, which is
     * what enforces CLAUDE.md's 404-not-403 rule instead of falling through to the generic
     * 400 fallback.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), currentTraceId()));
    }

    /**
     * §6.3 server-side form validation (domain/rule/FormValidationRule) failed - also a
     * BusinessException, resolved here first for the same reason handleEntityNotFound is:
     * this carries per-field detail the generic handleBusiness below cannot, so it needs
     * fieldErrors[] populated exactly like handleValidation does for bean validation.
     */
    @ExceptionHandler(FormValidationException.class)
    public ResponseEntity<ErrorResponse> handleFormValidation(FormValidationException ex) {
        List<FieldErrorDetail> fieldErrors = ex.getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(fe.field(), fe.message()))
                .toList();
        log.warn("Form validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), currentTraceId(), fieldErrors));
    }

    /**
     * Fallback for any BusinessException subtype without its own, more specific handler.
     * Defaults to 400: a business-rule violation is, by default, "the request is invalid
     * given the current state" - a new subtype earning a different status (409 for an
     * illegal workflow transition, for instance) gets its own @ExceptionHandler above this
     * one when that use case is built, the same way EntityNotFoundException does.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        log.warn("Business rule violation [{}]: {}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getCode(), ex.getMessage(), currentTraceId()));
    }

    /**
     * A login attempt fails inside AuthController (AuthenticationManager.authenticate),
     * for a wrong password, an unknown e-mail, or a deactivated account (§6.1) alike - one
     * generic message on purpose, so this endpoint cannot be used to enumerate which
     * e-mails have an account (§13 - fuite d'information).
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of("UNAUTHENTICATED", "Identifiants invalides.", currentTraceId()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("FORBIDDEN", "Action non autorisée.", currentTraceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        String traceId = currentTraceId();
        log.error("Unexpected error, traceId={}", traceId, ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR", "Une erreur inattendue est survenue.", traceId));
    }

    private String currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        return traceId != null ? traceId : "no-trace-id";
    }
}
