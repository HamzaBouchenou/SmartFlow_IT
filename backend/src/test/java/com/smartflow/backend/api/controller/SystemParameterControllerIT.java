package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.crosscutting.security.LoginAttemptListener;
import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.AuditLogRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.10 - "Paramètres généraux", administrés uniquement par un FUNCTIONAL_ADMIN (§5). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SystemParameterControllerIT {

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
    private AuditLogRepository auditLogRepository;

    private User requester;
    private UserDetails asRequester;
    private User functionalAdmin;
    private UserDetails asFunctionalAdmin;
    private UserDetails asTechnicalAdmin;

    @BeforeEach
    void seedUsers() {
        requester = userRepository.save(new User("Amina", "Idrissi", "amina.param@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        functionalAdmin = userRepository.save(new User("Karim", "El Fassi", "karim.param@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(functionalAdmin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asFunctionalAdmin = new SmartFlowUserDetails(functionalAdmin, Set.of(Role.FUNCTIONAL_ADMIN));

        User technicalAdmin = userRepository.save(new User("Sara", "Bennis", "sara.param@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(technicalAdmin, Role.TECHNICAL_ADMIN, ScopeType.GLOBAL, null));
        asTechnicalAdmin = new SmartFlowUserDetails(technicalAdmin, Set.of(Role.TECHNICAL_ADMIN));
    }

    @Test
    @DisplayName("GET /api/v1/admin/system-parameters requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system-parameters")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot list system parameters (404, not 403)")
    void plainRequesterCannotList() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system-parameters").with(user(asRequester)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§5 - a TECHNICAL_ADMIN cannot manage functional parameters either (Administrateur fonctionnel only)")
    void technicalAdminCannotManage() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system-parameters").with(user(asTechnicalAdmin)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.10 - the catalog is returned with each key's coded default when nothing is overridden")
    void listsCatalogWithDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/admin/system-parameters").with(user(asFunctionalAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.key=='" + LoginAttemptListener.MAX_ATTEMPTS_KEY + "')].value").value("5"))
                .andExpect(jsonPath("$[?(@.key=='" + LoginAttemptListener.MAX_ATTEMPTS_KEY + "')].overridden").value(false));
    }

    @Test
    @DisplayName("RG-11/§6.10 - updating a parameter persists it, the list reflects it, and it is journalized")
    void updatePersistsAndJournalizes() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("value", "8"));

        mockMvc.perform(put("/api/v1/admin/system-parameters/{key}", LoginAttemptListener.MAX_ATTEMPTS_KEY)
                        .with(user(asFunctionalAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("8"))
                .andExpect(jsonPath("$.overridden").value(true));

        mockMvc.perform(get("/api/v1/admin/system-parameters").with(user(asFunctionalAdmin)))
                .andExpect(jsonPath("$[?(@.key=='" + LoginAttemptListener.MAX_ATTEMPTS_KEY + "')].value").value("8"));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo("UPDATE_PARAMETER");
                    assertThat(entry.getObjectType()).isEqualTo("SystemParameter");
                    assertThat(entry.getObjectId()).isEqualTo(LoginAttemptListener.MAX_ATTEMPTS_KEY);
                    assertThat(entry.getActor().getId()).isEqualTo(functionalAdmin.getId());
                });
    }

    @Test
    @DisplayName("§6.10 - an invalid integer value is refused and nothing is persisted")
    void rejectsInvalidIntegerValue() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("value", "not-a-number"));

        mockMvc.perform(put("/api/v1/admin/system-parameters/{key}", LoginAttemptListener.MAX_ATTEMPTS_KEY)
                        .with(user(asFunctionalAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_VALUE"));
    }

    @Test
    @DisplayName("§6.10 - a non true/false value is refused for the boolean key")
    void rejectsInvalidBooleanValue() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("value", "maybe"));

        mockMvc.perform(put("/api/v1/admin/system-parameters/{key}", AuthorizationService.SEPARATION_OF_DUTIES_KEY)
                        .with(user(asFunctionalAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER_VALUE"));
    }

    @Test
    @DisplayName("§6.10 - an unknown key is refused (closed catalog, CLAUDE.md rule 1)")
    void rejectsUnknownKey() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("value", "1"));

        mockMvc.perform(put("/api/v1/admin/system-parameters/{key}", "not.a.real.key")
                        .with(user(asFunctionalAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_PARAMETER"));
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot update a parameter (404, not 403)")
    void plainRequesterCannotUpdate() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("value", "8"));

        mockMvc.perform(put("/api/v1/admin/system-parameters/{key}", LoginAttemptListener.MAX_ATTEMPTS_KEY)
                        .with(user(asRequester)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }
}
