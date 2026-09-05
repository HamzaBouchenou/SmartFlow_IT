package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.AuditLog;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §6.10/§13.1 - "Journal d'audit consultable avec filtres par utilisateur, action, objet et
 * période", lecture seule, gouvernée par AuthorizationService.canViewAuditLog.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditLogControllerIT {

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
    @Autowired
    private AuditLogRepository auditLogRepository;

    private User requester;
    private UserDetails asRequester;
    private User admin;
    private UserDetails asAdmin;
    private User auditor;
    private UserDetails asAuditor;

    @BeforeEach
    void seedUsers() {
        requester = userRepository.save(new User("Amina", "Idrissi", "amina.audit@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        admin = userRepository.save(new User("Karim", "El Fassi", "karim.audit@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        auditor = userRepository.save(new User("Sara", "Bennis", "sara.audit@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(auditor, Role.AUDITOR, ScopeType.GLOBAL, null));
        asAuditor = new SmartFlowUserDetails(auditor, Set.of(Role.AUDITOR));
    }

    @Test
    @DisplayName("GET /api/v1/admin/audit-log requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-log")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot view the audit log (404, not 403)")
    void plainRequesterCannotView() throws Exception {
        mockMvc.perform(get("/api/v1/admin/audit-log").with(user(asRequester)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§13.1 - a FUNCTIONAL_ADMIN can view the audit log, most recent first")
    void functionalAdminCanView() throws Exception {
        auditLogRepository.save(withSummary(new AuditLog(admin, "SUBMIT", "Request", "1", "SUCCESS"), "a"));
        auditLogRepository.save(withSummary(new AuditLog(admin, "CANCEL", "Request", "2", "SUCCESS"), "b"));

        mockMvc.perform(get("/api/v1/admin/audit-log").with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].actorName").value("Karim El Fassi"));
    }

    @Test
    @DisplayName("§5 - an AUDITOR can also view the audit log (lecture seule)")
    void auditorCanView() throws Exception {
        auditLogRepository.save(new AuditLog(admin, "SUBMIT", "Request", "1", "SUCCESS"));

        mockMvc.perform(get("/api/v1/admin/audit-log").with(user(asAuditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    @DisplayName("RG-12/ADR-15 - a system action (null actor) surfaces actorId/actorName as null, never a fabricated user")
    void systemActionHasNoActor() throws Exception {
        auditLogRepository.save(new AuditLog(null, "ARCHIVE", "Request", "1", "SUCCESS"));

        mockMvc.perform(get("/api/v1/admin/audit-log").with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorId").doesNotExist())
                .andExpect(jsonPath("$.content[0].actorName").doesNotExist());
    }

    @Test
    @DisplayName("§13.1 - filters by actor")
    void filtersByActor() throws Exception {
        auditLogRepository.save(new AuditLog(admin, "SUBMIT", "Request", "1", "SUCCESS"));
        auditLogRepository.save(new AuditLog(auditor, "CANCEL", "Request", "2", "SUCCESS"));

        mockMvc.perform(get("/api/v1/admin/audit-log").queryParam("actorId", admin.getId().toString()).with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actorId").value(admin.getId()));
    }

    @Test
    @DisplayName("§13.1 - filters by action")
    void filtersByAction() throws Exception {
        auditLogRepository.save(new AuditLog(admin, "SUBMIT", "Request", "1", "SUCCESS"));
        auditLogRepository.save(new AuditLog(admin, "CANCEL", "Request", "2", "SUCCESS"));

        mockMvc.perform(get("/api/v1/admin/audit-log").queryParam("action", "CANCEL").with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].action").value("CANCEL"));
    }

    @Test
    @DisplayName("§13.1 - filters by object type")
    void filtersByObjectType() throws Exception {
        auditLogRepository.save(new AuditLog(admin, "UPDATE_PARAMETER", "SystemParameter", "attachments.max-size-bytes", "SUCCESS"));
        auditLogRepository.save(new AuditLog(admin, "SUBMIT", "Request", "1", "SUCCESS"));

        mockMvc.perform(get("/api/v1/admin/audit-log").queryParam("objectType", "SystemParameter").with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].objectType").value("SystemParameter"));
    }

    @Test
    @DisplayName("§13.1 - filters by period, a future occurredFrom excludes every existing entry")
    void filtersByPeriod() throws Exception {
        auditLogRepository.save(new AuditLog(admin, "SUBMIT", "Request", "1", "SUCCESS"));
        Instant future = Instant.now().plus(1, ChronoUnit.DAYS);

        mockMvc.perform(get("/api/v1/admin/audit-log").queryParam("occurredFrom", future.toString()).with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    private static AuditLog withSummary(AuditLog entry, String summary) {
        entry.setSummary(summary);
        return entry;
    }
}
