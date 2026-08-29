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

/**
 * §6.8 - "Centre de notifications... avec statut lu/non lu" et déclenchement à la
 * soumission, l'affectation, la demande de complément, la décision et la clôture. Le canal
 * e-mail (infrastructure/mail) n'est pas vérifiable ici sans serveur SMTP réel : ces tests
 * portent sur le canal applicatif (Notification, toujours écrit de façon synchrone).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NotificationControllerIT {

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

    private RequestType requestType;
    private User requester;
    private UserDetails asRequester;
    private User agent;
    private UserDetails asAgent;
    private User manager;
    private UserDetails asManager;

    @BeforeEach
    void seedWorkflow() {
        Department department = departmentRepository.save(new Department("Support", null));
        Team team = teamRepository.save(new Team("Équipe", department));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));

        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step qualification = stepRepository.save(withTeam(new Step(workflow, "QUALIFICATION", "Qualification"), Role.AGENT, team, 1));
        Step validation = stepRepository.save(withTeam(new Step(workflow, "VALIDATION", "Validation"), Role.MANAGER, team, 2));
        Step traitement = stepRepository.save(withTeam(new Step(workflow, "TRAITEMENT", "Traitement"), Role.AGENT, team, 3));

        transitionRepository.save(new Transition(qualification, WorkflowAction.ASSIGN, validation));
        transitionRepository.save(new Transition(validation, WorkflowAction.VALIDATE, traitement));
        transitionRepository.save(new Transition(traitement, WorkflowAction.REQUEST_INFO, traitement));
        transitionRepository.save(new Transition(traitement, WorkflowAction.CLOSE, null));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.notif@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        agent = userRepository.save(new User("Sara", "Bennis", "sara.notif@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent, Role.AGENT, ScopeType.TEAM, team.getId()));
        asAgent = new SmartFlowUserDetails(agent, Set.of(Role.AGENT));

        manager = userRepository.save(new User("Karim", "El Fassi", "karim.notif@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(manager, Role.MANAGER, ScopeType.TEAM, team.getId()));
        asManager = new SmartFlowUserDetails(manager, Set.of(Role.MANAGER));
    }

    @Test
    @DisplayName("§6.8 - submission, assignment-to-another, decision, info request and closure each notify the right recipient, never the actor")
    void fullLifecycleNotifiesExpectedRecipients() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        // ASSIGN to the manager explicitly (not a self-assign) - only the manager is notified.
        transition(id, asAgent, "ASSIGN", null, manager.getId())
                .andExpect(status().isOk());

        transition(id, asManager, "VALIDATE", "OK", null)
                .andExpect(status().isOk());

        transition(id, asAgent, "REQUEST_INFO", null, null)
                .andExpect(status().isOk());

        String closeBody = objectMapper.writeValueAsString(Map.of("action", "CLOSE", "closureReason", "Résolu"));
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", id).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(closeBody))
                .andExpect(status().isOk());

        // The requester received SUBMISSION, DECISION (VALIDATE), INFO_REQUESTED and CLOSURE - 4 total.
        MvcResult requesterList = mockMvc.perform(get("/api/v1/notifications").with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4))
                .andReturn();
        String requesterTypes = requesterList.getResponse().getContentAsString();
        assertThat(requesterTypes).contains("SUBMISSION").contains("DECISION").contains("INFO_REQUESTED").contains("CLOSURE");
        assertThat(requesterTypes).doesNotContain("ASSIGNMENT");

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user(asRequester)))
                .andExpect(jsonPath("$.count").value(4));

        // Only the manager - not the assigning agent - was notified of the assignment.
        mockMvc.perform(get("/api/v1/notifications").with(user(asManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].type").value("ASSIGNMENT"));
        mockMvc.perform(get("/api/v1/notifications").with(user(asAgent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("§6.8 - a self-assignment (\"prendre en charge\") does not notify the acting agent")
    void selfAssignDoesNotNotifyActor() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        transition(id, asAgent, "ASSIGN", null, null)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user(asAgent)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("§6.8 - marking a notification read updates its status and the unread count")
    void markReadUpdatesStatusAndCount() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        MvcResult list = mockMvc.perform(get("/api/v1/notifications").with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        long notificationId = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("content").get(0).get("id").asLong();

        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(user(asRequester)))
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    @DisplayName("§6.6/RG-06 - marking someone else's notification read is refused (404, not 403)")
    void markReadRefusedForSomeoneElsesNotification() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        MvcResult list = mockMvc.perform(get("/api/v1/notifications").with(user(asRequester)))
                .andReturn();
        long notificationId = objectMapper.readTree(list.getResponse().getContentAsString())
                .get("content").get(0).get("id").asLong();

        mockMvc.perform(post("/api/v1/notifications/{id}/read", notificationId).with(user(asAgent)).with(csrf()))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions transition(Long requestId, UserDetails actor, String action,
                                                                            String comment, Long assignedUserId) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("action", action);
        if (comment != null) {
            payload.put("comment", comment);
        }
        if (assignedUserId != null) {
            payload.put("assignedUserId", assignedUserId);
        }
        String body = objectMapper.writeValueAsString(payload);
        return mockMvc.perform(post("/api/v1/requests/{id}/transitions", requestId).with(user(actor)).with(csrf())
                .contentType("application/json").content(body));
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
