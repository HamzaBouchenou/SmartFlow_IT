package com.smartflow.backend.crosscutting.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * ADR-01 (docs/DECISIONS.md) - session serveur portée par un cookie, CSRF activé. Pas de
 * JWT, pas de refresh token : la révocation passe par l'invalidation de la session.
 *
 * Business authorization (role, perimeter, step, séparation des tâches) is never decided
 * here: this class only establishes who the caller is (authentication) and that
 * state-changing requests carry a valid CSRF token. Every per-resource decision goes
 * through application/security/AuthorizationService.canAct (CLAUDE.md - "Ne disperse
 * jamais ces règles dans des @PreAuthorize dupliqués"), which is why authorizeHttpRequests
 * below only ever distinguishes "public" from "must be logged in", never a role or a
 * specific route's business rule.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // §13 - "hachage robuste" pour users.password_hash.
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, RestAuthenticationEntryPoint authenticationEntryPoint,
                                                     RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        // CookieCsrfTokenRepository stores the raw token in the cookie; a JSON
                        // client reads that cookie and echoes it back verbatim in X-XSRF-TOKEN
                        // (Angular's HttpClientXsrfModule does exactly this). Validating that
                        // requires comparing raw-to-raw, so the plain CsrfTokenRequestAttributeHandler
                        // replaces Security 6+'s default Xor handler, which BREACH-masks the token
                        // for server-rendered forms - a masking this cookie-to-header flow neither
                        // produces nor needs (Spring Security reference docs - SPA CSRF pattern).
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        // §11.2 - la connexion elle-même, et l'amorçage du cookie CSRF
                        // qu'elle requiert (AuthController.csrf) : un visiteur sans session
                        // doit pouvoir les atteindre.
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // §11.1 - "Documentation OpenAPI ... disponible dans
                        // l'environnement de développement" : convenance de dev, pas encore
                        // restreinte par profil ; à revoir avant un déploiement en
                        // production (§15).
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) -> response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"));
        return http.build();
    }
}
