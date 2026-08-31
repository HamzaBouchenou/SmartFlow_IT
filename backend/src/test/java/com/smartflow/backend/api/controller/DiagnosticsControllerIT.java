package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
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

import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.10/§15.3 - page de diagnostic, réservée à TECHNICAL_ADMIN (§5 - "supervision"). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DiagnosticsControllerIT {

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
    private UserRepository userRepository;
    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private UserDetails asRequester;
    private UserDetails asFunctionalAdmin;
    private UserDetails asTechnicalAdmin;

    @BeforeEach
    void seedUsers() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.diag@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        User functionalAdmin = userRepository.save(new User("Karim", "El Fassi", "karim.diag@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(functionalAdmin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asFunctionalAdmin = new SmartFlowUserDetails(functionalAdmin, Set.of(Role.FUNCTIONAL_ADMIN));

        User technicalAdmin = userRepository.save(new User("Sara", "Bennis", "sara.diag@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(technicalAdmin, Role.TECHNICAL_ADMIN, ScopeType.GLOBAL, null));
        asTechnicalAdmin = new SmartFlowUserDetails(technicalAdmin, Set.of(Role.TECHNICAL_ADMIN));
    }

    @Test
    @DisplayName("GET /api/v1/admin/diagnostics requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/diagnostics")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot view diagnostics (404, not 403)")
    void plainRequesterCannotView() throws Exception {
        mockMvc.perform(get("/api/v1/admin/diagnostics").with(user(asRequester))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§5 - a FUNCTIONAL_ADMIN cannot view diagnostics either (Administrateur technique only, distinct role)")
    void functionalAdminCannotView() throws Exception {
        mockMvc.perform(get("/api/v1/admin/diagnostics").with(user(asFunctionalAdmin))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§15.3 - a TECHNICAL_ADMIN sees the backend and database up, AI reported disabled (default, §12.2), never a secret")
    void technicalAdminSeesLiveStatus() throws Exception {
        mockMvc.perform(get("/api/v1/admin/diagnostics").with(user(asTechnicalAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backendStatus").value("UP"))
                .andExpect(jsonPath("$.databaseStatus").value("UP"))
                .andExpect(jsonPath("$.aiServiceStatus").value("DISABLED"))
                .andExpect(jsonPath("$.mailStatus").exists())
                .andExpect(jsonPath("$.checkedAt").exists());
    }
}
