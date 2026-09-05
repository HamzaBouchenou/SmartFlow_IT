package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.LoginRequest;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.AuditLogRepository;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.1/§6.10 - "Gestion des utilisateurs", RG-02 (jamais de suppression physique), ADR-13
 * (verrouillage/déverrouillage). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerIT {

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
    private AuditLogRepository auditLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private User admin;
    private UserDetails asRequester;
    private UserDetails asAdmin;
    private Department department;

    @BeforeEach
    void seedUsers() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.user@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        admin = userRepository.save(new User("Karim", "El Fassi", "karim.user@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        department = departmentRepository.save(new Department("Support", null));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot administer users (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").with(user(asRequester))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.1/RG-11 - creating a user hashes the password (never returned) and is journalized")
    void createsUserAndJournalizes() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Nadia", "lastName", "Amrani", "email", "nadia.amrani@example.com",
                "password", "s3cret-pass", "departmentId", department.getId()));

        String response = mockMvc.perform(post("/api/v1/admin/users").with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("nadia.amrani@example.com"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.locked").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(response).doesNotContain("s3cret-pass");

        User created = userRepository.findByEmail("nadia.amrani@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("s3cret-pass", created.getPasswordHash())).isTrue();

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo("CREATE");
                    assertThat(entry.getObjectType()).isEqualTo("User");
                    assertThat(entry.getActor().getId()).isEqualTo(admin.getId());
                });
    }

    @Test
    @DisplayName("§6.1 - creating a user with an already-used e-mail is refused")
    void rejectsDuplicateEmailOnCreate() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Nadia", "lastName", "Amrani", "email", "karim.user@example.com", "password", "s3cret-pass"));

        mockMvc.perform(post("/api/v1/admin/users").with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    @DisplayName("§6.1 - updating a user's own e-mail to the same value it already has is not refused")
    void updateKeepingOwnEmailSucceeds() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "firstName", "Karim", "lastName", "El Fassi Jr", "email", "karim.user@example.com"));

        mockMvc.perform(put("/api/v1/admin/users/{id}", admin.getId())
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("El Fassi Jr"));
    }

    @Test
    @DisplayName("RG-02 - deactivate/activate toggles the logical flag, never a physical delete")
    void deactivateThenActivate() throws Exception {
        User target = userRepository.save(new User("Nadia", "Amrani", "nadia.deact@example.com", "hash"));

        mockMvc.perform(post("/api/v1/admin/users/{id}/deactivate", target.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(userRepository.findById(target.getId())).isPresent();

        mockMvc.perform(post("/api/v1/admin/users/{id}/activate", target.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("§6.1/§13 - resetting a password lets the user log in with the new one, and the raw value never reaches the audit log")
    void resetPasswordEnablesLoginWithNewPassword() throws Exception {
        User target = userRepository.save(new User("Nadia", "Amrani", "nadia.reset@example.com",
                passwordEncoder.encode("old-password")));

        String body = objectMapper.writeValueAsString(Map.of("newPassword", "brand-new-pass"));
        mockMvc.perform(post("/api/v1/admin/users/{id}/reset-password", target.getId())
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json")
                        .content(objectMapper.writeValueAsString(new LoginRequest("nadia.reset@example.com", "brand-new-pass"))))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .filteredOn(entry -> "RESET_PASSWORD".equals(entry.getAction()))
                .isNotEmpty()
                .allSatisfy(entry -> {
                    // §13 - "aucun mot de passe... dans les logs" : le résumé ne porte
                    // jamais la valeur, qu'il soit renseigné ou (comme ici) absent.
                    if (entry.getSummary() != null) {
                        assertThat(entry.getSummary()).doesNotContain("brand-new-pass");
                    }
                });
    }

    @Test
    @DisplayName("ADR-13 - unlock clears failedLoginAttempts and lockedUntil before the natural lockout expiry")
    void unlockClearsLockout() throws Exception {
        User target = userRepository.save(new User("Nadia", "Amrani", "nadia.unlock@example.com", "hash"));
        target.setFailedLoginAttempts(5);
        target.setLockedUntil(Instant.now().plusSeconds(900));
        userRepository.save(target);

        mockMvc.perform(post("/api/v1/admin/users/{id}/unlock", target.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));

        User reloaded = userRepository.findById(target.getId()).orElseThrow();
        assertThat(reloaded.getFailedLoginAttempts()).isZero();
        assertThat(reloaded.getLockedUntil()).isNull();
    }
}
