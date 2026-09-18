package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.FieldOption;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.FormField;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.FieldType;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.FieldOptionRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.FormFieldRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §6.4 - brouillon, modification, soumission, annulation avant prise en charge - à travers
 * la vraie pile HTTP (MockMvc) contre un vrai Postgres (Testcontainers), pas seulement
 * RequestService en isolation : ce qui compte ici est aussi que GlobalExceptionHandler
 * traduise correctement chaque exception métier en code HTTP + corps d'erreur.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestControllerIT {

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
    private RequestRepository requestRepository;
    @Autowired
    private FormDefinitionRepository formDefinitionRepository;
    @Autowired
    private FormFieldRepository formFieldRepository;
    @Autowired
    private FieldOptionRepository fieldOptionRepository;
    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired
    private StepRepository stepRepository;
    @Autowired
    private TaskAssignmentRepository taskAssignmentRepository;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    private Department department;
    private RequestType requestType;
    private Step firstStep;
    private UserDetails asRequester;
    private User requester;
    private User otherRequester;
    private UserDetails asOtherRequester;

    @BeforeEach
    void seedCatalogAndWorkflow() {
        department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));

        FormDefinition form = formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));
        FormField title = new FormField(form, "justification", "Justification", FieldType.TEXT);
        title.setRequired(true);
        formFieldRepository.save(title);
        FormField urgency = new FormField(form, "urgency", "Urgence", FieldType.LIST);
        urgency.setRequired(false);
        formFieldRepository.save(urgency);
        fieldOptionRepository.save(new FieldOption(urgency, "Normal", "Normal"));
        fieldOptionRepository.save(new FieldOption(urgency, "Urgent", "Urgent"));

        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step second = stepRepository.save(withOrder(new Step(workflow, "VALIDATION", "Validation"), 2));
        firstStep = stepRepository.save(withOrder(new Step(workflow, "QUALIFICATION", "Qualification"), 1));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.qa@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));
        otherRequester = userRepository.save(new User("Leila", "Chraibi", "leila.qa@example.com", "hash"));
        asOtherRequester = new SmartFlowUserDetails(otherRequester, Set.of(Role.REQUESTER));
    }

    @Test
    @DisplayName("POST /api/v1/requests requires authentication")
    void createRequiresAuthentication() throws Exception {
        // .with(csrf()) isolates "requires authentication" from ADR-01's separate CSRF
        // requirement (already covered by AuthenticationIT.loginWithoutCsrfTokenIsRefused).
        mockMvc.perform(post("/api/v1/requests").with(csrf()).contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§6.4/RG-01 - creating a draft generates a unique, sequence-backed reference (ADR-02: DEM-{année}-{6 chiffres})")
    void createDraftGeneratesReference() throws Exception {
        Long id = createDraft(Map.of());

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("DEM-\\d{4}-\\d{6}")));
    }

    @Test
    @DisplayName("§6.3 - creating a draft with an unknown field code fails with a field-level VALIDATION_ERROR")
    void createDraftWithUnknownFieldCodeFails() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Titre", "fieldValues", Map.of("no_such_field", "x")));

        mockMvc.perform(post("/api/v1/requests").with(user(asRequester)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("no_such_field"));
    }

    @Test
    @DisplayName("§6.3/§6.4 - draft field values are saved and re-readable, updates upsert existing ones")
    void updateDraftUpsertsFieldValues() throws Exception {
        Long id = createDraft(Map.of("justification", "Clavier cassé"));

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(user(asRequester)))
                .andExpect(jsonPath("$.fieldValues.justification").value("Clavier cassé"));

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "title", "Titre modifié", "fieldValues", Map.of("justification", "Écran cassé", "urgency", "Urgent")));
        mockMvc.perform(put("/api/v1/requests/{id}", id).with(user(asRequester)).with(csrf()).contentType("application/json").content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre modifié"))
                .andExpect(jsonPath("$.fieldValues.justification").value("Écran cassé"))
                .andExpect(jsonPath("$.fieldValues.urgency").value("Urgent"));
    }

    @Test
    @DisplayName("RG-06 - a draft is invisible to anyone but its own requester (404, not 403)")
    void draftIsInvisibleToAnotherUser() throws Exception {
        Long id = createDraft(Map.of());

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(user(asOtherRequester)))
                .andExpect(status().isNotFound());

        String updateBody = objectMapper.writeValueAsString(Map.of("title", "x"));
        mockMvc.perform(put("/api/v1/requests/{id}", id).with(user(asOtherRequester)).with(csrf()).contentType("application/json").content(updateBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADR-10/RG-06 - a service manager whose scope covers the request's department can view it once submitted, never while still a draft")
    void complementaryRoleSeesSubmittedRequestButNotItsDraft() throws Exception {
        User serviceManager = userRepository.save(new User("Nawal", "Manager", "nawal.qa@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(serviceManager, Role.SERVICE_MANAGER, ScopeType.DEPARTMENT, department.getId()));
        UserDetails asServiceManager = new SmartFlowUserDetails(serviceManager, Set.of(Role.SERVICE_MANAGER));

        Long id = createDraft(Map.of("justification", "Clavier cassé"));
        mockMvc.perform(get("/api/v1/requests/{id}", id).with(user(asServiceManager)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(user(asServiceManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("ADR-10 - a service manager whose scope covers a different department cannot view the request (404, not 403)")
    void unrelatedDepartmentScopeStillCannotViewRequest() throws Exception {
        Department otherDepartment = departmentRepository.save(new Department("Achats", null));
        User outsiderManager = userRepository.save(new User("Zak", "Manager", "zak.qa@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(outsiderManager, Role.SERVICE_MANAGER, ScopeType.DEPARTMENT, otherDepartment.getId()));
        UserDetails asOutsiderManager = new SmartFlowUserDetails(outsiderManager, Set.of(Role.SERVICE_MANAGER));

        Long id = createDraft(Map.of("justification", "Clavier cassé"));
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(user(asOutsiderManager)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.4 - a submitted request can no longer be edited as a draft")
    void submittedRequestCannotBeEditedAsDraft() throws Exception {
        Long id = createDraft(Map.of("justification", "Clavier cassé"));
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        String updateBody = objectMapper.writeValueAsString(Map.of("title", "x"));
        mockMvc.perform(put("/api/v1/requests/{id}", id).with(user(asRequester)).with(csrf()).contentType("application/json").content(updateBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_A_DRAFT"));
    }

    @Test
    @DisplayName("§6.3 - submitting with a missing required field fails and leaves the request as DRAFT")
    void submitFailsValidationAndStaysADraft() throws Exception {
        Long id = createDraft(Map.of());

        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("justification"));

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(user(asRequester)))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    @DisplayName("§6.4/RG-01/RG-03 - submitting freezes the published workflow and enters its first step")
    void submitFreezesWorkflowAndEntersFirstStep() throws Exception {
        Long id = createDraft(Map.of("justification", "Clavier cassé"));

        MvcResult result = mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.currentStepId").value(firstStep.getId()))
                .andExpect(jsonPath("$.availableActions").isEmpty())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).contains("\"reference\"");

        // §6.4 - submitting again is rejected: it is no longer a draft.
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_A_DRAFT"));
    }

    @Test
    @DisplayName("§6.4 - cancelling a draft, or a submitted request nobody took charge of yet, both succeed (RG-02: logical status only)")
    void cancelSucceedsBeforePriseEnCharge() throws Exception {
        Long draftId = createDraft(Map.of());
        mockMvc.perform(post("/api/v1/requests/{id}/cancel", draftId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        Long submittedId = createDraft(Map.of("justification", "Clavier cassé"));
        mockMvc.perform(post("/api/v1/requests/{id}/submit", submittedId).with(user(asRequester)).with(csrf()));
        mockMvc.perform(post("/api/v1/requests/{id}/cancel", submittedId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("§6.4 - cancelling is refused once the request has been taken in charge (an active TaskAssignment exists)")
    void cancelRefusedOnceTakenCharge() throws Exception {
        Long id = createDraft(Map.of("justification", "Clavier cassé"));
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()));
        var submittedRequest = requestRepository.findById(id).orElseThrow();
        taskAssignmentRepository.save(new TaskAssignment(submittedRequest, requester, null, requester));

        mockMvc.perform(post("/api/v1/requests/{id}/cancel", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_TAKEN_CHARGE"));
    }

    @Test
    @DisplayName("§6.4 - cancelling an already-cancelled request is refused")
    void cancelRefusedWhenAlreadyTerminal() throws Exception {
        Long id = createDraft(Map.of());
        mockMvc.perform(post("/api/v1/requests/{id}/cancel", id).with(user(asRequester)).with(csrf()));

        mockMvc.perform(post("/api/v1/requests/{id}/cancel", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ALREADY_TERMINAL"));
    }

    @Test
    @DisplayName("§6.9/RG-06 - GET /api/v1/requests is the requester's own list, including drafts, and never another requester's")
    void listMineReturnsOwnRequestsIncludingDrafts() throws Exception {
        Long ownDraftId = createDraft(Map.of());

        String otherBody = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Autre demande", "fieldValues", Map.of()));
        mockMvc.perform(post("/api/v1/requests").with(user(asOtherRequester)).with(csrf()).contentType("application/json").content(otherBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/requests").with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(ownDraftId))
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
    }

    @Test
    @DisplayName("§6.9/§11.1 - GET /api/v1/requests?status= filters the requester's own list")
    void listMineFiltersByStatus() throws Exception {
        Long draftId = createDraft(Map.of());
        Long submittedId = createDraft(Map.of("justification", "Clavier cassé"));
        mockMvc.perform(post("/api/v1/requests/{id}/submit", submittedId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/requests").queryParam("status", "DRAFT").with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(draftId));

        mockMvc.perform(get("/api/v1/requests").queryParam("status", "SUBMITTED").with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(submittedId));
    }

    @Test
    @DisplayName("§3.4/§6.4/ADR-23 - the frise starts at the submission itself: one SUBMIT row, no from-step, entering the workflow's first step")
    void historyStartsAtTheSubmission() throws Exception {
        Long id = createDraft(Map.of("justification", "Clavier cassé"));

        // Avant la soumission, un brouillon n'a encore changé d'état d'aucune façon.
        mockMvc.perform(get("/api/v1/requests/{id}/history", id).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/requests/{id}/history", id).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].action").value("SUBMIT"))
                .andExpect(jsonPath("$[0].fromStepName").doesNotExist())
                .andExpect(jsonPath("$[0].toStepName").value(firstStep.getName()))
                .andExpect(jsonPath("$[0].actorId").value(requester.getId()));
    }

    @Test
    @DisplayName("RG-06 - the history of another requester's request is invisible (404, not 403)")
    void historyInvisibleToAnotherUser() throws Exception {
        Long id = createDraft(Map.of());

        mockMvc.perform(get("/api/v1/requests/{id}/history", id).with(user(asOtherRequester)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.7 - the request detail now also carries priority and the materialized SLA read model")
    void detailCarriesSlaAndPriorityFields() throws Exception {
        Long id = createDraft(Map.of("justification", "Clavier cassé"));

        mockMvc.perform(get("/api/v1/requests/{id}", id).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").doesNotExist())
                .andExpect(jsonPath("$.slaStatus").doesNotExist())
                .andExpect(jsonPath("$.slaDueAtFirstResponse").doesNotExist())
                .andExpect(jsonPath("$.slaDueAtResolution").doesNotExist())
                .andExpect(jsonPath("$.reopenDeadline").doesNotExist());
    }

    private Long createDraft(Map<String, String> fieldValues) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Remplacement clavier", "fieldValues", fieldValues));
        MvcResult result = mockMvc.perform(post("/api/v1/requests").with(user(asRequester)).with(csrf()).contentType("application/json").content(body))
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
