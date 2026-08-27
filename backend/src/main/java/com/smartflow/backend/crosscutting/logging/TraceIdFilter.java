package com.smartflow.backend.crosscutting.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Puts a traceId in the SLF4J MDC for the lifetime of each request (§8: "le traceId doit
 * se retrouver dans les logs" - see logging.pattern.level in application.properties, which
 * prints this MDC value on every line), and echoes it back as a response header so a caller
 * can quote it when reporting an error. GlobalExceptionHandler (crosscutting/error) reads
 * this same MDC value into the { code, message, traceId, fieldErrors[] } response body, so
 * the logged traceId and the one shown to the caller always agree - CLAUDE.md's "jamais
 * attraper une exception en perdant le traceId" holds by construction rather than by every
 * catch block remembering to pass it along.
 *
 * Reuses an inbound X-Trace-Id header when the caller already supplies one (a browser
 * retry, or eventually infrastructure/ai calling out and back), so a single operation keeps
 * one traceId across hops instead of getting a new one at every filter.
 *
 * Runs at the very front of the filter chain - including ahead of Spring Security, once
 * configured - so a rejected-before-authentication request still logs and reports a
 * traceId.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "traceId";
    public static final String HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
