package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.WorkflowDefinitionRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §6.4 - "Ajout de commentaires... autorisés". ADR-11 (docs/DECISIONS.md) - lecture comme
 * getDetail (ADR-10), écriture restreinte par canAnnotate (jamais AUDITOR).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CommentControllerIT {

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
    private DepartmentRepository departmentRepository;
    @Autowired
    private ServiceCatalogRepository serviceCatalogRepository;
    @Autowired
    private RequestTypeRepository requestTypeRepository;
    @Autowired
    private FormDefinitionRepository formDefinitionRepository;
    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired
    private StepRepository stepRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private Department department;
    private RequestType requestType;
    private UserDetails asRequester;
    private User requester;

    @BeforeEach
    void seed() {
        department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        FormDefinition form = formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));
        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step step = stepRepository.save(withOrder(new Step(workflow, "QUALIFICATION", "Qualification"), 1));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.comments@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));
    }

    @Test
    @DisplayName("POST comments requires authentication")
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/comments", 999L).with(csrf())
                        .contentType("application/json").content("{\"body\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§6.4 - the requester can comment on their own DRAFT request, and read it back")
    void requesterCanCommentOnOwnDraft() throws Exception {
        Long id = createDraft();

        mockMvc.perform(post("/api/v1/requests/{id}/comments", id).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content("{\"body\":\"Une precision\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.body").value("Une precision"))
                .andExpect(jsonPath("$.authorName").value("Amina Idrissi"));

        mockMvc.perform(get("/api/v1/requests/{id}/comments", id).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Une precision"));
    }

    @Test
    @DisplayName("§6.3 server-side validation - a blank comment body is refused")
    void blankCommentBodyRejected() throws Exception {
        Long id = createDraft();

        mockMvc.perform(post("/api/v1/requests/{id}/comments", id).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content("{\"body\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RG-06/ADR-11 - an unrelated user cannot read or write comments on someone else's draft (404, not 403)")
    void unrelatedUserCannotAccessDraftComments() throws Exception {
        Long id = createDraft();
        User other = userRepository.save(new User("Leila", "Chraibi", "leila.comments@example.com", "hash"));
        UserDetails asOther = new SmartFlowUserDetails(other, Set.of(Role.REQUESTER));

        mockMvc.perform(get("/api/v1/requests/{id}/comments", id).with(user(asOther)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/requests/{id}/comments", id).with(user(asOther)).with(csrf())
                        .contentType("application/json").content("{\"body\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADR-11 - a service manager whose scope covers a submitted request can read and write its comments")
    void complementaryRoleCanAnnotateSubmittedRequest() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        User manager = userRepository.save(new User("Nawal", "Manager", "nawal.comments@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(manager, Role.SERVICE_MANAGER, ScopeType.DEPARTMENT, department.getId()));
        UserDetails asManager = new SmartFlowUserDetails(manager, Set.of(Role.SERVICE_MANAGER));

        mockMvc.perform(post("/api/v1/requests/{id}/comments", id).with(user(asManager)).with(csrf())
                        .contentType("application/json").content("{\"body\":\"Pris en charge\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/v1/requests/{id}/comments", id).with(user(asManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Pris en charge"));
    }

    @Test
    @DisplayName("ADR-11 - an AUDITOR can read a submitted request's comments but cannot write one (404, read-only)")
    void auditorCanReadButNotWriteComments() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/comments", id).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content("{\"body\":\"Note interne\"}"))
                .andExpect(status().isCreated());

        User auditor = userRepository.save(new User("Ines", "Auditor", "ines.comments@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(auditor, Role.AUDITOR, ScopeType.GLOBAL, null));
        UserDetails asAuditor = new SmartFlowUserDetails(auditor, Set.of(Role.AUDITOR));

        mockMvc.perform(get("/api/v1/requests/{id}/comments", id).with(user(asAuditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].body").value("Note interne"));
        mockMvc.perform(post("/api/v1/requests/{id}/comments", id).with(user(asAuditor)).with(csrf())
                        .contentType("application/json").content("{\"body\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    private Long createDraft() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Remplacement clavier", "fieldValues", Map.of()));
        MvcResult result = mockMvc.perform(post("/api/v1/requests").with(user(asRequester)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private static FormDefinition withPublished(FormDefinition definition) {
        definition.setStatus(PublicationStatus.PUBLISHED);
        return definition;
    }

    private static WorkflowDefinition withPublished(WorkflowDefinition definition) {
        definition.setStatus(PublicationStatus.PUBLISHED);
        return definition;
    }

    private static Step withOrder(Step step, int order) {
        step.setDisplayOrder(order);
        return step;
    }
}
