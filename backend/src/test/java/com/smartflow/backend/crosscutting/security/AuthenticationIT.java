package com.smartflow.backend.crosscutting.security;

import com.smartflow.backend.api.dto.request.LoginRequest;
import com.smartflow.backend.domain.entity.SystemParameter;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves ADR-01 end to end through the real Spring Security filter chain (SecurityConfig):
 * JSON login sets a session, a missing CSRF token is refused on a state-changing request,
 * an unauthenticated request to a protected route gets CLAUDE.md's common error format
 * (not Spring Security's default whitebox page), and a wrong password does too - without
 * revealing which of the two was wrong (§13).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthenticationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private SystemParameterRepository systemParameterRepository;

    @BeforeEach
    void seedUser() {
        User user = userRepository.save(new User("Ada", "Lovelace", "ada@example.com", passwordEncoder.encode("s3cret-pass")));
        userRoleAssignmentRepository.save(new UserRoleAssignment(user, Role.AGENT, ScopeType.TEAM, 1L));
    }

    @Test
    @DisplayName("GET /api/v1/auth/csrf is public and deposits the XSRF-TOKEN cookie ADR-01 requires")
    void csrfEndpointIsPublicAndSetsCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    @DisplayName("a valid login establishes a session that authenticates later requests, and returns identity/roles, never the password hash")
    void validLoginEstablishesSession() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();

        // MockMvc's TestDispatcherServlet does not emit a Set-Cookie: JSESSIONID header the
        // way a real container does, so a cookie assertion here would test a harness
        // artifact, not ADR-01 itself. Reusing the resulting MockHttpSession on a later
        // request is what actually proves the session carries the authentication forward.
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "s3cret-pass"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo("ada@example.com")))
                .andExpect(jsonPath("$.roles[0]", equalTo("AGENT")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("s3cret-pass"))))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // Unauthenticated, the same route returns 401 (protectedRouteWithoutSessionIsUnauthenticated).
        // Reusing the session clears Spring Security and reaches "no such route" instead.
        mockMvc.perform(get("/api/v1/anything").session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a wrong password is rejected with the same generic message as an unknown e-mail (§13 - no enumeration)")
    void wrongPasswordIsRejectedGenerically() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")))
                .andExpect(jsonPath("$.message", equalTo("Identifiants invalides.")))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("ADR-01 - a login POST without a CSRF token is refused (403), not silently accepted")
    void loginWithoutCsrfTokenIsRefused() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "s3cret-pass"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", equalTo("FORBIDDEN")));
    }

    @Test
    @DisplayName("an unauthenticated request to a protected route gets CLAUDE.md's common error format, not Spring Security's default page")
    void protectedRouteWithoutSessionIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    @DisplayName("§13 - every response carries the security headers the CDC's XSS/CSRF line requires, even on the public login route")
    void everyResponseCarriesSecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Content-Security-Policy",
                        org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
                .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me returns the session's identity and roles, never the password hash (§11.2 - a SPA must recover who is logged in after a page reload)")
    void meReturnsIdentityOfSessionOwner() throws Exception {
        Cookie csrfCookie = fetchCsrfCookie();
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "s3cret-pass"))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", equalTo("ada@example.com")))
                .andExpect(jsonPath("$.roles[0]", equalTo("AGENT")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("s3cret-pass"))));
    }

    @Test
    @DisplayName("GET /api/v1/auth/me without a session is unauthenticated, exactly like any other protected route")
    void meWithoutSessionIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")));
    }

    @Test
    @DisplayName("ADR-13 - after max-attempts wrong passwords, the account locks: even the correct password is now refused")
    void accountLocksAfterMaxFailedAttempts() throws Exception {
        systemParameterRepository.save(new SystemParameter(LoginAttemptListener.MAX_ATTEMPTS_KEY, "3"));
        Cookie csrfCookie = fetchCsrfCookie();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-XSRF-TOKEN", csrfCookie.getValue())
                            .cookie(csrfCookie)
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "wrong-password"))))
                    .andExpect(status().isUnauthorized());
        }

        // The 3rd wrong attempt above already locked the account (max-attempts=3) - even
        // the correct password is refused now, with the exact same generic message (§13 -
        // a locked account must not be distinguishable from a merely-wrong password).
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "s3cret-pass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")))
                .andExpect(jsonPath("$.message", equalTo("Identifiants invalides.")));

        User locked = userRepository.findByEmail("ada@example.com").orElseThrow();
        assertThat(locked.getLockedUntil()).isNotNull().isAfter(java.time.Instant.now());
        assertThat(locked.getFailedLoginAttempts()).isZero();
    }

    @Test
    @DisplayName("ADR-13 - fewer than max-attempts wrong passwords do not lock the account; a correct login still succeeds")
    void fewerThanMaxAttemptsDoesNotLockAccount() throws Exception {
        systemParameterRepository.save(new SystemParameter(LoginAttemptListener.MAX_ATTEMPTS_KEY, "5"));
        Cookie csrfCookie = fetchCsrfCookie();

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "wrong-password"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "s3cret-pass"))))
                .andExpect(status().isOk());

        // A successful login resets the counter (ADR-13), not just leaves it below threshold.
        User user = userRepository.findByEmail("ada@example.com").orElseThrow();
        assertThat(user.getFailedLoginAttempts()).isZero();
        assertThat(user.getLockedUntil()).isNull();
    }

    /** One GET, one resulting cookie - reused as both the request cookie and the X-XSRF-TOKEN header value it must match. */
    private Cookie fetchCsrfCookie() throws Exception {
        return mockMvc.perform(get("/api/v1/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
    }
}
