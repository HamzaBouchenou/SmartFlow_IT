package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.infrastructure.repository.RequestHistoryRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §6.6/ADR-18 (docs/DECISIONS.md) - regression coverage for REC-SCN-10/18/19
 * (docs/CAHIER_DE_RECETTE.md), run against the real migrated demo dataset (Flyway V1-V9)
 * rather than a hand-built fixture like the other *ControllerIT classes in this package:
 * the defect ADR-18 fixes was a gap in the two published pilot workflows' own topology, not
 * in WorkflowTransitionService itself (already covered in isolation by
 * ManualAssignmentControllerIT) - the only way to actually prove it closed is to exercise
 * the real V5/V9 rows.
 *
 * Priority is set directly on the entity rather than through the API: no application code
 * path sets Request.priority today (RequestService never calls setPriority - see this
 * session's other finding, still open). Every other *ControllerIT touching a CRITICAL
 * request does the same for the same reason.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PilotWorkflowReassignmentIT {

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
    private UserRepository userRepository;
    @Autowired
    private RequestRepository requestRepository;
    @Autowired
    private RequestHistoryRepository requestHistoryRepository;

    private UserDetails asAmina;
    private UserDetails asSara;
    private User mehdi;

    @BeforeEach
    void loadDemoUsers() {
        User amina = userRepository.findByEmail("amina.idrissi@smartflow.local").orElseThrow();
        asAmina = new SmartFlowUserDetails(amina, Set.of(Role.REQUESTER));
        User sara = userRepository.findByEmail("sara.bennis@smartflow.local").orElseThrow();
        asSara = new SmartFlowUserDetails(sara, Set.of(Role.AGENT));
        mehdi = userRepository.findByEmail("mehdi.ouazzani@smartflow.local").orElseThrow();
    }

    @Test
    @DisplayName("V9/ADR-18 - once a CRITICAL request reaches TRAITEMENT, an agent can reassign it by name to a specific teammate")
    void namedReassignmentWithinTraitementSucceeds() throws Exception {
        long requestId = submitCriticalItRequest();

        // QUALIFICATION -> TRAITEMENT directly (the pre-existing CRITICAL bypass, transition 1).
        mockMvc.perform(transition(requestId, asSara, Map.of("action", "ASSIGN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(3));

        // V9's new self-loop: Sara hands the dossier to Mehdi by name, still in TRAITEMENT.
        mockMvc.perform(transition(requestId, asSara, Map.of("action", "ASSIGN", "assignedUserId", mehdi.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(3))
                .andExpect(jsonPath("$.assignedUserId").value(mehdi.getId()));

        // RG-04/§3.4 - exactly one RequestHistory row per transition actually executed.
        assertThat(requestHistoryRepository.findByRequestIdOrderByOccurredAtAsc(requestId)).hasSize(2);
    }

    @Test
    @DisplayName("V9/ADR-18 - auto-assign within TRAITEMENT picks the least-loaded of the team's two real agents")
    void autoAssignWithinTraitementPicksLeastLoaded() throws Exception {
        // Give Sara one active TRAITEMENT assignment via self-assign (the default when
        // neither assignedUserId nor assignedTeamId is given), so Mehdi - still untouched -
        // is strictly less loaded than her when the auto-assign below runs.
        long saraLoad = submitCriticalItRequest();
        mockMvc.perform(transition(saraLoad, asSara, Map.of("action", "ASSIGN"))).andExpect(status().isOk());

        long fresh = submitCriticalItRequest();
        mockMvc.perform(transition(fresh, asSara, Map.of("action", "ASSIGN"))).andExpect(status().isOk());

        mockMvc.perform(transition(fresh, asSara, Map.of("action", "ASSIGN", "assignedTeamId", 1, "autoAssign", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedUserId").value(mehdi.getId()));
    }

    private long submitCriticalItRequest() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", 1, "title", "Remplacement clavier cassé",
                "fieldValues", Map.of("equipment_type", "Clavier", "quantity", "1", "urgency", "Urgent")));
        MvcResult created = mockMvc.perform(post("/api/v1/requests").with(user(asAmina)).with(csrf())
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        long requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", requestId).with(user(asAmina)).with(csrf()))
                .andExpect(status().isOk());

        // No application code path sets Request.priority yet (see this session's other,
        // still-open finding) - set it directly, exactly like RequestTransitionControllerIT/
        // TaskQueueControllerIT/HomeDashboardControllerIT already do for the same reason.
        Request request = requestRepository.findById(requestId).orElseThrow();
        request.setPriority(Priority.CRITICAL);
        requestRepository.save(request);
        return requestId;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transition(
            long requestId, UserDetails actingAs, Map<String, ?> body) throws Exception {
        return post("/api/v1/requests/{id}/transitions", requestId).with(user(actingAs)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body));
    }
}
