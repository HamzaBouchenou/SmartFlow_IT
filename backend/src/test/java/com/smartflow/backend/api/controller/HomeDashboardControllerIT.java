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

import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §9.4 (écran Accueil) / §6.9 ("vue demandeur", "vue agent") - GET /api/v1/dashboards/home. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HomeDashboardControllerIT {

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
    private Team team;
    private User requester;
    private UserDetails asRequester;
    private User agent;
    private UserDetails asAgent;
    private UserDetails asManager;

    @BeforeEach
    void seed() {
        Department department = departmentRepository.save(new Department("Support", null));
        team = teamRepository.save(new Team("Équipe support", department));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));
        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step qualification = stepRepository.save(withTeam(new Step(workflow, "QUALIFICATION", "Qualification"), team, 1));
        Step traitement = stepRepository.save(withTeam(new Step(workflow, "TRAITEMENT", "Traitement"), team, 2));
        transitionRepository.save(new Transition(qualification, WorkflowAction.ASSIGN, traitement));
        transitionRepository.save(new Transition(traitement, WorkflowAction.VALIDATE, traitement));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.home@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        agent = userRepository.save(new User("Sara", "Bennis", "sara.home@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent, Role.AGENT, ScopeType.TEAM, team.getId()));
        asAgent = new SmartFlowUserDetails(agent, Set.of(Role.AGENT));

        User manager = userRepository.save(new User("Karim", "Manager", "karim.home@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(manager, Role.MANAGER, ScopeType.TEAM, team.getId()));
        asManager = new SmartFlowUserDetails(manager, Set.of(Role.MANAGER));
    }

    @Test
    @DisplayName("GET dashboards/home requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/home")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§6.9 - un demandeur pur (aucune UserRoleAssignment) n'a pas de section agent")
    void pureRequesterHasNoAgentSection() throws Exception {
        Long id = createDraft("Clavier cassé");
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboards/home").with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requester.inProgressCount").value(1))
                .andExpect(jsonPath("$.requester.requests[0].reference").exists())
                .andExpect(jsonPath("$.agent").doesNotExist());
    }

    @Test
    @DisplayName("§6.9 - une ligne UserRoleAssignment REQUESTER/OWN (jeu de données V5) ne déclenche pas de section agent")
    void requesterRoleAssignmentRowStillMeansNoAgentSection() throws Exception {
        // V5__seed_demo_data.sql pose ce genre de ligne à titre illustratif - une régression
        // ici serait invisible tant qu'aucun test ne recrée ce cas précis (contrairement à
        // pureRequesterHasNoAgentSection, qui teste l'absence totale de ligne).
        userRoleAssignmentRepository.save(new UserRoleAssignment(requester, Role.REQUESTER, ScopeType.OWN, null));

        mockMvc.perform(get("/api/v1/dashboards/home").with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent").doesNotExist());
    }

    @Test
    @DisplayName("§6.9 - dernières décisions : VALIDATE sur une des demandes du demandeur")
    void recentDecisionsSurfaceValidateOnOwnRequest() throws Exception {
        Long id = createDraft("Écran cassé");
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        assign(id);
        String validateBody = objectMapper.writeValueAsString(Map.of("action", "VALIDATE"));
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", id).with(user(asManager)).with(csrf())
                        .contentType("application/json").content(validateBody))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboards/home").with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requester.recentDecisions[0].action").value("VALIDATE"));
    }

    @Test
    @DisplayName("§6.9 - vue agent : charge actuelle, éléments en retard et priorités hautes, depuis « Mes tâches »")
    void agentSeesLoadOverdueAndHighPriority() throws Exception {
        Long overdueId = createDraft("Serveur en panne");
        mockMvc.perform(post("/api/v1/requests/{id}/submit", overdueId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        assign(overdueId);
        setSlaStatus(overdueId, SlaStatus.OVERDUE);
        setPriority(overdueId, Priority.CRITICAL);

        mockMvc.perform(get("/api/v1/dashboards/home").with(user(asAgent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent.currentLoad").value(1))
                .andExpect(jsonPath("$.agent.overdue[0].id").value(overdueId))
                .andExpect(jsonPath("$.agent.highPriority[0].id").value(overdueId));
    }

    private void assign(Long id) throws Exception {
        String assignBody = objectMapper.writeValueAsString(Map.of("action", "ASSIGN"));
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", id).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(assignBody))
                .andExpect(status().isOk());
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

    private Long createDraft(String title) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", title, "fieldValues", Map.of()));
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

    private static Step withTeam(Step step, Team team, int order) {
        step.setResponsibleTeam(team);
        step.setDisplayOrder(order);
        return step;
    }
}
