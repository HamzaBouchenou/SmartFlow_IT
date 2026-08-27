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
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.domain.enums.SlaStatus;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.6 - "Mes tâches" / file d'équipe, avec les filtres statut/priorité/demandeur/catégorie/retard. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TaskQueueControllerIT {

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

    private RequestType requestType;
    private Team team1;
    private User requester1;
    private UserDetails asAgentA;
    private UserDetails asAgentB;
    private UserDetails asRequester1;

    @BeforeEach
    void seed() {
        Department department = departmentRepository.save(new Department("Support", null));
        team1 = teamRepository.save(new Team("Équipe 1", department));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        service.setCategory("IT");
        serviceCatalogRepository.save(service);
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));

        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step qualification = stepRepository.save(withTeam(new Step(workflow, "QUALIFICATION", "Qualification"), team1, 1));
        Step traitement = stepRepository.save(withTeam(new Step(workflow, "TRAITEMENT", "Traitement"), team1, 2));
        transitionRepository.save(new Transition(qualification, WorkflowAction.ASSIGN, traitement));

        requester1 = userRepository.save(new User("Amina", "Idrissi", "amina.tq@example.com", "hash"));
        asRequester1 = new SmartFlowUserDetails(requester1, Set.of(Role.REQUESTER));

        User agentA = userRepository.save(new User("Sara", "Bennis", "sara.tq@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agentA, Role.AGENT, ScopeType.TEAM, team1.getId()));
        asAgentA = new SmartFlowUserDetails(agentA, Set.of(Role.AGENT));

        User agentB = userRepository.save(new User("Karim", "El Fassi", "karim.tq@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agentB, Role.AGENT, ScopeType.TEAM, team1.getId()));
        asAgentB = new SmartFlowUserDetails(agentB, Set.of(Role.AGENT));
    }

    @Test
    @DisplayName("§6.6 - /tasks/mine and /tasks/team require authentication")
    void queuesRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tasks/team")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§6.6 - a self-assigned request appears in the agent's personal queue, not anyone else's")
    void selfAssignedRequestAppearsInMyTasks() throws Exception {
        Long id = createSubmitAndAssign(requester1, "self", null);

        mockMvc.perform(get("/api/v1/tasks/mine").with(user(asAgentA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));

        mockMvc.perform(get("/api/v1/tasks/mine").with(user(asAgentB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("§6.6 - a request assigned to the team appears in every team member's team queue, but nobody's personal queue")
    void teamAssignedRequestAppearsInTeamQueueForEveryMember() throws Exception {
        Long id = createSubmitAndAssign(requester1, "team", team1.getId());

        mockMvc.perform(get("/api/v1/tasks/team").with(user(asAgentA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mockMvc.perform(get("/api/v1/tasks/team").with(user(asAgentB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/v1/tasks/mine").with(user(asAgentA)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("§6.6 - a user with no TEAM-scoped role assignment has an empty team queue, not an error")
    void noTeamAssignmentMeansEmptyTeamQueue() throws Exception {
        mockMvc.perform(get("/api/v1/tasks/team").with(user(asRequester1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("§6.6 - priority filter narrows the personal queue")
    void filtersByPriority() throws Exception {
        Long id = createSubmitAndAssign(requester1, "self", null);
        setPriority(id, Priority.CRITICAL);

        mockMvc.perform(get("/api/v1/tasks/mine").param("priority", "CRITICAL").with(user(asAgentA)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/tasks/mine").param("priority", "LOW").with(user(asAgentA)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("§6.6 - requester filter narrows the personal queue")
    void filtersByRequester() throws Exception {
        Long id = createSubmitAndAssign(requester1, "self", null);

        mockMvc.perform(get("/api/v1/tasks/mine").param("requesterId", requester1.getId().toString()).with(user(asAgentA)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/tasks/mine").param("requesterId", "999999").with(user(asAgentA)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("§6.6 - category filter narrows the personal queue")
    void filtersByCategory() throws Exception {
        createSubmitAndAssign(requester1, "self", null);

        mockMvc.perform(get("/api/v1/tasks/mine").param("category", "IT").with(user(asAgentA)))
                .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/v1/tasks/mine").param("category", "Achats").with(user(asAgentA)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("§6.6 - overdue filter reads the materialized SlaStatus, never recomputes it")
    void filtersByOverdue() throws Exception {
        Long onTrack = createSubmitAndAssign(requester1, "self", null);
        Long overdue = createSubmitAndAssign(requester1, "self", null);
        setSlaStatus(overdue, SlaStatus.OVERDUE);
        setSlaStatus(onTrack, SlaStatus.ON_TRACK);

        mockMvc.perform(get("/api/v1/tasks/mine").param("overdue", "true").with(user(asAgentA)))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(overdue));
    }

    private Long createSubmitAndAssign(User requester, String mode, Long teamId) throws Exception {
        UserDetails asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));
        String createBody = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Remplacement clavier", "fieldValues", Map.of()));
        MvcResult created = mockMvc.perform(post("/api/v1/requests").with(user(asRequester)).with(csrf())
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        Map<String, Object> assignBody = new HashMap<>();
        assignBody.put("action", "ASSIGN");
        if ("team".equals(mode)) {
            assignBody.put("assignedTeamId", teamId);
        }
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", id).with(user(asAgentA)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(assignBody)))
                .andExpect(status().isOk());
        return id;
    }

    private void setPriority(Long requestId, Priority priority) {
        Request request = requestRepository.findById(requestId).orElseThrow();
        request.setPriority(priority);
        requestRepository.save(request);
    }

    private void setSlaStatus(Long requestId, SlaStatus status) {
        Request request = requestRepository.findById(requestId).orElseThrow();
        request.setSlaStatus(status);
        requestRepository.save(request);
    }

    private static WorkflowDefinition withPublished(WorkflowDefinition definition) {
        definition.setStatus(PublicationStatus.PUBLISHED);
        return definition;
    }

    private static FormDefinition withPublished(FormDefinition definition) {
        definition.setStatus(PublicationStatus.PUBLISHED);
        return definition;
    }

    private static Step withTeam(Step step, Team team, int order) {
        step.setResponsibleRole(Role.AGENT);
        step.setResponsibleTeam(team);
        step.setDisplayOrder(order);
        return step;
    }
}
