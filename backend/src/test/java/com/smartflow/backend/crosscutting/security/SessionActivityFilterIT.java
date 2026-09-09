package com.smartflow.backend.crosscutting.security;

import com.smartflow.backend.api.dto.request.LoginRequest;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-22 - §6.1 "Expiration de session" / §6.10 "durée des sessions" : prouve que
 * l'inactivité se mesure sur les gestes de l'utilisateur, et qu'une requête périodique
 * d'arrière-plan ne peut pas maintenir une session ouverte indéfiniment.
 *
 * Le temps n'est pas simulé par une horloge de test : c'est l'attribut d'activité de la
 * session (SessionActivityFilter.LAST_INTERACTION_ATTRIBUTE) qui est reculé dans le passé,
 * exactement la donnée dont le filtre décide. Un test qui attendrait réellement la durée
 * d'inactivité administrable (une minute au minimum, §6.10 la compte en minutes) ne
 * prouverait rien de plus en soixante fois plus de temps.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionActivityFilterIT {

    /** 30 minutes, la valeur par défaut de SessionTimeoutListener - posée à la main sous
     * MockMvc, voir login(). */
    private static final int TEST_TIMEOUT_SECONDS = 30 * 60;

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

    @BeforeEach
    void seedUser() {
        User user = userRepository.save(
                new User("Ada", "Lovelace", "ada@example.com", passwordEncoder.encode("s3cret-pass")));
        userRoleAssignmentRepository.save(new UserRoleAssignment(user, Role.AGENT, ScopeType.TEAM, 1L));
    }

    @Test
    @DisplayName("ADR-22 - a background poll never pushes the expiry back: past the idle window the session is invalidated even though the poll keeps arriving")
    void backgroundPollCannotKeepAnIdleSessionAlive() throws Exception {
        MockHttpSession session = login();
        makeIdle(session);

        // Exactement l'appel que l'ossature émet toutes les 30 s (NotificationBell) : avant
        // ADR-22 il repoussait `lastAccessedTime` et la session ne pouvait plus jamais
        // expirer tant qu'un onglet restait ouvert.
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .session(session)
                        .header(SessionActivityFilter.BACKGROUND_REQUEST_HEADER, "true"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("SESSION_EXPIRED")))
                .andExpect(jsonPath("$.traceId").exists());

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("ADR-22 - a user-initiated request past the idle window is refused too: the poll header only ever shortens a session, never extends one")
    void userRequestPastTheIdleWindowIsAlsoRefused() throws Exception {
        MockHttpSession session = login();
        makeIdle(session);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("SESSION_EXPIRED")));
    }

    @Test
    @DisplayName("ADR-22 - within the idle window, a user-initiated request pushes the expiry back and a background poll does not")
    void onlyUserRequestsPushTheExpiryBack() throws Exception {
        MockHttpSession session = login();

        long tenSecondsAgo = System.currentTimeMillis() - 10_000;
        session.setAttribute(SessionActivityFilter.LAST_INTERACTION_ATTRIBUTE, tenSecondsAgo);
        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .session(session)
                        .header(SessionActivityFilter.BACKGROUND_REQUEST_HEADER, "true"))
                .andExpect(status().isOk());
        assertThat(session.getAttribute(SessionActivityFilter.LAST_INTERACTION_ATTRIBUTE))
                .describedAs("un appel de fond ne vaut pas activité de l'utilisateur")
                .isEqualTo(tenSecondsAgo);

        mockMvc.perform(get("/api/v1/auth/me").session(session))
                .andExpect(status().isOk());
        assertThat((Long) session.getAttribute(SessionActivityFilter.LAST_INTERACTION_ATTRIBUTE))
                .describedAs("un geste de l'utilisateur repousse bien l'échéance")
                .isGreaterThan(tenSecondsAgo);
    }

    @Test
    @DisplayName("ADR-22 - a state-changing request past the idle window reports SESSION_EXPIRED, not a confusing CSRF 403")
    void expiredSessionOnAStateChangingRequestIsNotMistakenForACsrfFailure() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        MockHttpSession session = login();
        makeIdle(session);

        // Le jeton CSRF vit dans un cookie (ADR-01, CookieCsrfTokenRepository), pas dans la
        // session : l'invalider ne le fait donc pas disparaître, et l'utilisateur reçoit la
        // vraie raison du refus plutôt qu'un 403 qui l'enverrait chercher un problème de
        // jeton.
        mockMvc.perform(post("/api/v1/notifications/read-all")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("SESSION_EXPIRED")));
    }

    @Test
    @DisplayName("ADR-22 - a request with no session at all stays UNAUTHENTICATED: SESSION_EXPIRED is only for a session this filter has just invalidated")
    void noSessionIsStillReportedAsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", equalTo("UNAUTHENTICATED")));
    }

    /** Recule l'horloge d'activité juste au-delà du délai d'inactivité de la session. */
    private static void makeIdle(MockHttpSession session) {
        long idleWindowMillis = session.getMaxInactiveInterval() * 1000L;
        session.setAttribute(SessionActivityFilter.LAST_INTERACTION_ATTRIBUTE,
                System.currentTimeMillis() - idleWindowMillis - 1_000L);
    }

    private MockHttpSession login() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andReturn().getResponse().getCookie("XSRF-TOKEN");
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .cookie(csrfCookie)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("ada@example.com", "s3cret-pass"))))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();

        // MockHttpSession ne notifie aucun HttpSessionListener - c'est le conteneur qui le
        // fait - donc SessionTimeoutListener n'a pas posé la durée administrable ici, et
        // l'intervalle vaut 0 ("n'expire jamais"). Le poser à la main est le seul moyen
        // d'exercer le filtre sous MockMvc, exactement comme AuthenticationIT constate que
        // MockMvc n'émet pas de Set-Cookie: JSESSIONID. Que cette durée vienne bien de la
        // SystemParameter est prouvé ailleurs (SessionTimeoutListenerTest).
        session.setMaxInactiveInterval(TEST_TIMEOUT_SECONDS);
        return session;
    }
}
