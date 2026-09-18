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
import com.smartflow.backend.infrastructure.ai.AiClient;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-16 (docs/DECISIONS.md) - §12/RG-10. AiClient is mocked (@MockitoBean) so this proves
 * the Spring-side contract - authorization, storage, RG-10's guarantee that nothing here
 * ever writes to Request - without depending on a live Flask process; infrastructure/ai's
 * own kill-switch behavior is covered in isolation by AiClientTest.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AiAnalysisControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @MockitoBean
    private AiClient aiClient;

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
    @Autowired
    private RequestRepository requestRepository;

    private RequestType requestType;
    private UserDetails asRequester;
    private User requester;

    @BeforeEach
    void seed() {
        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));
        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        stepRepository.save(withOrder(new Step(workflow, "QUALIFICATION", "Qualification"), 1));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.ai@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));
    }

    @Test
    @DisplayName("POST analyze requires authentication")
    void analyzeRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/ai/requests/{id}/analyze", 999L).with(csrf())
                        .contentType("application/json").content("{\"analysisType\":\"CLASSIFICATION\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§12.1/RG-10 - CLASSIFICATION stores a suggestion as its own AiAnalysis row, never writes to the Request")
    void classificationStoresSuggestionWithoutTouchingRequest() throws Exception {
        when(aiClient.classify(anyString(), anyString()))
                .thenReturn(new AiClient.ClassificationResult("MATERIEL", 0.9, "HIGH", 0.8, "ml", "rules"));
        Long id = createDraft();

        MvcResult result = mockMvc.perform(post("/api/v1/ai/requests/{id}/analyze", id).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content("{\"analysisType\":\"CLASSIFICATION\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysisType").value("CLASSIFICATION"))
                .andExpect(jsonPath("$.suggestedValue").value(org.hamcrest.Matchers.containsString("MATERIEL")))
                .andExpect(jsonPath("$.acceptedValue").doesNotExist())
                .andExpect(jsonPath("$.validatedById").doesNotExist())
                .andReturn();

        // RG-10 - the request itself is untouched: still a plain DRAFT with no priority set.
        var request = requestRepository.findById(id).orElseThrow();
        assertThat(request.getPriority()).isNull();
        assertThat(request.getTitle()).isEqualTo("Remplacement clavier");

        long analysisId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(get("/api/v1/ai/requests/{id}/analyses", id).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(analysisId));

        // RG-10 - validating only ever updates the AiAnalysis row, still never the Request.
        mockMvc.perform(post("/api/v1/ai/analyses/{id}/validate", analysisId).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content("{\"acceptedValue\":\"{\\\"category\\\":\\\"MATERIEL\\\",\\\"priority\\\":\\\"HIGH\\\"}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedValue").exists())
                .andExpect(jsonPath("$.validatedById").value(requester.getId()));
        assertThat(requestRepository.findById(id).orElseThrow().getPriority()).isNull();
    }

    @Test
    @DisplayName("§12.1 - SUMMARY sends the description and stores the returned summary text")
    void summaryStoresReturnedText() throws Exception {
        when(aiClient.summarize(anyString())).thenReturn("Résumé généré par le service IA.");
        Long id = createDraft();

        mockMvc.perform(post("/api/v1/ai/requests/{id}/analyze", id).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content("{\"analysisType\":\"SUMMARY\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.analysisType").value("SUMMARY"))
                .andExpect(jsonPath("$.suggestedValue").value("Résumé généré par le service IA."));
    }

    @Test
    @DisplayName("ADR-16 - the AI service being disabled (AI_DISABLED from AiClient) surfaces as a clean business error, not a 500")
    void aiClientDisabledSurfacesAsBusinessError() throws Exception {
        when(aiClient.classify(anyString(), anyString())).thenThrow(
                new com.smartflow.backend.domain.exception.InvalidRequestStateException("AI_DISABLED", "Le module d'intelligence artificielle est désactivé (§12.2)."));
        Long id = createDraft();

        mockMvc.perform(post("/api/v1/ai/requests/{id}/analyze", id).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content("{\"analysisType\":\"CLASSIFICATION\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_DISABLED"));
    }

    @Test
    @DisplayName("ADR-16/ADR-11 - an AUDITOR can list a submitted request's AI analyses but cannot request a new one (404, read-only)")
    void auditorCanListButNotAnalyze() throws Exception {
        when(aiClient.classify(anyString(), anyString()))
                .thenReturn(new AiClient.ClassificationResult("MATERIEL", 0.9, "HIGH", 0.8, "ml", "rules"));
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai/requests/{id}/analyze", id).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content("{\"analysisType\":\"CLASSIFICATION\"}"))
                .andExpect(status().isCreated());

        User auditor = userRepository.save(new User("Ines", "Auditor", "ines.ai@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(auditor, Role.AUDITOR, ScopeType.GLOBAL, null));
        UserDetails asAuditor = new SmartFlowUserDetails(auditor, Set.of(Role.AUDITOR));

        mockMvc.perform(get("/api/v1/ai/requests/{id}/analyses", id).with(user(asAuditor)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/ai/requests/{id}/analyze", id).with(user(asAuditor)).with(csrf())
                        .contentType("application/json").content("{\"analysisType\":\"CLASSIFICATION\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("RG-06/ADR-16 - an unrelated user cannot request an analysis on someone else's draft (404, not 403)")
    void unrelatedUserCannotAnalyzeDraft() throws Exception {
        Long id = createDraft();
        User other = userRepository.save(new User("Leila", "Chraibi", "leila.ai@example.com", "hash"));
        UserDetails asOther = new SmartFlowUserDetails(other, Set.of(Role.REQUESTER));

        mockMvc.perform(post("/api/v1/ai/requests/{id}/analyze", id).with(user(asOther)).with(csrf())
                        .contentType("application/json").content("{\"analysisType\":\"CLASSIFICATION\"}"))
                .andExpect(status().isNotFound());
    }

    private Long createDraft() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Remplacement clavier", "description", "Mon écran ne fonctionne plus.",
                "fieldValues", Map.of()));
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
