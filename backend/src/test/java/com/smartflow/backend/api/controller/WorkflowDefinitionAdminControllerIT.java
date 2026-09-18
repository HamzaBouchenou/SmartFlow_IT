package com.smartflow.backend.api.controller;

import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.FormField;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.FieldType;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.FormFieldRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
import com.smartflow.backend.infrastructure.repository.TransitionRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §6.5/§10.1/§6.10 - administration versionnée des workflows (ADR-17, docs/DECISIONS.md ;
 * RG-03 ; §14.1 scénario 8). */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WorkflowDefinitionAdminControllerIT {

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
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private ServiceCatalogRepository serviceCatalogRepository;
    @Autowired
    private RequestTypeRepository requestTypeRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private FormDefinitionRepository formDefinitionRepository;
    @Autowired
    private FormFieldRepository formFieldRepository;

    @Autowired
    private TransitionRepository transitionRepository;

    private UserDetails asRequester;
    private UserDetails asAdmin;
    private RequestType requestType;

    @BeforeEach
    void seedCatalog() {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina.wf@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));

        User admin = userRepository.save(new User("Karim", "El Fassi", "karim.wf@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(admin, Role.FUNCTIONAL_ADMIN, ScopeType.GLOBAL, null));
        asAdmin = new SmartFlowUserDetails(admin, Set.of(Role.FUNCTIONAL_ADMIN));

        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));

        FormDefinition form = formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));
        formFieldRepository.save(new FormField(form, "justification", "Justification", FieldType.TEXT));
    }

    @Test
    @DisplayName("GET .../workflow-definitions requires authentication")
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/admin/request-types/{id}/workflow-definitions", requestType.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§11.1 - a plain requester cannot administer workflows (404, not 403)")
    void plainRequesterCannotAdminister() throws Exception {
        mockMvc.perform(get("/api/v1/admin/request-types/{id}/workflow-definitions", requestType.getId()).with(user(asRequester)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("ADR-17 - creating a draft while one already exists is refused")
    void secondConcurrentDraftIsRefused() throws Exception {
        createDraft();

        mockMvc.perform(post("/api/v1/admin/request-types/{id}/workflow-definitions", requestType.getId())
                        .with(user(asAdmin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DRAFT_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("ADR-17 - a workflow with no steps cannot be published")
    void emptyWorkflowCannotBePublished() throws Exception {
        long draftId = createDraft();

        mockMvc.perform(post("/api/v1/admin/workflow-definitions/{id}/publish", draftId).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMPTY_WORKFLOW"));
    }

    @Test
    @DisplayName("UpsertStepRequest.suspendSla is nullable - a step can be created without sending the key at all")
    void addStepWithoutSuspendSlaKeySucceeds() throws Exception {
        // Found by running docs/CAHIER_DE_RECETTE.md's REC-SCN-16 against the real API
        // (not through the SPA, which always sends the key): suspendSla used to be a
        // primitive boolean, and Jackson refused a record whose JSON body omitted it
        // (MALFORMED_REQUEST) instead of defaulting it to false - the exact class of bug
        // CLAUDE.md already documents for ExecuteTransitionRequest/BulkAssignRequest.
        long draftId = createDraft();
        String body = objectMapper.writeValueAsString(Map.of("code", "QUALIFICATION", "name", "Qualification", "displayOrder", 1));

        mockMvc.perform(post("/api/v1/admin/workflow-definitions/{id}/steps", draftId).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.suspendSla").value(false));
    }

    @Test
    @DisplayName("UpsertStepRequest.displayOrder stays mandatory - a clean VALIDATION_ERROR, not MALFORMED_REQUEST, when it is missing")
    void addStepWithoutDisplayOrderIsRejectedCleanly() throws Exception {
        long draftId = createDraft();
        String body = objectMapper.writeValueAsString(Map.of("code", "QUALIFICATION", "name", "Qualification", "suspendSla", false));

        mockMvc.perform(post("/api/v1/admin/workflow-definitions/{id}/steps", draftId).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("displayOrder"));
    }

    @Test
    @DisplayName("§6.5 - a transition targeting a step of another workflow version is refused")
    void crossWorkflowTransitionIsRefused() throws Exception {
        // v1 reste DRAFT ici : le but est d'isoler la garde "même workflow" de la garde
        // "brouillon uniquement" (déjà couverte par publishedVersionIsImmutable) - publier
        // v1 avant ce test ferait échouer sur NOT_A_DRAFT et ne prouverait rien sur
        // CROSS_WORKFLOW_TRANSITION. otherStep appartient au brouillon d'un second type de
        // demande plutôt qu'à une seconde version du même type : ADR-17 limite à un
        // brouillon par type de demande, deux brouillons DRAFT simultanés n'auraient donc
        // été possibles d'aucune autre façon - et la garde elle-même ne se limite pas aux
        // versions d'un même type, elle vaut entre deux WorkflowDefinition quelconques.
        long v1 = createDraft();
        long qualification = addStep(v1, "QUALIFICATION", "Qualification", 1);

        RequestType otherRequestType = requestTypeRepository.save(new RequestType(requestType.getServiceCatalog(), "Autre type"));
        MvcResult otherDraftResult = mockMvc.perform(post("/api/v1/admin/request-types/{id}/workflow-definitions", otherRequestType.getId())
                        .with(user(asAdmin)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        long v2 = objectMapper.readTree(otherDraftResult.getResponse().getContentAsString()).get("id").asLong();
        long otherStep = addStep(v2, "AUTRE", "Autre étape", 1);

        String body = objectMapper.writeValueAsString(Map.of("action", "CLOSE", "toStepId", otherStep));
        mockMvc.perform(post("/api/v1/admin/steps/{id}/transitions", qualification).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CROSS_WORKFLOW_TRANSITION"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUBMIT", "REOPEN"})
    @DisplayName("ADR-23/ADR-14 - an action the application executes itself cannot be wired as a transition")
    void nonConfigurableActionIsRefusedAsTransition(String action) throws Exception {
        long v1 = createDraft();
        long qualification = addStep(v1, "QUALIFICATION", "Qualification", 1);
        long traitement = addStep(v1, "TRAITEMENT", "Traitement", 2);

        String body = objectMapper.writeValueAsString(Map.of("action", action, "toStepId", traitement));
        mockMvc.perform(post("/api/v1/admin/steps/{id}/transitions", qualification).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTION_NOT_CONFIGURABLE"));

        // Et la même garde sur la mise à jour, pas seulement sur la création : sinon une
        // transition légale créée puis rebasculée sur SUBMIT contournerait le contrôle.
        addAssignTransition(qualification, traitement);
        long transitionId = transitionRepository.findByFromStepIdAndAction(qualification, WorkflowAction.ASSIGN)
                .get(0).getId();
        mockMvc.perform(put("/api/v1/admin/transitions/{id}", transitionId).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ACTION_NOT_CONFIGURABLE"));
    }

    @Test
    @DisplayName("ADR-17 - a published version is immutable: adding a step to it is refused")
    void publishedVersionIsImmutable() throws Exception {
        long v1 = createDraft();
        addStep(v1, "QUALIFICATION", "Qualification", 1);
        publish(v1);

        String body = objectMapper.writeValueAsString(Map.of(
                "code", "EXTRA", "name", "Extra", "displayOrder", 2, "suspendSla", false));
        mockMvc.perform(post("/api/v1/admin/workflow-definitions/{id}/steps", v1).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_A_DRAFT"));
    }

    @Test
    @DisplayName("ADR-17 - a never-published draft can be deleted; a published one cannot")
    void deleteDraftOnlyWorksOnDrafts() throws Exception {
        long draftId = createDraft();
        mockMvc.perform(delete("/api/v1/admin/workflow-definitions/{id}", draftId).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isNoContent());

        long v1 = createDraft();
        addStep(v1, "QUALIFICATION", "Qualification", 1);
        publish(v1);

        mockMvc.perform(delete("/api/v1/admin/workflow-definitions/{id}", v1).with(user(asAdmin)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOT_A_DRAFT"));
    }

    @Test
    @DisplayName("RG-03/ADR-17/§14.1 scénario 8 - publier une nouvelle version du workflow n'affecte pas une demande déjà en cours sur l'ancienne")
    void publishingNewVersionNeverAffectsInProgressRequest() throws Exception {
        Team team = teamRepository.save(new Team("Équipe 1", requestType.getServiceCatalog().getDepartment()));
        User agent = userRepository.save(new User("Sara", "Bennis", "sara.wf@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent, Role.AGENT, ScopeType.TEAM, team.getId()));
        UserDetails asAgent = new SmartFlowUserDetails(agent, Set.of(Role.AGENT));

        // v1 - "qualification" (ASSIGN -> "traitement"), "traitement" (CLOSE -> null), publiée.
        long v1 = createDraft();
        long qualificationV1 = addStepWithTeam(v1, "QUALIFICATION", "Qualification", 1, team.getId());
        long traitementV1 = addStepWithTeam(v1, "TRAITEMENT", "Traitement", 2, team.getId());
        addAssignTransition(qualificationV1, traitementV1);
        addCloseTransition(traitementV1);
        publish(v1);

        // La demande est soumise sur v1 (RG-03 - gelée à la soumission) puis prise en charge.
        long requestId = createAndSubmit();
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", requestId).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(objectMapper.writeValueAsString(Map.of("action", "ASSIGN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(traitementV1));

        // v2 est publiée pendant que la demande est en cours sur v1 - graphe volontairement
        // différent (une seule étape) pour que toute confusion entre les deux versions soit
        // immédiatement visible dans les assertions ci-dessous.
        long v2 = createDraft();
        long soloV2 = addStepWithTeam(v2, "SOLO", "Étape unique", 1, team.getId());
        addCloseTransition(soloV2);
        publish(v2);

        mockMvc.perform(get("/api/v1/admin/workflow-definitions/{id}", v1).with(user(asAdmin)))
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        // La demande en cours n'a pas bougé : toujours "traitement" de v1, jamais "solo" de v2.
        mockMvc.perform(get("/api/v1/requests/{id}", requestId).with(user(asAgent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStepId").value(traitementV1))
                .andExpect(jsonPath("$.availableActions[0]").value("CLOSE"));

        // Elle continue de progresser sur le graphe de v1, jamais celui de v2 (CLOSE
        // n'existe sur v2 qu'à partir de "solo", jamais accessible depuis "traitement" de v1).
        String closeBody = objectMapper.writeValueAsString(Map.of(
                "action", "CLOSE", "closureReason", "Résolu", "closureSolution", "Remplacement effectué"));
        mockMvc.perform(post("/api/v1/requests/{id}/transitions", requestId).with(user(asAgent)).with(csrf())
                        .contentType("application/json").content(closeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    private long createAndSubmit() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "requestTypeId", requestType.getId(), "title", "Remplacement clavier",
                "fieldValues", Map.of("justification", "Clavier cassé")));
        MvcResult created = mockMvc.perform(post("/api/v1/requests").with(user(asRequester)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        return id;
    }

    private long createDraft() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/request-types/{id}/workflow-definitions", requestType.getId())
                        .with(user(asAdmin)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long addStep(long workflowDefinitionId, String code, String name, int displayOrder) throws Exception {
        // suspendSla est un boolean primitif du record UpsertStepRequest : jamais omis
        // d'un corps JSON, ce projet le désérialise autrement en 400 MALFORMED_REQUEST
        // plutôt qu'en false implicite.
        String body = objectMapper.writeValueAsString(Map.of(
                "code", code, "name", name, "displayOrder", displayOrder, "suspendSla", false));
        MvcResult result = mockMvc.perform(post("/api/v1/admin/workflow-definitions/{id}/steps", workflowDefinitionId)
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long addStepWithTeam(long workflowDefinitionId, String code, String name, int displayOrder, long teamId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "code", code, "name", name, "displayOrder", displayOrder, "suspendSla", false,
                "responsibleRole", "AGENT", "responsibleTeamId", teamId));
        MvcResult result = mockMvc.perform(post("/api/v1/admin/workflow-definitions/{id}/steps", workflowDefinitionId)
                        .with(user(asAdmin)).with(csrf()).contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void addAssignTransition(long fromStepId, long toStepId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("action", "ASSIGN", "toStepId", toStepId));
        mockMvc.perform(post("/api/v1/admin/steps/{id}/transitions", fromStepId).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private void addCloseTransition(long fromStepId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("action", "CLOSE"));
        mockMvc.perform(post("/api/v1/admin/steps/{id}/transitions", fromStepId).with(user(asAdmin)).with(csrf())
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private void publish(long workflowDefinitionId) throws Exception {
        mockMvc.perform(post("/api/v1/admin/workflow-definitions/{id}/publish", workflowDefinitionId)
                        .with(user(asAdmin)).with(csrf()))
                .andExpect(status().isOk());
    }

    private static FormDefinition withPublished(FormDefinition definition) {
        definition.setStatus(PublicationStatus.PUBLISHED);
        return definition;
    }
}
