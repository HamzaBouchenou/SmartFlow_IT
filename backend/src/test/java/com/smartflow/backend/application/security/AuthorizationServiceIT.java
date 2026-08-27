package com.smartflow.backend.application.security;

import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.SystemParameter;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.Transition;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
import com.smartflow.backend.infrastructure.repository.TransitionRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves AuthorizationService.canAct against a real persisted graph - the pure clauses
 * (RolePermissionRule, ScopeRule, WorkflowActionAvailabilityRule, SeparationOfDutiesRule)
 * already have their own unit tests; what only an integration test can catch is whether
 * this service resolves the right ids from real entities: the Department ancestor chain
 * (DIRECTION scope), the active TaskAssignment (TEAM scope), and the current step's real
 * Transitions (etapeAutoriseAction) - @Transactional per SlaSuspensionServiceIT's pattern.
 */
@Testcontainers
@SpringBootTest
@Transactional
class AuthorizationServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AuthorizationService authorizationService;
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
    private TaskAssignmentRepository taskAssignmentRepository;
    @Autowired
    private RequestRepository requestRepository;
    @Autowired
    private SystemParameterRepository systemParameterRepository;

    @Test
    @DisplayName("grants when role permits the action, DEPARTMENT scope covers the request's service, and the step offers the action")
    void grantsWithDepartmentScope() {
        Department direction = departmentRepository.save(new Department("Direction IT", null));
        Department service = departmentRepository.save(new Department("Support", direction));
        Request request = aSubmittedRequest(service, WorkflowAction.VALIDATE, "ada1");
        User manager = userRepository.save(new User("Nadia", "Manager", "nadia@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(manager, Role.MANAGER, ScopeType.DEPARTMENT, service.getId()));

        boolean result = authorizationService.canAct(manager, request, WorkflowAction.VALIDATE);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("§5.1 - refuses a requester validating their own request while separation of duties is enabled")
    void refusesSelfValidationWhenSeparationOfDutiesEnabled() {
        Department direction = departmentRepository.save(new Department("Direction IT", null));
        Department service = departmentRepository.save(new Department("Support", direction));
        systemParameterRepository.save(new SystemParameter(AuthorizationService.SEPARATION_OF_DUTIES_KEY, "true"));
        User requesterAsManager = userRepository.save(new User("Sami", "Requester", "sami@example.com", "hash"));
        Request request = aSubmittedRequest(service, WorkflowAction.VALIDATE, requesterAsManager, "DEM-2026-000010");
        userRoleAssignmentRepository.save(new UserRoleAssignment(requesterAsManager, Role.MANAGER, ScopeType.DEPARTMENT, service.getId()));

        boolean result = authorizationService.canAct(requesterAsManager, request, WorkflowAction.VALIDATE);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("defaults separation of duties to enabled when the SystemParameter row is absent (secure default)")
    void separationOfDutiesDefaultsToEnabledWhenUnconfigured() {
        Department direction = departmentRepository.save(new Department("Direction IT", null));
        Department service = departmentRepository.save(new Department("Support", direction));
        // No SystemParameter row saved for AuthorizationService.SEPARATION_OF_DUTIES_KEY.
        User requesterAsManager = userRepository.save(new User("Omar", "Requester", "omar@example.com", "hash"));
        Request request = aSubmittedRequest(service, WorkflowAction.REJECT, requesterAsManager, "DEM-2026-000011");
        userRoleAssignmentRepository.save(new UserRoleAssignment(requesterAsManager, Role.MANAGER, ScopeType.DEPARTMENT, service.getId()));

        boolean result = authorizationService.canAct(requesterAsManager, request, WorkflowAction.REJECT);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("§6.6 - TEAM scope covers via the active TaskAssignment even when it differs from the step's own responsible team")
    void grantsWithTeamScopeViaActiveAssignment() {
        Department direction = departmentRepository.save(new Department("Direction IT", null));
        Department service = departmentRepository.save(new Department("Support", direction));
        Team stepTeam = teamRepository.save(new Team("Equipe Etape", service));
        Team assignedTeam = teamRepository.save(new Team("Equipe Affectee", service));
        RequestType requestType = aRequestType(service);
        Step step = aStepWithTransition(aWorkflowDefinition(requestType), WorkflowAction.CLOSE, stepTeam);
        User requester = userRepository.save(new User("Rita", "Requester", "rita@example.com", "hash"));
        Request request = aRequest(requestType, requester, "DEM-2026-000012");
        request.setCurrentStep(step);
        request = requestRepository.save(request);
        User agentBoss = userRepository.save(new User("Boss", "Assign", "boss@example.com", "hash"));
        User agent = userRepository.save(new User("Yanis", "Agent", "yanis@example.com", "hash"));
        taskAssignmentRepository.save(new TaskAssignment(request, agent, assignedTeam, agentBoss));
        userRoleAssignmentRepository.save(new UserRoleAssignment(agent, Role.AGENT, ScopeType.TEAM, assignedTeam.getId()));

        boolean result = authorizationService.canAct(agent, request, WorkflowAction.CLOSE);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("§4.1 - DIRECTION scope covers through the department ancestor chain, not just the immediate service")
    void grantsWithDirectionScopeViaAncestorChain() {
        Department direction = departmentRepository.save(new Department("Direction IT", null));
        Department service = departmentRepository.save(new Department("Support", direction));
        Request request = aSubmittedRequest(service, WorkflowAction.CLOSE, "ada2");
        User serviceManager = userRepository.save(new User("Dee", "Director", "dee@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(serviceManager, Role.SERVICE_MANAGER, ScopeType.DIRECTION, direction.getId()));

        boolean result = authorizationService.canAct(serviceManager, request, WorkflowAction.CLOSE);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("etapeAutoriseAction - refuses an action the current step does not offer as a Transition, even with full role and scope")
    void refusesActionNotOfferedByCurrentStep() {
        Department direction = departmentRepository.save(new Department("Direction IT", null));
        Department service = departmentRepository.save(new Department("Support", direction));
        // Step only offers VALIDATE, never CLOSE.
        Request request = aSubmittedRequest(service, WorkflowAction.VALIDATE, "ada3");
        User manager = userRepository.save(new User("Karim", "Manager", "karim@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(manager, Role.MANAGER, ScopeType.GLOBAL, null));

        boolean result = authorizationService.canAct(manager, request, WorkflowAction.CLOSE);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("refuses when the acting user holds no UserRoleAssignment at all")
    void refusesWithNoRoleAssignment() {
        Department direction = departmentRepository.save(new Department("Direction IT", null));
        Department service = departmentRepository.save(new Department("Support", direction));
        Request request = aSubmittedRequest(service, WorkflowAction.VALIDATE, "ada4");
        User bystander = userRepository.save(new User("Noe", "Bystander", "noe@example.com", "hash"));

        boolean result = authorizationService.canAct(bystander, request, WorkflowAction.VALIDATE);

        assertThat(result).isFalse();
    }

    private RequestType aRequestType(Department service) {
        ServiceCatalog serviceCatalog = serviceCatalogRepository.save(new ServiceCatalog("Support catalog", service));
        return requestTypeRepository.save(new RequestType(serviceCatalog, "Incident"));
    }

    private WorkflowDefinition aWorkflowDefinition(RequestType requestType) {
        return workflowDefinitionRepository.save(new WorkflowDefinition(requestType, 1));
    }

    private Step aStepWithTransition(WorkflowDefinition workflowDefinition, WorkflowAction action, Team responsibleTeam) {
        Step step = new Step(workflowDefinition, action.name(), "Etape " + action.name());
        step.setResponsibleTeam(responsibleTeam);
        step = stepRepository.save(step);
        transitionRepository.save(new Transition(step, action, null));
        return step;
    }

    private Request aRequest(RequestType requestType, User requester, String reference) {
        return requestRepository.save(new Request(reference, requestType, requester, "Test request"));
    }

    /** A submitted request, with a fresh requester, sitting in a step that offers exactly one Transition: offeredAction. */
    private Request aSubmittedRequest(Department service, WorkflowAction offeredAction, String requesterEmailPrefix) {
        User requester = userRepository.save(new User("Ada", "Lovelace", requesterEmailPrefix + "@example.com", "hash"));
        return aSubmittedRequest(service, offeredAction, requester, "DEM-2026-" + offeredAction);
    }

    /** Same as above, but the requester and reference are given - lets a test act as its own requester. */
    private Request aSubmittedRequest(Department service, WorkflowAction offeredAction, User requester, String reference) {
        RequestType requestType = aRequestType(service);
        Step step = aStepWithTransition(aWorkflowDefinition(requestType), offeredAction, null);
        Request request = aRequest(requestType, requester, reference);
        request.setCurrentStep(step);
        return requestRepository.save(request);
    }
}
