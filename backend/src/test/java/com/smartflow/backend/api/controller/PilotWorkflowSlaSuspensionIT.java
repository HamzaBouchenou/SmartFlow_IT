package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.SlaEventRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
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

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RG-07/ADR-20 - couverture de non-régression pour REC-SCN-22, contre le jeu de données de
 * démonstration réellement migré (Flyway V1-V10) plutôt qu'une fixture montée à la main :
 * ce que le scénario mettait en défaut n'était pas le mécanisme de suspension (déjà testé
 * seul par SlaSuspensionServiceIT/SlaCalculatorTest) mais la configuration des workflows
 * pilotes, qui ne portait aucune étape `suspend_sla = true`. Seules les vraies lignes V5/V10
 * peuvent le prouver.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PilotWorkflowSlaSuspensionIT {

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
    private StepRepository stepRepository;
    @Autowired
    private SlaEventRepository slaEventRepository;

    private UserDetails asAmina;
    private UserDetails asSara;
    private UserDetails asYoussef;

    @BeforeEach
    void loadDemoUsers() {
        asAmina = new SmartFlowUserDetails(
                userRepository.findByEmail("amina.idrissi@smartflow.local").orElseThrow(), Set.of(Role.REQUESTER));
        asSara = new SmartFlowUserDetails(
                userRepository.findByEmail("sara.bennis@smartflow.local").orElseThrow(), Set.of(Role.AGENT));
        asYoussef = new SmartFlowUserDetails(
                userRepository.findByEmail("youssef.amrani@smartflow.local").orElseThrow(), Set.of(Role.MANAGER));
    }

    @Test
    @DisplayName("V10/ADR-20 - REQUEST_INFO depuis TRAITEMENT mène à une étape qui suspend le SLA, et ASSIGN reprend")
    void requestInfoSuspendsTheSlaAndAssignResumesIt() throws Exception {
        long requestId = submitAndQualify();

        // QUALIFICATION -> VALIDATION -> TRAITEMENT, le circuit nominal du workflow pilote.
        mockMvc.perform(transition(requestId, asSara, Map.of("action", "ASSIGN"))).andExpect(status().isOk());
        mockMvc.perform(transition(requestId, asYoussef, Map.of("action", "VALIDATE"))).andExpect(status().isOk());

        // Le complément demandé déplace la demande sur l'étape d'attente (V10), pas sur
        // place comme avant : c'est ce changement d'étape qui déclenche la suspension.
        mockMvc.perform(transition(requestId, asSara, Map.of("action", "REQUEST_INFO", "comment", "Merci de préciser le modèle.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(stepIdOf(requestId, "EN_ATTENTE_INFO")));
        assertThat(eventTypesOf(requestId)).containsExactly(SlaEventType.SUSPENDED);

        // L'agent reprend la main : RESUMED, une seule fois, et la demande revient en TRAITEMENT.
        mockMvc.perform(transition(requestId, asSara, Map.of("action", "ASSIGN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(stepIdOf(requestId, "TRAITEMENT")));
        assertThat(eventTypesOf(requestId)).containsExactly(SlaEventType.SUSPENDED, SlaEventType.RESUMED);
    }

    @Test
    @DisplayName("ADR-20 - l'étape d'attente du workflow pilote porte bien suspend_sla, contrairement à toutes les autres")
    void onlyTheWaitingStepSuspendsTheSla() {
        var itSteps = stepRepository.findAll().stream()
                .filter(step -> step.getWorkflowDefinition().getRequestType().getId() == 1L)
                .toList();

        assertThat(itSteps).filteredOn(step -> step.getCode().equals("EN_ATTENTE_INFO"))
                .singleElement()
                .satisfies(step -> assertThat(step.isSuspendSla()).isTrue());
        assertThat(itSteps).filteredOn(step -> !step.getCode().equals("EN_ATTENTE_INFO"))
                .allSatisfy(step -> assertThat(step.isSuspendSla()).isFalse());
    }

    private List<SlaEventType> eventTypesOf(long requestId) {
        Request request = requestRepository.findById(requestId).orElseThrow();
        return request.getSlaEvents().stream().map(event -> event.getEventType()).toList();
    }

    private long stepIdOf(long requestId, String code) {
        Request request = requestRepository.findById(requestId).orElseThrow();
        return stepRepository.findByWorkflowDefinitionIdOrderByDisplayOrderAsc(request.getWorkflowDefinition().getId())
                .stream()
                .filter(step -> step.getCode().equals(code))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private long submitAndQualify() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", 1, "title", "Écran de remplacement",
                "fieldValues", Map.of("equipment_type", "Écran", "quantity", "1", "urgency", "Normal")));
        MvcResult created = mockMvc.perform(post("/api/v1/requests").with(user(asAmina)).with(csrf())
                        .contentType("application/json").content(createBody))
                .andExpect(status().isCreated())
                .andReturn();
        long requestId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", requestId).with(user(asAmina)).with(csrf()))
                .andExpect(status().isOk());
        // §5/RG-07 - sans priorité, SlaSweepScheduler ignorerait la demande ; qualifier fait
        // partie du parcours normal depuis RequestService.qualify.
        mockMvc.perform(post("/api/v1/requests/{id}/qualify", requestId).with(user(asSara)).with(csrf())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("priority", Priority.MEDIUM.name()))))
                .andExpect(status().isOk());
        return requestId;
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transition(
            long requestId, UserDetails actingAs, Map<String, ?> body) throws Exception {
        return post("/api/v1/requests/{id}/transitions", requestId).with(user(actingAs)).with(csrf())
                .contentType("application/json").content(objectMapper.writeValueAsString(body));
    }
}
