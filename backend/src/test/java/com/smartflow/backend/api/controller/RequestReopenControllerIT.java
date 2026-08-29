package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.Request;
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
import com.smartflow.backend.infrastructure.repository.RequestHistoryRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** RG-08/ADR-14 (docs/DECISIONS.md) - réouverture d'une demande clôturée. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestReopenControllerIT {

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
    @Autowired
    private RequestRepository requestRepository;
    @Autowired
    private RequestHistoryRepository requestHistoryRepository;

    private Department department;
    private RequestType requestType;
    private Step qualification;
    private Step traitement;
    private User requester;
    private UserDetails asRequester;
    private User agent;
    private UserDetails asAgent;

    @BeforeEach
    void seedWorkflow() {
        department = departmentRepository.save(new Department("Support", null));
        Team team = teamRepository.save(new Team("Équipe", department));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));

        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        qualification = stepRepository.save(withTeam(new Step(workflow, "QUALIFICATION", "Qualification"), Role.AGENT, team, 1));
        traitement = stepRepository.save(withTeam(new Step(workflow, "TRAITEMENT", "Traitement"), Role.AGENT, team, 2));
        transitionRepository.save(new Transition(qualification, WorkflowAction.ASSIGN, traitement));
        transitionRepository.save(new Transition(traitement, WorkflowAction.CLOSE, null));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.reopen@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        agent = userRepository.save(new User("Sara", "Bennis", "sara.reopen@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent, Role.AGENT, ScopeType.TEAM, team.getId()));
        asAgent = new SmartFlowUserDetails(agent, Set.of(Role.AGENT));
    }

    @Test
    @DisplayName("POST reopen requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/reopen", 999L).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RG-08/ADR-14 - the agent who could close it resumes it exactly at CLOSE's fromStep, and REQUEST_INFO shows in availableActions before then")
    void agentReopensToPreCloseStep() throws Exception {
        Long id = closeARequest();

        mockMvc.perform(post("/api/v1/requests/{id}/reopen", id).with(user(asAgent)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.currentStepId").value(traitement.getId()));

        assertThat(requestHistoryRepository.findByRequestIdOrderByOccurredAtAsc(id))
                .anySatisfy(h -> {
                    assertThat(h.getAction()).isEqualTo(WorkflowAction.REOPEN);
                    assertThat(h.getFromStep()).isNull();
                    assertThat(h.getToStep().getId()).isEqualTo(traitement.getId());
                    assertThat(h.getActor().getId()).isEqualTo(agent.getId());
                });

        Request reloaded = requestRepository.findById(id).orElseThrow();
        assertThat(reloaded.getReopenDeadline()).isNull();
    }

    @Test
    @DisplayName("RG-08/ADR-14 - the requester can always reopen their own request")
    void requesterCanReopenOwnRequest() throws Exception {
        Long id = closeARequest();

        mockMvc.perform(post("/api/v1/requests/{id}/reopen", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("RG-06/ADR-14 - an unrelated user (no role, not the requester) cannot reopen (404, not 403)")
    void unrelatedUserCannotReopen() throws Exception {
        Long id = closeARequest();
        User other = userRepository.save(new User("Leila", "Chraibi", "leila.reopen@example.com", "hash"));
        UserDetails asOther = new SmartFlowUserDetails(other, Set.of(Role.REQUESTER));

        mockMvc.perform(post("/api/v1/requests/{id}/reopen", id).with(user(asOther)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.4 - reopening a request that was never closed is refused")
    void reopeningNonClosedRequestFails() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/requests/{id}/reopen", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_CLOSED"));
    }

    @Test
    @DisplayName("RG-08 - reopening past the deadline is refused")
    void reopeningPastDeadlineFails() throws Exception {
        Long id = closeARequest();
        Request request = requestRepository.findById(id).orElseThrow();
        request.setReopenDeadline(Instant.now().minus(1, ChronoUnit.DAYS));
        requestRepository.save(request);

        mockMvc.perform(post("/api/v1/requests/{id}/reopen", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REOPEN_WINDOW_EXPIRED"));
    }

    @Test
    @DisplayName("RG-08 - reopening is refused when the request type disallows it")
    void reopeningDisallowedByRequestTypeFails() throws Exception {
        Long id = closeARequest();
        RequestType type = requestTypeRepository.findById(requestType.getId()).orElseThrow();
        type.setReopenAllowed(false);
        requestTypeRepository.save(type);

        mockMvc.perform(post("/api/v1/requests/{id}/reopen", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REOPEN_NOT_ALLOWED"));
    }

    private Long closeARequest() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", id).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("action", "ASSIGN"))))
                .andExpect(status().isOk());
        String closeBody = objectMapper.writeValueAsString(Map.of("action", "CLOSE", "closureReason", "Résolu"));
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", id).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
        return id;
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

    private static Step withTeam(Step step, Role role, Team team, int order) {
        step.setResponsibleRole(role);
        step.setResponsibleTeam(team);
        step.setDisplayOrder(order);
        return step;
    }
}
