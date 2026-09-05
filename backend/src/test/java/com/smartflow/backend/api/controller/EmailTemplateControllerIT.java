package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
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

/** §6.8/§6.10 - modèles d'e-mail, catalogue fermé aux sept NotificationType, seedés en V6. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EmailTemplateControllerIT {

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

    private User admin;
    private UserDetails asRequester;
    private UserDetails asAdmin;

    @BeforeEach
    void seedUsers() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.mail@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        admin = userRepository.save(new User("Karim", "El Fassi", "karim.mail@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));
    }

    @Test
    @DisplayName("GET /api/v1/admin/email-templates requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/email-templates")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot administer e-mail templates (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/email-templates").with(user(asRequester))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.8 - lists exactly the seven NotificationType codes, all configured by the V6 seed")
    void listsAllSevenSeededTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/admin/email-templates").with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[?(@.code=='SUBMISSION')].configured").value(true));
    }

    @Test
    @DisplayName("RG-11/§6.8 - updating a template's subject/body persists it and is journalized")
    void updatePersistsAndJournalizes() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "subject", "Votre demande {{reference}} a été soumise (mise à jour)",
                "bodyHtml", "<p>Nouveau contenu.</p>"));

        mockMvc.perform(put("/api/v1/admin/email-templates/{code}", "SUBMISSION")
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("Votre demande {{reference}} a été soumise (mise à jour)"));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo("UPDATE_EMAIL_TEMPLATE");
                    assertThat(entry.getObjectType()).isEqualTo("EmailTemplate");
                    assertThat(entry.getObjectId()).isEqualTo("SUBMISSION");
                    assertThat(entry.getActor().getId()).isEqualTo(admin.getId());
                });
    }

    @Test
    @DisplayName("§6.8 - an unknown code (no matching NotificationType) is refused")
    void rejectsUnknownCode() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("subject", "x", "bodyHtml", "<p>x</p>"));

        mockMvc.perform(put("/api/v1/admin/email-templates/{code}", "NOT_A_REAL_TYPE")
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_TEMPLATE_CODE"));
    }
}
