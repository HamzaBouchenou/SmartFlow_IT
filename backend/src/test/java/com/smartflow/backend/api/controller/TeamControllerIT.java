package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §4.1/§6.10 - équipes, cible d'affectation (§6.6). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TeamControllerIT {

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

    private UserDetails asRequester;
    private UserDetails asAdmin;
    private Department department;
    private Department otherDepartment;
    private Team team;

    @BeforeEach
    void seedUsers() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.team@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        User admin = userRepository.save(new User("Karim", "El Fassi", "karim.team@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        department = departmentRepository.save(new Department("Support", null));
        otherDepartment = departmentRepository.save(new Department("Achats", null));
        team = teamRepository.save(new Team("Équipe 1", department));
    }

    @Test
    @DisplayName("GET /api/v1/admin/teams requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/teams")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot administer teams (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/teams").with(user(asRequester))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.6 - creating a team under a department")
    void createsTeam() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Équipe 2", "departmentId", department.getId()));

        mockMvc.perform(post("/api/v1/admin/teams").with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Équipe 2"))
                .andExpect(jsonPath("$.departmentId").value(department.getId()));
    }

    @Test
    @DisplayName("§6.6 - listing teams filters by departmentId")
    void filtersByDepartment() throws Exception {
        teamRepository.save(new Team("Équipe Achats", otherDepartment));

        mockMvc.perform(get("/api/v1/admin/teams").queryParam("departmentId", department.getId().toString()).with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(team.getId()));
    }

    @Test
    @DisplayName("§4.1 - reassigning a team to another department")
    void reassignsDepartment() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", team.getName(), "departmentId", otherDepartment.getId()));

        mockMvc.perform(put("/api/v1/admin/teams/{id}", team.getId())
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentId").value(otherDepartment.getId()));
    }

    @Test
    @DisplayName("RG-02/RG-12 - deactivate/activate toggles the logical flag")
    void deactivateThenActivate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/teams/{id}/deactivate", team.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/admin/teams/{id}/activate", team.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }
}
