package com.smartflow.backend.crosscutting.audit;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.AuditLog;
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
import com.smartflow.backend.infrastructure.repository.AuditLogRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RG-11 - "Les changements de rôle, de statut, d'affectation et de configuration sont
 * inscrits dans le journal d'audit." §13.1 - contenu minimal (acteur, date, action, type
 * d'objet, id, résultat, résumé). Exercé à travers la vraie pile HTTP pour prouver que
 * AuditService.record est bien appelé par les cas d'usage réels, pas seulement testable en
 * isolation.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuditServiceIT {

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
    private AuditLogRepository auditLogRepository;

    private RequestType requestType;
    private User requester;
    private UserDetails asRequester;
    private User agent;
    private UserDetails asAgent;

    @BeforeEach
    void seedWorkflow() {
        Department department = departmentRepository.save(new Department("Support", null));
        Team team = teamRepository.save(new Team("Équipe", department));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));

        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step qualification = stepRepository.save(withTeam(new Step(workflow, "QUALIFICATION", "Qualification"), Role.AGENT, team, 1));
        Step traitement = stepRepository.save(withTeam(new Step(workflow, "TRAITEMENT", "Traitement"), Role.AGENT, team, 2));
        transitionRepository.save(new Transition(qualification, WorkflowAction.ASSIGN, traitement));
        transitionRepository.save(new Transition(traitement, WorkflowAction.CLOSE, null));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.audit@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        agent = userRepository.save(new User("Sara", "Bennis", "sara.audit@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent, Role.AGENT, ScopeType.TEAM, team.getId()));
        asAgent = new SmartFlowUserDetails(agent, Set.of(Role.AGENT));
    }

    @Test
    @DisplayName("RG-11 - SUBMIT writes an audit entry with the acting user, the status change and a traceId")
    void submitWritesAuditEntry() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("SUBMIT");
            assertThat(entry.getObjectType()).isEqualTo("Request");
            assertThat(entry.getObjectId()).isEqualTo(id.toString());
            assertThat(entry.getResult()).isEqualTo("SUCCESS");
            assertThat(entry.getActor().getId()).isEqualTo(requester.getId());
            assertThat(entry.getSummary()).contains("DRAFT").contains("SUBMITTED");
            assertThat(entry.getTraceId()).isNotBlank();
        });
    }

    @Test
    @DisplayName("RG-11 - ASSIGN (affectation) and CLOSE (statut) each write their own audit entry")
    void assignAndCloseWriteAuditEntries() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());

        transition(id, asAgent, "ASSIGN", null).andExpect(status().isOk());
        String closeBody = objectMapper.writeValueAsString(Map.of("action", "CLOSE", "closureReason", "Résolu"));
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", id).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(closeBody))
                .andExpect(status().isOk());

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("ASSIGN");
            assertThat(entry.getActor().getId()).isEqualTo(agent.getId());
            assertThat(entry.getSummary()).contains("assignedUserId=" + agent.getId());
        });
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("CLOSE");
            assertThat(entry.getSummary()).contains("CLOSED");
        });
    }

    private org.springframework.test.web.servlet.ResultActions transition(Long requestId, UserDetails actor, String action,
                                                                            String comment) throws Exception {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("action", action);
        if (comment != null) {
            payload.put("comment", comment);
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
