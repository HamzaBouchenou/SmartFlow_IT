package com.smartflow.backend.api.controller;

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
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §4.1/§6.10 - directions et services, RG-02/RG-12 (jamais de suppression physique). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DepartmentControllerIT {

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

    private UserDetails asRequester;
    private User admin;
    private UserDetails asAdmin;
    private Department direction;

    @BeforeEach
    void seedUsers() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.dept@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        admin = userRepository.save(new User("Karim", "El Fassi", "karim.dept@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        direction = departmentRepository.save(new Department("Direction IT", null));
    }

    @Test
    @DisplayName("GET /api/v1/admin/departments requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/departments")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot list or create a department (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/departments").with(user(asRequester))).andExpect(status().isNotFound());

        String body = objectMapper.writeValueAsString(Map.of("name", "Support"));
        mockMvc.perform(post("/api/v1/admin/departments").with(user(asRequester)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§4.1 - creating a service under a direction, RG-11 journalizes it")
    void createsServiceUnderDirectionAndJournalizes() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Support Informatique", "parentId", direction.getId()));

        mockMvc.perform(post("/api/v1/admin/departments").with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Support Informatique"))
                .andExpect(jsonPath("$.parentId").value(direction.getId()))
                .andExpect(jsonPath("$.active").value(true));

        assertThat(auditLogRepository.findAll())
                .anySatisfy(entry -> {
                    assertThat(entry.getAction()).isEqualTo("CREATE");
                    assertThat(entry.getObjectType()).isEqualTo("Department");
                    assertThat(entry.getActor().getId()).isEqualTo(admin.getId());
                });
    }

    @Test
    @DisplayName("§4.1 - creating a department with an unknown parentId is refused (404, not silently null)")
    void unknownParentIsRefused() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Support", "parentId", 999999));

        mockMvc.perform(post("/api/v1/admin/departments").with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§4.1 - updating a department's name")
    void updatesName() throws Exception {
        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("name", "Direction Informatique");
        updateBody.put("parentId", null);
        updateBody.put("leadId", null);
        String body = objectMapper.writeValueAsString(updateBody);

        mockMvc.perform(put("/api/v1/admin/departments/{id}", direction.getId())
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Direction Informatique"));
    }

    @Test
    @DisplayName("RG-02/RG-12 - deactivate/activate toggles the logical flag, never a physical delete")
    void deactivateThenActivate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/departments/{id}/deactivate", direction.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(departmentRepository.findById(direction.getId())).isPresent();

        mockMvc.perform(post("/api/v1/admin/departments/{id}/activate", direction.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
