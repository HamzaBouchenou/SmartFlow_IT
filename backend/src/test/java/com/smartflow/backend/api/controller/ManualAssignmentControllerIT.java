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
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §6.6 - "Affectation manuelle à un agent habilité", exécutée via ASSIGN
 * (WorkflowTransitionService.assign) plutôt qu'un endpoint séparé : voir §6.5, "affecter"
 * est l'une des six actions de workflow, pas un concept à part.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ManualAssignmentControllerIT {

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
    private TaskAssignmentRepository taskAssignmentRepository;

    private RequestType requestType;
    private Step validation;
    private Team team1;
    private Team otherDepartmentTeam;
    private UserDetails asAgent1;
    private User agent2;
    private UserDetails asOutsiderAgent;

    @BeforeEach
    void seed() {
        Department department = departmentRepository.save(new Department("Support", null));
        Department otherDepartment = departmentRepository.save(new Department("Achats", null));
        team1 = teamRepository.save(new Team("Équipe 1", department));
        Team outsiderTeam = teamRepository.save(new Team("Équipe 2", department));
        otherDepartmentTeam = teamRepository.save(new Team("Équipe Achats", otherDepartment));

        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));

        // Both steps stay AGENT/team1-responsible on purpose: ASSIGN here hands the request
        // off between agents of the same team (a manager-only next step would make every
        // AGENT target "ineligible" by construction, which is not what these tests probe).
        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step qualification = stepRepository.save(withTeam(new Step(workflow, "QUALIFICATION", "Qualification"), Role.AGENT, team1, 1));
        validation = stepRepository.save(withTeam(new Step(workflow, "TRAITEMENT", "Traitement"), Role.AGENT, team1, 2));
        transitionRepository.save(new Transition(qualification, WorkflowAction.ASSIGN, validation));
        transitionRepository.save(new Transition(qualification, WorkflowAction.REQUEST_INFO, qualification));
        transitionRepository.save(new Transition(validation, WorkflowAction.CLOSE, null));

        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.ma@example.com", "hash"));

        User agent1 = userRepository.save(new User("Sara", "Bennis", "sara.ma@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent1, Role.AGENT, ScopeType.TEAM, team1.getId()));
        asAgent1 = new SmartFlowUserDetails(agent1, Set.of(Role.AGENT));

        agent2 = userRepository.save(new User("Karim", "El Fassi", "karim.ma@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent2, Role.AGENT, ScopeType.TEAM, team1.getId()));

        User outsiderAgent = userRepository.save(new User("Reda", "Bakkali", "reda.ma@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(outsiderAgent, Role.AGENT, ScopeType.TEAM, outsiderTeam.getId()));
        asOutsiderAgent = new SmartFlowUserDetails(outsiderAgent, Set.of(Role.AGENT));

        String createBody = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Remplacement clavier", "fieldValues", Map.of()));
        try {
            var asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));
            MvcResult created = mockMvc.perform(post("/api/v1/requests").with(user(asRequester)).with(csrf())
                            .contentType("application/json").content(createBody))
                    .andExpect(status().isCreated())
                    .andReturn();
            requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
            mockMvc.perform(post("/api/v1/requests/{id}/submit", requestId).with(user(asRequester)).with(csrf()))
                    .andExpect(status().isOk());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Long requestId;

    @Test
    @DisplayName("§6.6 - assigning to a specific eligible agent succeeds and moves the request")
    void assignToSpecificEligibleAgentSucceeds() throws Exception {
        mockMvc.perform(assignRequest(Map.of("assignedUserId", agent2.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(validation.getId()))
                .andExpect(jsonPath("$.assignedUserId").value(agent2.getId()));

        assertThat(taskAssignmentRepository.findByRequestIdAndActiveTrue(requestId))
                .hasValueSatisfying(a -> assertThat(a.getAssignedUser().getId()).isEqualTo(agent2.getId()));
    }

    @Test
    @DisplayName("§6.6 - assigning to a user outside the target step's scope is refused")
    void assignToIneligibleUserFails() throws Exception {
        mockMvc.perform(assignRequest(mapWith("assignedUserId", extractUserIdFromOutsider())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("USER_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("§6.6 - assigning to an unknown user id is a 404")
    void assignToUnknownUserFails() throws Exception {
        mockMvc.perform(assignRequest(Map.of("assignedUserId", 999_999)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.6 - assigning to the responsible team (file d'équipe) leaves the request unassigned to any one person")
    void assignToTeamPutsRequestInTeamQueue() throws Exception {
        mockMvc.perform(assignRequest(Map.of("assignedTeamId", team1.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTeamId").value(team1.getId()))
                .andExpect(jsonPath("$.assignedUserId").doesNotExist());

        assertThat(taskAssignmentRepository.findByRequestIdAndActiveTrue(requestId))
                .hasValueSatisfying(a -> {
                    assertThat(a.getAssignedUser()).isNull();
                    assertThat(a.getAssignedTeam().getId()).isEqualTo(team1.getId());
                });
    }

    @Test
    @DisplayName("§6.6 - assigning to a team outside the request's service department is refused")
    void assignToTeamOutsideServiceDepartmentFails() throws Exception {
        mockMvc.perform(assignRequest(Map.of("assignedTeamId", otherDepartmentTeam.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEAM_NOT_ELIGIBLE"));
    }

    @Test
    @DisplayName("§6.6 - specifying both a user and a team is ambiguous and refused")
    void assignWithBothUserAndTeamFails() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("assignedUserId", agent2.getId());
        body.put("assignedTeamId", team1.getId());

        mockMvc.perform(assignRequest(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AMBIGUOUS_ASSIGNMENT"));
    }

    @Test
    @DisplayName("§6.6 - assignedUserId only applies to ASSIGN, not to any other action")
    void assignedUserIdOnNonAssignActionFails() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("action", "REQUEST_INFO");
        body.put("assignedUserId", agent2.getId());
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", requestId).with(user(asAgent1)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_APPLICABLE"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder assignRequest(Map<String, ?> extra)
            throws Exception {
        Map<String, Object> body = new HashMap<>(extra);
        body.put("action", "ASSIGN");
        return post("/api/v1/requests/{id}/transitions", requestId).with(user(asAgent1)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body));
    }

    private static Map<String, Object> mapWith(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    private Long extractUserIdFromOutsider() {
        return ((SmartFlowUserDetails) asOutsiderAgent).getUser().getId();
    }

    private static WorkflowDefinition withPublished(WorkflowDefinition definition) {
        definition.setStatus(PublicationStatus.PUBLISHED);
        return definition;
    }

    private static FormDefinition withPublished(FormDefinition definition) {
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
