package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.AuditLogRepository;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §5.1/§6.10 - affectations de rôle : accorder/révoquer, jamais une désactivation logique. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserRoleAssignmentControllerIT {

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
    private DepartmentRepository departmentRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;

    private User admin;
    private UserDetails asRequester;
    private UserDetails asAdmin;
    private User target;
    private Team team;

    @BeforeEach
    void seedUsers() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.role@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        admin = userRepository.save(new User("Karim", "El Fassi", "karim.role@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        target = userRepository.save(new User("Sara", "Bennis", "sara.role@example.com", "hash"));

        Department department = departmentRepository.save(new Department("Support", null));
        team = teamRepository.save(new Team("Équipe 1", department));
    }

    @Test
    @DisplayName("GET .../role-assignments requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}/role-assignments", target.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot administer role assignments (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/{id}/role-assignments", target.getId()).with(user(asRequester)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§5.1 - granting a TEAM-scoped role resolves the team's name, and is journalized (RG-11)")
    void grantsTeamScopedRoleAndJournalizes() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", "AGENT", "scopeType", "TEAM", "scopeId", team.getId()));

        mockMvc.perform(post("/api/v1/admin/users/{id}/role-assignments", target.getId())
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("AGENT"))
                .andExpect(jsonPath("$.scopeType").value("TEAM"))
                .andExpect(jsonPath("$.scopeName").value("Équipe 1"));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo("GRANT_ROLE");
                    assertThat(entry.getObjectType()).isEqualTo("UserRoleAssignment");
                    assertThat(entry.getActor().getId()).isEqualTo(admin.getId());
                });
    }

    @Test
    @DisplayName("§5.1 - granting a GLOBAL-scoped role needs no scopeId and resolves no scopeName")
    void grantsGlobalScopedRole() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", "AUDITOR", "scopeType", "GLOBAL"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/role-assignments", target.getId())
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.scopeId").doesNotExist())
                .andExpect(jsonPath("$.scopeName").doesNotExist());
    }

    @Test
    @DisplayName("§5.1 - granting a role with an unknown team is refused (404)")
    void unknownTeamIsRefused() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("role", "AGENT", "scopeType", "TEAM", "scopeId", 999999));

        mockMvc.perform(post("/api/v1/admin/users/{id}/role-assignments", target.getId())
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§5.1 - revoking an assignment removes it (physical, no logical flag on UserRoleAssignment) and is journalized")
    void revokeRemovesAssignment() throws Exception {
        UserRoleAssignment assignment = userRoleAssignmentRepository.save(new UserRoleAssignment(target, Role.AGENT, ScopeType.TEAM, team.getId()));

        mockMvc.perform(delete("/api/v1/admin/users/{id}/role-assignments/{assignmentId}", target.getId(), assignment.getId())
                        .with(user(asAdmin)).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(userRoleAssignmentRepository.findById(assignment.getId())).isEmpty();
        assertThat(auditLogRepository.findAll()).anySatisfy(entry -> assertThat(entry.getAction()).isEqualTo("REVOKE_ROLE"));
    }

    @Test
    @DisplayName("§5.1 - revoking an assignment that belongs to another user is refused (404, not silently reachable by id alone)")
    void revokeMismatchedUserIsRefused() throws Exception {
        UserRoleAssignment assignment = userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));

        mockMvc.perform(delete("/api/v1/admin/users/{id}/role-assignments/{assignmentId}", target.getId(), assignment.getId())
                        .with(user(asAdmin)).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(userRoleAssignmentRepository.findById(assignment.getId())).isPresent();
    }
}
