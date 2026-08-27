package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.LoginRequest;
import com.smartflow.backend.api.dto.response.LoginResponse;
import com.smartflow.backend.api.mapper.UserMapper;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * §6.1 - Authentification. §11.2 lists only POST /api/v1/auth/login by name; logout is
 * configured declaratively on SecurityConfig's logout DSL (POST /api/v1/auth/logout), not a
 * controller method here. GET /api/v1/auth/me is this controller's own addition, needed by
 * ADR-01's session model (see its javadoc below).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    /**
     * A JSON login endpoint, not Spring Security's default form-urlencoded processing
     * filter (ADR-01: session + cookie, but the credentials arrive as a JSON body).
     * AuthenticationManager.authenticate throws AuthenticationException on bad
     * credentials or a disabled account (SmartFlowUserDetails.isEnabled - §6.1 compte
     * désactivé) - handled by GlobalExceptionHandler.handleAuthenticationException, which
     * intentionally returns one generic message so a caller cannot use this endpoint to
     * enumerate valid e-mails (§13 - fuite d'information).
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        // Persists the context into the HTTP session (ADR-01) so it survives past this
        // request - without this, the session cookie would be set but empty, and the very
        // next request would come back unauthenticated.
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        SmartFlowUserDetails principal = (SmartFlowUserDetails) authentication.getPrincipal();
        return UserMapper.toLoginResponse(principal.getUser(), principal.getRoles());
    }

    /**
     * §11.2 - a session cookie carries no identity a SPA can read (it is HttpOnly, ADR-01):
     * after a page reload, or on first load, the frontend has no other way to learn who is
     * logged in, or whether the session is still valid at all, than asking the server. Same
     * response shape as login - roles here still only inform what the UI shows, never what
     * an action may do (canAct decides that server-side, CLAUDE.md).
     */
    @GetMapping("/me")
    public LoginResponse me(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        return UserMapper.toLoginResponse(principal.getUser(), principal.getRoles());
    }

    /**
     * Exists only to prime the CSRF cookie (ADR-01) before the frontend submits its first
     * state-changing request: merely declaring the CsrfToken parameter forces Spring
     * Security's deferred token to resolve, which is what makes
     * CookieCsrfTokenRepository actually write the cookie on this response (Spring
     * Security reference docs - "Supplying the CSRF Token to a SPA"). GET requests are not
     * themselves CSRF-checked, so this endpoint needs no token to call.
     */
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken csrfToken) {
    }
}
