package com.smartflow.backend.crosscutting.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Replaces Spring Security's default whitebox 403 page with CLAUDE.md's common error
 * format. Reached mainly for a missing/invalid CSRF token on a state-changing request
 * (ADR-01) - CsrfException is itself an AccessDeniedException - since per-resource business
 * authorization never throws here: application/security's canAct returns a plain boolean,
 * and a caller turns a false into EntityNotFoundException (404), never a 403 (CLAUDE.md -
 * "404 et non 403 pour une ressource hors périmètre").
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter writer;

    public RestAccessDeniedHandler(SecurityErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        writer.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Action non autorisée.");
    }
}
