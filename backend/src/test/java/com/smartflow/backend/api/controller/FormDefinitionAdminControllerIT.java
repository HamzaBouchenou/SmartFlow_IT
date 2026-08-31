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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.3/§10.1/§6.10 - administration versionnée des formulaires (ADR-17, docs/DECISIONS.md). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class FormDefinitionAdminControllerIT {

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
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.form@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        User admin = userRepository.save(new User("Karim", "El Fassi", "karim.form@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
    }

    @Test
    @DisplayName("GET .../form-definitions requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/request-types/{id}/form-definitions", requestType.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot administer forms (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/request-types/{id}/form-definitions", requestType.getId()).with(user(asRequester)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADR-17 - creating a draft while one already exists is refused")
    void secondConcurrentDraftIsRefused() throws Exception {
        createDraft();

        mockMvc.perform(post("/api/v1/admin/request-types/{id}/form-definitions", requestType.getId())
                        .with(user(asAdmin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DRAFT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("§6.3 - fields and options can be added to a DRAFT")
    void addsFieldAndOptionToDraft() throws Exception {
        long draftId = createDraft();
        long fieldId = addField(draftId, "urgency", "Urgence", "LIST");

        String optionBody = objectMapper.writeValueAsString(Map.of("value", "Urgent", "label", "Urgent", "displayOrder", 1));
        mockMvc.perform(post("/api/v1/admin/form-fields/{id}/options", fieldId).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(optionBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.value").value("Urgent"));

        mockMvc.perform(get("/api/v1/admin/form-definitions/{id}", draftId).with(user(asAdmin)))
                .andExpect(jsonPath("$.fields[0].options[0].value").value("Urgent"));
    }

    @Test
    @DisplayName("ADR-17 - a form with no fields cannot be published")
    void emptyFormCannotBePublished() throws Exception {
        long draftId = createDraft();

        mockMvc.perform(post("/api/v1/admin/form-definitions/{id}/publish", draftId).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_FORM"));
    }

    @Test
    @DisplayName("ADR-17/RG-12 - publishing archives the previously published version and becomes the active form (§11.2)")
    void publishArchivesPreviousAndBecomesActive() throws Exception {
        long v1 = createDraft();
        addField(v1, "justification", "Justification", "TEXT");
        mockMvc.perform(post("/api/v1/admin/form-definitions/{id}/publish", v1).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        // §11.2 - GET /request-types/{id}/form sert désormais v1.
        mockMvc.perform(get("/api/v1/request-types/{id}/form", requestType.getId()).with(user(asAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        long v2 = createDraft();
        addField(v2, "justification", "Justification", "TEXT");
        mockMvc.perform(post("/api/v1/admin/form-definitions/{id}/publish", v2).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/admin/form-definitions/{id}", v1).with(user(asAdmin)))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // §11.2 - la même route sert maintenant v2, sans jamais avoir touché v1.
        mockMvc.perform(get("/api/v1/request-types/{id}/form", requestType.getId()).with(user(asAdmin)))
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    @DisplayName("ADR-17 - a published version is immutable: adding a field to it is refused")
    void publishedVersionIsImmutable() throws Exception {
        long v1 = createDraft();
        addField(v1, "justification", "Justification", "TEXT");
        mockMvc.perform(post("/api/v1/admin/form-definitions/{id}/publish", v1).with(user(asAdmin)).with(csrf()));

        String body = objectMapper.writeValueAsString(Map.of(
                "code", "extra", "label", "Extra", "fieldType", "TEXT", "required", false, "displayOrder", 2));
        mockMvc.perform(post("/api/v1/admin/form-definitions/{id}/fields", v1).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_A_DRAFT"));
    }

    @Test
    @DisplayName("ADR-17 - a never-published draft can be deleted; a published one cannot")
    void deleteDraftOnlyWorksOnDrafts() throws Exception {
        long draftId = createDraft();
        mockMvc.perform(delete("/api/v1/admin/form-definitions/{id}", draftId).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isNoContent());

        long v1 = createDraft();
        addField(v1, "justification", "Justification", "TEXT");
        mockMvc.perform(post("/api/v1/admin/form-definitions/{id}/publish", v1).with(user(asAdmin)).with(csrf()));

        mockMvc.perform(delete("/api/v1/admin/form-definitions/{id}", v1).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_A_DRAFT"));
    }

    @Test
    @DisplayName("§6.3 - updating and deleting a field, only while its version stays DRAFT")
    void updatesAndDeletesField() throws Exception {
        long draftId = createDraft();
        long fieldId = addField(draftId, "justification", "Justification", "TEXT");

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "code", "justification", "label", "Motif", "fieldType", "TEXT", "required", true, "displayOrder", 1));
        mockMvc.perform(put("/api/v1/admin/form-fields/{id}", fieldId).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("Motif"))
                .andExpect(jsonPath("$.required").value(true));

        mockMvc.perform(delete("/api/v1/admin/form-fields/{id}", fieldId).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/admin/form-definitions/{id}", draftId).with(user(asAdmin)))
                .andExpect(jsonPath("$.fields.length()").value(0));
    }

    private long createDraft() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/request-types/{id}/form-definitions", requestType.getId())
                        .with(user(asAdmin)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long addField(long draftId, String code, String label, String fieldType) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "code", code, "label", label, "fieldType", fieldType, "required", false, "displayOrder", 1));
        MvcResult result = mockMvc.perform(post("/api/v1/admin/form-definitions/{id}/fields", draftId)
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
