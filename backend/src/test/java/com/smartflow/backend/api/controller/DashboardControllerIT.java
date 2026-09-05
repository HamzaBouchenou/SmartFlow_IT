package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.Transition;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
import com.smartflow.backend.infrastructure.repository.TransitionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.9 - "Vue responsable" (volumes, indicateurs) et export CSV, gouvernés par canViewDashboard. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DashboardControllerIT {

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
    private TeamRepository teamRepository;
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
    private TransitionRepository transitionRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private Department department;
    private ServiceCatalog serviceCatalog;
    private RequestType requestType;
    private UserDetails asRequester;
    private User serviceManager;
    private UserDetails asServiceManager;

    @BeforeEach
    void seed() {
        department = departmentRepository.save(new Department("Support", null));
        serviceCatalog = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(serviceCatalog, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));
        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step qualification = stepRepository.save(withOrder(new Step(workflow, "QUALIFICATION", "Qualification"), 1));
        transitionRepository.save(new Transition(qualification, WorkflowAction.CLOSE, null));

        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.dash@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        serviceManager = userRepository.save(new User("Karim", "Manager", "karim.dash@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(serviceManager, Role.SERVICE_MANAGER, ScopeType.DEPARTMENT, department.getId()));
        asServiceManager = new SmartFlowUserDetails(serviceManager, Set.of(Role.SERVICE_MANAGER));
    }

    @Test
    @DisplayName("GET dashboards/service requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/service").param("serviceId", serviceCatalog.getId().toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§6.9 - a REQUESTER (no DEPARTMENT/DIRECTION/GLOBAL scope) cannot view the service dashboard (404, not 403)")
    void requesterCannotViewDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/service").param("serviceId", serviceCatalog.getId().toString()).with(user(asRequester)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.9 - a service manager sees volumes by status and category for submitted requests")
    void serviceManagerSeesVolumes() throws Exception {
        submitAndClose("Clavier cassé");
        createDraftOnly(); // a DRAFT must never count in the dashboard

        mockMvc.perform(get("/api/v1/dashboards/service").param("serviceId", serviceCatalog.getId().toString()).with(user(asServiceManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value(serviceCatalog.getId()))
                .andExpect(jsonPath("$.volumesByStatus.CLOSED").value(1))
                .andExpect(jsonPath("$.volumesByCategory").isNotEmpty())
                .andExpect(jsonPath("$.averageResolutionMinutes").isNumber())
                .andExpect(jsonPath("$.slaComplianceRatePercent").value(100.0))
                .andExpect(jsonPath("$.reopenRatePercent").value(0.0));
    }

    @Test
    @DisplayName("§6.9 - taux de réouverture : demandes rouvertes / demandes déjà clôturées, sur la période")
    void reopenRateCountsReopenedAmongEverClosed() throws Exception {
        Long stillClosedId = createDraft("Clavier cassé");
        mockMvc.perform(post("/api/v1/requests/{id}/submit", stillClosedId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        closeRequest(stillClosedId);

        Long reopenedId = createDraft("Écran cassé");
        mockMvc.perform(post("/api/v1/requests/{id}/submit", reopenedId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        closeRequest(reopenedId);
        mockMvc.perform(post("/api/v1/requests/{id}/reopen", reopenedId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboards/service").param("serviceId", serviceCatalog.getId().toString()).with(user(asServiceManager)))
                .andExpect(status().isOk())
                // 1 sur 2 demandes déjà clôturées a été rouverte - jamais 0.0 codé en dur (ADR-14 est livré).
                .andExpect(jsonPath("$.reopenRatePercent").value(50.0));
    }

    @Test
    @DisplayName("§6.9 - CSV export contains one data row per matching request, excluding drafts")
    void csvExportContainsMatchingRequests() throws Exception {
        submitAndClose("Écran cassé");
        createDraftOnly();

        MvcResult result = mockMvc.perform(get("/api/v1/dashboards/service/export")
                        .param("serviceId", serviceCatalog.getId().toString()).with(user(asServiceManager)))
                .andExpect(status().isOk())
                .andReturn();

        String csv = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String[] lines = csv.strip().split("\n");
        assertThat(lines).hasSize(2); // header + 1 request (the draft never appears)
        assertThat(lines[0]).contains("reference;title;status");
        assertThat(lines[1]).contains("CLOSED");
    }

    private void submitAndClose(String justification) throws Exception {
        Long id = createDraft(justification);
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        closeRequest(id);
    }

    private void closeRequest(Long id) throws Exception {
        String closeBody = objectMapper.writeValueAsString(Map.of("action", "CLOSE", "closureReason", "Résolu"));
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", id).with(user(asServiceManager)).with(csrf())
                        .contentType("application/json").content(closeBody))
                .andExpect(status().isOk());
    }

    private void createDraftOnly() throws Exception {
        createDraft("Brouillon jamais soumis");
    }

    private Long createDraft(String justification) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", justification, "fieldValues", Map.of()));
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
