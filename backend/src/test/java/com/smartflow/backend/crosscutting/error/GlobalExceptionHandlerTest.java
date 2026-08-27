package com.smartflow.backend.crosscutting.error;

import com.smartflow.backend.crosscutting.logging.TraceIdFilter;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.exception.FormValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests GlobalExceptionHandler's mapping of exception -> HTTP status + ErrorResponse.
 *
 * §11.1 - "format d'erreur commun contenant un code métier, un message et un identifiant de
 * trace" ; CLAUDE.md - "{ code, message, traceId, fieldErrors[] }". Every case here checks
 * all four fields, not just the status code, and in particular that traceId always comes
 * from the MDC value TraceIdFilter set for this request (CLAUDE.md: "jamais attraper une
 * exception en perdant le traceId").
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void putTraceId() {
        MDC.put(TraceIdFilter.MDC_KEY, "test-trace-id");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("maps bean-validation field errors on a request DTO to 400 with one fieldErrors entry per invalid field")
    void handlesMethodArgumentNotValid() throws NoSuchMethodException {
        Method dummyMethod = DummyController.class.getMethod("dummy", String.class);
        MethodParameter methodParameter = new MethodParameter(dummyMethod, 0);
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "title", "ne doit pas être vide"));
        bindingResult.addError(new FieldError("request", "priority", "doit être une valeur connue"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.traceId()).isEqualTo("test-trace-id");
        assertThat(body.fieldErrors()).containsExactlyInAnyOrder(
                new FieldErrorDetail("title", "ne doit pas être vide"),
                new FieldErrorDetail("priority", "doit être une valeur connue"));
    }

    @Test
    @DisplayName("maps a constraint violation (e.g. an invalid @RequestParam) to 400 with a fieldErrors entry")
    void handlesConstraintViolation() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        org.mockito.Mockito.when(violation.getPropertyPath())
                .thenReturn(jakarta.validation.Path.class.cast(pathOf("page")));
        org.mockito.Mockito.when(violation.getMessage()).thenReturn("doit être positif");
        ConstraintViolationException ex = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().fieldErrors()).hasSize(1);
        assertThat(response.getBody().fieldErrors().get(0).message()).isEqualTo("doit être positif");
    }

    @Test
    @DisplayName("maps EntityNotFoundException to 404 - CLAUDE.md: 404 not 403 for an out-of-perimeter resource")
    void handlesEntityNotFound() {
        EntityNotFoundException ex = new EntityNotFoundException("Demande introuvable");

        ResponseEntity<ErrorResponse> response = handler.handleEntityNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Demande introuvable");
        assertThat(response.getBody().traceId()).isEqualTo("test-trace-id");
        assertThat(response.getBody().fieldErrors()).isEmpty();
    }

    @Test
    @DisplayName("maps a login failure (AuthenticationException) to 401 with a generic message - never revealing whether the e-mail exists")
    void handlesAuthenticationFailure() {
        BadCredentialsException ex = new BadCredentialsException("Bad credentials for ada@example.com");

        ResponseEntity<ErrorResponse> response = handler.handleAuthenticationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
        assertThat(response.getBody().message()).doesNotContain("ada@example.com");
    }

    @Test
    @DisplayName("maps an unmapped route (NoResourceFoundException) to 404, not the 500 fallback")
    void handlesNoResourceFound() {
        NoResourceFoundException ex = new NoResourceFoundException(org.springframework.http.HttpMethod.GET, "api/v1/anything", null);

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("maps an unrecognized AccessDeniedException to 403")
    void handlesAccessDenied() {
        AccessDeniedException ex = new AccessDeniedException("denied");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    @DisplayName("maps FormValidationException (§6.3) to 400 with one fieldErrors entry per invalid field")
    void handlesFormValidation() {
        FormValidationException ex = new FormValidationException(List.of(
                new FormValidationException.FieldValidationError("title", "ne doit pas être vide")));

        ResponseEntity<ErrorResponse> response = handler.handleFormValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.traceId()).isEqualTo("test-trace-id");
        assertThat(body.fieldErrors()).containsExactly(new FieldErrorDetail("title", "ne doit pas être vide"));
    }

    @Test
    @DisplayName("maps a path variable that cannot bind to its declared type (e.g. GET /request-types/abc) to 400, not a 500")
    void handlesTypeMismatch() throws NoSuchMethodException {
        Method dummyMethod = DummyController.class.getMethod("dummy", String.class);
        MethodParameter methodParameter = new MethodParameter(dummyMethod, 0);
        MethodArgumentTypeMismatchException ex =
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", methodParameter, new NumberFormatException());

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("id");
    }

    @Test
    @DisplayName("maps unreadable request bodies (malformed JSON) to 400 rather than a 500")
    void handlesUnreadableBody() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null);

        ResponseEntity<ErrorResponse> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MALFORMED_REQUEST");
    }

    @Test
    @DisplayName("maps any unanticipated exception to 500 without ever losing the traceId")
    void handlesUnexpected() {
        RuntimeException ex = new RuntimeException("boom");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().traceId()).isEqualTo("test-trace-id");
    }

    @Test
    @DisplayName("falls back to a literal placeholder rather than null when no traceId was set on the MDC")
    void fallsBackWhenNoTraceIdIsSet() {
        MDC.clear();

        ResponseEntity<ErrorResponse> response = handler.handleEntityNotFound(new EntityNotFoundException("x"));

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().traceId()).isNotBlank();
    }

    private jakarta.validation.Path pathOf(String propertyName) {
        return new jakarta.validation.Path() {
            @Override
            public java.util.Iterator<Node> iterator() {
                return java.util.List.<Node>of().iterator();
            }

            @Override
            public String toString() {
                return propertyName;
            }
        };
    }

    public static class DummyController {
        public void dummy(String request) {
        }
    }
}
