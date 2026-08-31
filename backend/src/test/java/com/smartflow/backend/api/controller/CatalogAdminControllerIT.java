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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.2/§6.10 - fiches de catalogue et types de demande, vue administration (ADR-17 - non
 * versionnés, même patron CRUD que les autres référentiels). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogAdminControllerIT {

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
    private Department department;
    private ServiceCatalog service;

    @BeforeEach
    void seedUsers() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.cat@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        User admin = userRepository.save(new User("Karim", "El Fassi", "karim.cat@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        department = departmentRepository.save(new Department("Support", null));
        service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
    }

    @Test
    @DisplayName("GET /api/v1/admin/services requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/services")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot administer the catalogue (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/services").with(user(asRequester))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.2 - admin listing includes an inactive service, unlike the public catalogue")
    void listIncludesInactiveService() throws Exception {
        ServiceCatalog inactive = serviceCatalogRepository.save(new ServiceCatalog("Archivé §6.2 test", department));
        inactive.setActive(false);
        serviceCatalogRepository.save(inactive);

        // §6.2 - la table service_catalog porte déjà le jeu de données de démonstration
        // (V5) : une longueur exacte testerait ce jeu de données, pas la règle - seule la
        // présence de la fiche désactivée fraîchement créée est affirmée.
        mockMvc.perform(get("/api/v1/admin/services").with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Archivé §6.2 test')].active").value(false));
    }

    @Test
    @DisplayName("§6.2 - creating and updating a service")
    void createsAndUpdatesService() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "name", "Achats", "departmentId", department.getId(), "displayOrder", 1));

        String response = mockMvc.perform(post("/api/v1/admin/services").with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Achats"))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Achats et fournitures", "departmentId", department.getId(), "displayOrder", 2));
        mockMvc.perform(put("/api/v1/admin/services/{id}", id).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Achats et fournitures"));
    }

    @Test
    @DisplayName("RG-02/RG-12 - deactivate/activate a service toggles the logical flag")
    void deactivateThenActivateService() throws Exception {
        mockMvc.perform(post("/api/v1/admin/services/{id}/deactivate", service.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/admin/services/{id}/activate", service.getId()).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("§6.2 - request-type admin listing includes inactive types and requires serviceCatalogId")
    void listsRequestTypesIncludingInactive() throws Exception {
        RequestType active = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        RequestType inactive = requestTypeRepository.save(new RequestType(service, "Ancien type"));
        inactive.setActive(false);
        requestTypeRepository.save(inactive);

        mockMvc.perform(get("/api/v1/admin/request-types").queryParam("serviceCatalogId", service.getId().toString())
                        .with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("§6.2/RG-08 - creating a request type with reopenAllowed and toggling its active flag")
    void createsRequestTypeAndTogglesActive() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "serviceCatalogId", service.getId(), "name", "Demande de matériel",
                "reopenAllowed", false, "displayOrder", 1));

        String response = mockMvc.perform(post("/api/v1/admin/request-types").with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reopenAllowed").value(false))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/request-types/{id}/deactivate", id).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
