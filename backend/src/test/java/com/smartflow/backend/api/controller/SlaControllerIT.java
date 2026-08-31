package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.7/§6.10 - cibles SLA par (type de demande, priorité). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SlaControllerIT {

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
    private ServiceCatalogRepository serviceCatalogRepository;
    @Autowired
    private RequestTypeRepository requestTypeRepository;

    private UserDetails asRequester;
    private UserDetails asAdmin;
    private RequestType requestType;

    @BeforeEach
    void seedCatalog() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.sla@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        User admin = userRepository.save(new User("Karim", "El Fassi", "karim.sla@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
    }

    @Test
    @DisplayName("GET .../sla requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/request-types/{id}/sla", requestType.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot administer SLA targets (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/request-types/{id}/sla", requestType.getId()).with(user(asRequester)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.7 - listing is empty until a target is upserted, then reflects it")
    void listEmptyThenUpserted() throws Exception {
        mockMvc.perform(get("/api/v1/admin/request-types/{id}/sla", requestType.getId()).with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String body = objectMapper.writeValueAsString(Map.of("firstResponseMinutes", 60, "resolutionMinutes", 480));
        mockMvc.perform(put("/api/v1/admin/request-types/{id}/sla/{priority}", requestType.getId(), "HIGH")
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.firstResponseMinutes").value(60))
                .andExpect(jsonPath("$.resolutionMinutes").value(480));

        mockMvc.perform(get("/api/v1/admin/request-types/{id}/sla", requestType.getId()).with(user(asAdmin)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("§6.7 - upserting an existing (requestType, priority) pair updates it in place, not a duplicate row")
    void upsertUpdatesInPlace() throws Exception {
        String firstBody = objectMapper.writeValueAsString(Map.of("firstResponseMinutes", 60, "resolutionMinutes", 480));
        mockMvc.perform(put("/api/v1/admin/request-types/{id}/sla/{priority}", requestType.getId(), "CRITICAL")
                .with(user(asAdmin)).with(csrf()).contentType("application/json").content(firstBody));

        String secondBody = objectMapper.writeValueAsString(Map.of("firstResponseMinutes", 15, "resolutionMinutes", 120));
        mockMvc.perform(put("/api/v1/admin/request-types/{id}/sla/{priority}", requestType.getId(), "CRITICAL")
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(secondBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstResponseMinutes").value(15));

        mockMvc.perform(get("/api/v1/admin/request-types/{id}/sla", requestType.getId()).with(user(asAdmin)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("§6.7 - a resolution shorter than the first-response target is refused")
    void resolutionShorterThanFirstResponseIsRefused() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("firstResponseMinutes", 120, "resolutionMinutes", 60));

        mockMvc.perform(put("/api/v1/admin/request-types/{id}/sla/{priority}", requestType.getId(), "LOW")
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SLA_TARGETS"));
    }

    @Test
    @DisplayName("§6.7 - an unknown request type is refused (404)")
    void unknownRequestTypeIsRefused() throws Exception {
        mockMvc.perform(get("/api/v1/admin/request-types/{id}/sla", 999999).with(user(asAdmin)))
                .andExpect(status().isNotFound());
    }
}
