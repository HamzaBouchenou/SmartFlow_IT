package com.smartflow.backend.crosscutting.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Replaces Spring Security's default redirect-to-login/whitebox page with a 401 in
 * CLAUDE.md's common error format, for any protected route hit without a session (ADR-01).
 *
 * SESSION_EXPIRED rather than UNAUTHENTICATED when SessionActivityFilter has just
 * invalidated the caller's session for inactivity (ADR-22): the SPA needs to tell "your
 * session expired, sign in again" apart from "you were never signed in", and it can only
 * do so from the error code. The distinction discloses nothing - it is only ever returned
 * to the very caller who was holding that session.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityErrorResponseWriter writer;

    public RestAuthenticationEntryPoint(SecurityErrorResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        if (Boolean.TRUE.equals(request.getAttribute(SessionActivityFilter.EXPIRED_REQUEST_ATTRIBUTE))) {
            writer.write(response, HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED",
                    "Votre session a expiré, veuillez vous reconnecter.");
            return;
        }
        writer.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentification requise.");
    }
}
