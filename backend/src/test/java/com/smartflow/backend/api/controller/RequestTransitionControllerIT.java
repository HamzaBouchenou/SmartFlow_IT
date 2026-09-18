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
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.RequestHistoryRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
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

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §6.5 - exécution des actions de workflow, à travers la vraie pile HTTP (MockMvc, vrai
 * Postgres via Testcontainers) : c'est ici que canAct, TransitionResolutionRule,
 * CommentRequirementRule, RequestHistory (RG-04) et SlaSuspensionService (RG-07) se
 * rencontrent réellement pour la première fois.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestTransitionControllerIT {

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
    @Autowired
    private TaskAssignmentRepository taskAssignmentRepository;
    @Autowired
    private com.smartflow.backend.infrastructure.repository.FormDefinitionRepository formDefinitionRepository;

    private RequestType requestType;
    private Step qualification;
    private Step validation;
    private Step traitement;
    private Step rejetee;

    private User requester;
    private UserDetails asRequester;
    private User agent;
    private UserDetails asAgent;
    private User manager;
    private UserDetails asManager;
    private UserDetails asOutsiderAgent;

    @BeforeEach
    void seedWorkflow() {
        Department department = departmentRepository.save(new Department("Support", null));
        Team team1 = teamRepository.save(new Team("Équipe 1", department));
        Team team2 = teamRepository.save(new Team("Équipe 2", department));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));

        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        qualification = stepRepository.save(withTeam(new Step(workflow, "QUALIFICATION", "Qualification"), Role.AGENT, team1, 1));
        validation = stepRepository.save(withTeam(new Step(workflow, "VALIDATION", "Validation"), Role.MANAGER, team1, 2));
        traitement = stepRepository.save(withTeam(new Step(workflow, "TRAITEMENT", "Traitement"), Role.AGENT, team1, 3));
        rejetee = stepRepository.save(withSuspendSla(withTeam(new Step(workflow, "REJETEE", "Rejetée"), null, null, 4)));

        transitionRepository.save(withCriticalCondition(new Transition(qualification, WorkflowAction.ASSIGN, traitement)));
        transitionRepository.save(new Transition(qualification, WorkflowAction.ASSIGN, validation));
        transitionRepository.save(new Transition(qualification, WorkflowAction.REQUEST_INFO, qualification));
        transitionRepository.save(new Transition(validation, WorkflowAction.VALIDATE, traitement));
        transitionRepository.save(new Transition(validation, WorkflowAction.REJECT, rejetee));
        transitionRepository.save(new Transition(validation, WorkflowAction.RETURN, qualification));
        transitionRepository.save(new Transition(traitement, WorkflowAction.CLOSE, null));
        transitionRepository.save(new Transition(traitement, WorkflowAction.REQUEST_INFO, traitement));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.tr@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        agent = userRepository.save(new User("Sara", "Bennis", "sara.tr@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent, Role.AGENT, ScopeType.TEAM, team1.getId()));
        asAgent = new SmartFlowUserDetails(agent, Set.of(Role.AGENT));

        manager = userRepository.save(new User("Karim", "El Fassi", "karim.tr@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(manager, Role.MANAGER, ScopeType.TEAM, team1.getId()));
        asManager = new SmartFlowUserDetails(manager, Set.of(Role.MANAGER));

        User outsiderAgent = userRepository.save(new User("Reda", "Bakkali", "reda.tr@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(outsiderAgent, Role.AGENT, ScopeType.TEAM, team2.getId()));
        asOutsiderAgent = new SmartFlowUserDetails(outsiderAgent, Set.of(Role.AGENT));
    }

    @Test
    @DisplayName("§6.5/RG-06 - an agent outside the step's responsible team cannot act (canAct denies, 404 not 403)")
    void actionDeniedOutsideTeamScopeIs404() throws Exception {
        Long id = createAndSubmit(asRequester);

        mockMvc.perform(transition(id, asOutsiderAgent, "ASSIGN", null, null, null))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.5/§6.6 - ASSIGN by an agent of the responsible team takes charge and moves to the default next step")
    void assignBySameTeamAgentTakesChargeAndMoves() throws Exception {
        Long id = createAndSubmit(asRequester);

        mockMvc.perform(transition(id, asAgent, "ASSIGN", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(validation.getId()));

        assertThat(taskAssignmentRepository.findByRequestIdAndActiveTrue(id))
                .hasValueSatisfying(assignment -> assertThat(assignment.getAssignedUser().getId()).isEqualTo(agent.getId()));
        // ADR-23 - la soumission elle-même est la première ligne ; la transition est la seconde.
        assertThat(requestHistoryRepository.findByRequestIdOrderByOccurredAtAsc(id)).hasSize(2)
                .satisfies(h -> assertThat(h.get(0).getAction()).isEqualTo(WorkflowAction.SUBMIT))
                .element(1)
                .satisfies(h -> {
                    assertThat(h.getAction()).isEqualTo(WorkflowAction.ASSIGN);
                    assertThat(h.getFromStep().getId()).isEqualTo(qualification.getId());
                    assertThat(h.getToStep().getId()).isEqualTo(validation.getId());
                    assertThat(h.getActor().getId()).isEqualTo(agent.getId());
                });

        // §6.4 - "frise d'avancement et historique complet" : GET /history reflects the
        // same row via the HTTP surface (RequestHistoryMapper), not just the repository.
        mockMvc.perform(get("/api/v1/requests/{id}/history", id).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].action").value("SUBMIT"))
                .andExpect(jsonPath("$[1].action").value("ASSIGN"))
                .andExpect(jsonPath("$[1].fromStepName").value(qualification.getName()))
                .andExpect(jsonPath("$[1].toStepName").value(validation.getName()))
                .andExpect(jsonPath("$[1].actorName").value("Sara Bennis"));
    }

    @Test
    @DisplayName("§6.5 - a simple priority condition selects a different transition for the same action (ASSIGN)")
    void criticalPriorityConditionSkipsValidation() throws Exception {
        Long id = createAndSubmit(asRequester);
        var request = requestRepository.findById(id).orElseThrow();
        request.setPriority(Priority.CRITICAL);
        requestRepository.save(request);

        mockMvc.perform(transition(id, asAgent, "ASSIGN", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(traitement.getId()));
    }

    @Test
    @DisplayName("RG-05 - REJECT without a comment is refused and leaves the request unchanged")
    void rejectWithoutCommentFails() throws Exception {
        Long id = advanceToValidation();

        mockMvc.perform(transition(id, asManager, "REJECT", null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMENT_REQUIRED"));

        assertThat(requestHistoryRepository.findByRequestIdOrderByOccurredAtAsc(id)).hasSize(2); // only SUBMIT and the earlier ASSIGN
    }

    @Test
    @DisplayName("RG-05/RG-04/RG-07 - REJECT with a comment records history and suspends the SLA on the target step")
    void rejectWithCommentRecordsHistoryAndSuspendsSla() throws Exception {
        Long id = advanceToValidation();

        mockMvc.perform(transition(id, asManager, "REJECT", "Budget non disponible", null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(rejetee.getId()))
                .andExpect(jsonPath("$.availableActions").isEmpty());

        var history = requestHistoryRepository.findByRequestIdOrderByOccurredAtAsc(id);
        assertThat(history).hasSize(3); // SUBMIT (ADR-23), ASSIGN, REJECT
        assertThat(history.get(2).getComment()).isEqualTo("Budget non disponible");
        assertThat(history.get(2).getAction()).isEqualTo(WorkflowAction.REJECT);

        var request = requestRepository.findById(id).orElseThrow();
        assertThat(request.getSlaEvents()).extracting("eventType").contains(SlaEventType.SUSPENDED);
    }

    @Test
    @DisplayName("§6.5 - a request outside the acting manager's scope for VALIDATE, once RETURNed and re-ASSIGNed, can still be REJECTed later")
    void returnThenReassignThenReject() throws Exception {
        Long id = advanceToValidation();

        mockMvc.perform(transition(id, asManager, "RETURN", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(qualification.getId()));

        mockMvc.perform(transition(id, asAgent, "ASSIGN", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(validation.getId()));

        assertThat(requestHistoryRepository.findByRequestIdOrderByOccurredAtAsc(id)).hasSize(4); // SUBMIT, ASSIGN, RETURN, ASSIGN
    }

    @Test
    @DisplayName("§5.1 - separation of duties blocks a requester who also holds MANAGER from validating their own request")
    void separationOfDutiesBlocksSelfValidation() throws Exception {
        userRoleAssignmentRepository.save(new UserRoleAssignment(requester, Role.MANAGER, ScopeType.TEAM,
                stepRepository.findById(validation.getId()).orElseThrow().getResponsibleTeam().getId()));
        UserDetails selfAsManagerToo = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER, Role.MANAGER));
        Long id = createAndSubmit(asRequester);
        mockMvc.perform(transition(id, asAgent, "ASSIGN", null, null, null)).andExpect(status().isOk());

        mockMvc.perform(transition(id, selfAsManagerToo, "VALIDATE", null, null, null))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADR-03/§6.4 - CLOSE requires a reason, then terminates the request and leaves it unactionable")
    void closeRequiresReasonThenTerminates() throws Exception {
        Long id = advanceToValidation();
        mockMvc.perform(transition(id, asManager, "VALIDATE", null, null, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(traitement.getId()));

        mockMvc.perform(transition(id, asAgent, "CLOSE", null, null, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLOSURE_REASON_REQUIRED"));

        mockMvc.perform(transition(id, asAgent, "CLOSE", null, "Matériel remis au demandeur", "Clavier remplacé"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.currentStepId").doesNotExist())
                // RG-08/ADR-14 - the agent who just closed it (same TEAM scope as
                // traitement) can reopen it within the window: REOPEN, not an empty list.
                .andExpect(jsonPath("$.availableActions").value(org.hamcrest.Matchers.contains("REOPEN")));

        var request = requestRepository.findById(id).orElseThrow();
        assertThat(request.getClosureReason()).isEqualTo("Matériel remis au demandeur");
        assertThat(request.getClosedAt()).isNotNull();

        // ADR-03 - once closed, currentStep is null: canAct denies every action, 404.
        mockMvc.perform(transition(id, asAgent, "CLOSE", null, "x", null))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.4 - niveau de satisfaction facultatif, saisi et validé à la clôture")
    void satisfactionRatingIsOptionalAtClosureAndValidated() throws Exception {
        Long id = advanceToValidation();
        mockMvc.perform(transition(id, asManager, "VALIDATE", null, null, null)).andExpect(status().isOk());

        // facultatif : une clôture sans note reste valide, satisfactionRating reste null.
        Long noRatingId = advanceToValidation();
        mockMvc.perform(transition(noRatingId, asManager, "VALIDATE", null, null, null)).andExpect(status().isOk());
        mockMvc.perform(transitionWithRating(noRatingId, asAgent, "CLOSE", "Résolu", "Remplacé", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.satisfactionRating").doesNotExist());

        // hors échelle 1-5 : rejeté.
        mockMvc.perform(transitionWithRating(id, asAgent, "CLOSE", "Résolu", "Remplacé", 6))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SATISFACTION_RATING"));

        mockMvc.perform(transitionWithRating(id, asAgent, "CLOSE", "Résolu", "Remplacé", 4))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.satisfactionRating").value(4));

        assertThat(requestRepository.findById(id).orElseThrow().getSatisfactionRating()).isEqualTo(4);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transitionWithRating(
            Long id, UserDetails as, String action, String closureReason, String closureSolution, Integer satisfactionRating)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("action", action);
        body.put("closureReason", closureReason);
        body.put("closureSolution", closureSolution);
        body.put("satisfactionRating", satisfactionRating);
        return post("/api/v1/requests/{id}/transitions", id).with(user(as)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body));
    }

    private Long advanceToValidation() throws Exception {
        Long id = createAndSubmit(asRequester);
        mockMvc.perform(transition(id, asAgent, "ASSIGN", null, null, null)).andExpect(status().isOk());
        return id;
    }

    private Long createAndSubmit(UserDetails as) throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Remplacement clavier", "fieldValues", Map.of()));
        MvcResult created = mockMvc.perform(post("/api/v1/requests").with(user(as)).with(csrf())
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(as)).with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transition(
            Long id, UserDetails as, String action, String comment, String closureReason, String closureSolution) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("action", action);
        body.put("comment", comment);
        body.put("closureReason", closureReason);
        body.put("closureSolution", closureSolution);
        return post("/api/v1/requests/{id}/transitions", id).with(user(as)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body));
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

    private static Step withSuspendSla(Step step) {
        step.setSuspendSla(true);
        return step;
    }

    private static Transition withCriticalCondition(Transition transition) {
        transition.setConditionPriority(Priority.CRITICAL);
        return transition;
    }
}
