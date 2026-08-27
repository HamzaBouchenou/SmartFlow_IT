package com.smartflow.backend.crosscutting.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * ADR-01 - CookieCsrfTokenRepository only writes the XSRF-TOKEN cookie once something
 * actually resolves the CsrfToken that Spring Security's CsrfFilter attaches to every
 * request as a deferred supplier (Spring Security reference docs - "Supplying the CSRF
 * Token to a SPA"). A server-rendered view triggers that resolution by reading the token to
 * put it in a form or a meta tag; a plain JSON REST API has no such view, so without this
 * filter the cookie a caller needs before their first POST/PUT/PATCH/DELETE would never get
 * set. Resolving it here, unconditionally, on every request keeps the cookie fresh
 * regardless of which endpoint the frontend happens to call first - not just
 * AuthController's dedicated /csrf convenience route.
 *
 * Registered directly on HttpSecurity (SecurityConfig), not as a Boot-managed @Component
 * like TraceIdFilter: it must run at a specific position inside Spring Security's own
 * filter chain (immediately after CsrfFilter), which only addFilterAfter can express.
 */
class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Resolving the deferred token is what makes CookieCsrfTokenRepository write it.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
