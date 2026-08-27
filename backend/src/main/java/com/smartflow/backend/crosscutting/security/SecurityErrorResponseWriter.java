package com.smartflow.backend.crosscutting.security;

import tools.jackson.databind.ObjectMapper;
import com.smartflow.backend.crosscutting.error.ErrorResponse;
import com.smartflow.backend.crosscutting.logging.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Writes the same { code, message, traceId, fieldErrors[] } body GlobalExceptionHandler
 * uses (crosscutting/error), for the two rejection points that happen inside Spring
 * Security's own filter chain - before DispatcherServlet, so @RestControllerAdvice cannot
 * see them: an unauthenticated request (RestAuthenticationEntryPoint) and a CSRF/other
 * access-denied rejection (RestAccessDeniedHandler). Without this, those two paths would
 * fall back to Spring Security's default HTML/whitebox error page instead of CLAUDE.md's
 * common error format.
 *
 * TraceIdFilter runs at Ordered.HIGHEST_PRECEDENCE, ahead of Spring Security's filter chain
 * (registered at the lower SecurityProperties.DEFAULT_FILTER_ORDER), so the MDC traceId is
 * already set by the time either handler below runs.
 */
@Component
class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    void write(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        ErrorResponse body = ErrorResponse.of(code, message, traceId != null ? traceId : "no-trace-id");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
