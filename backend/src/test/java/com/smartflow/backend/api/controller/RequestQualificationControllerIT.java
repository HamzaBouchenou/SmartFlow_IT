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
 * §5/RG-07 - RequestService.qualify, découvert manquant lors de cette session (aucun code
 * ne posait jamais Request.priority avant ce lot - CLAUDE.md). Sans lui, SlaSweepScheduler
 * ignore silencieusement toute demande ("not yet qualified") et RG-07 ne calcule jamais
 * d'échéance pour une demande réelle.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RequestQualificationControllerIT {

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

    private UserDetails asAgent;
    private UserDetails asRequester;
    private UserDetails asOutsiderAgent;
    private Long requestId;

    @BeforeEach
    void seed() throws Exception {
        Department department = departmentRepository.save(new Department("Support", null));
        Department otherDepartment = departmentRepository.save(new Department("Achats", null));
        Team team = teamRepository.save(new Team("Équipe 1", department));
        Team outsiderTeam = teamRepository.save(new Team("Équipe 2", otherDepartment));

        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        RequestType requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));

        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        Step qualification = stepRepository.save(withTeam(new Step(workflow, "QUALIFICATION", "Qualification"), Role.AGENT, team, 1));
        Step traitement = stepRepository.save(withTeam(new Step(workflow, "TRAITEMENT", "Traitement"), Role.AGENT, team, 2));
        transitionRepository.save(new Transition(qualification, WorkflowAction.ASSIGN, traitement));

        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.ma@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        User agent = userRepository.save(new User("Sara", "Bennis", "sara.ma@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent, Role.AGENT, ScopeType.TEAM, team.getId()));
        asAgent = new SmartFlowUserDetails(agent, Set.of(Role.AGENT));

        User outsiderAgent = userRepository.save(new User("Reda", "Bakkali", "reda.ma@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(outsiderAgent, Role.AGENT, ScopeType.TEAM, outsiderTeam.getId()));
        asOutsiderAgent = new SmartFlowUserDetails(outsiderAgent, Set.of(Role.AGENT));

        String createBody = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Remplacement clavier", "fieldValues", Map.of()));
        MvcResult created = mockMvc.perform(post("/api/v1/requests").with(user(asRequester)).with(csrf())
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", requestId).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("§5/RG-07 - an agent eligible on the current step can qualify a submitted request")
    void eligibleAgentCanQualify() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).with(user(asAgent)))
                .andExpect(jsonPath("$.canQualify").value(true))
                .andExpect(jsonPath("$.priority").doesNotExist());

        String body = objectMapper.writeValueAsString(Map.of("priority", "CRITICAL"));
        mockMvc.perform(post("/api/v1/requests/{id}/qualify", requestId).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("CRITICAL"));

        assertThat(auditLogRepository.findAll()).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("QUALIFY");
            assertThat(entry.getObjectId()).isEqualTo(requestId.toString());
        });
    }

    @Test
    @DisplayName("§5/RG-07 - requalifying (changing priority again) stays allowed while SUBMITTED")
    void requalifyingStaysAllowed() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/qualify", requestId).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("priority", "LOW"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("LOW"));

        mockMvc.perform(post("/api/v1/requests/{id}/qualify", requestId).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("priority", "HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));
    }

    @Test
    @DisplayName("§5/RG-07/RG-06 - the requester cannot qualify their own request (canQualify=false, 404 on the call)")
    void requesterCannotQualify() throws Exception {
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).with(user(asRequester)))
                .andExpect(jsonPath("$.canQualify").value(false));

        mockMvc.perform(post("/api/v1/requests/{id}/qualify", requestId).with(user(asRequester)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("priority", "HIGH"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§5/RG-06 - an agent outside the request's scope cannot qualify it (404, never a 403)")
    void outsiderAgentCannotQualify() throws Exception {
        mockMvc.perform(post("/api/v1/requests/{id}/qualify", requestId).with(user(asOutsiderAgent)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("priority", "HIGH"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§5/RG-07 - a draft request cannot be qualified (NOT_SUBMITTED)")
    void draftRequestCannotBeQualified() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestTypeRepository.findAll().get(0).getId(), "title", "Brouillon", "fieldValues", Map.of()));
        MvcResult created = mockMvc.perform(post("/api/v1/requests").with(user(asRequester)).with(csrf())
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        long draftId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/requests/{id}/qualify", draftId).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("priority", "HIGH"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_SUBMITTED"));
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
