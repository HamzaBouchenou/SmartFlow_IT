package com.smartflow.backend.application.service;

import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.SlaEvent;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the JPA cascade actually persists, against a real PostgreSQL - a unit test on
 * SlaSuspensionRule cannot catch a misconfigured cascade (see Request.slaEvents), only a
 * real flush can. @Transactional keeps one session open per test method (so the lazy
 * slaEvents collection stays readable after findById) and rolls each test back, so fixture
 * data such as the user's unique email cannot collide between test methods.
 */
@Testcontainers
@SpringBootTest
@Transactional
class SlaSuspensionServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SlaSuspensionService slaSuspensionService;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private ServiceCatalogRepository serviceCatalogRepository;
    @Autowired
    private RequestTypeRepository requestTypeRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RequestRepository requestRepository;
    @Autowired
    private WorkflowDefinitionRepository workflowDefinitionRepository;
    @Autowired
    private StepRepository stepRepository;

    @Test
    @DisplayName("entering a suspend-SLA step appends a SUSPENDED SlaEvent, actually persisted")
    void onStepEntered_intoSuspendingStep_persistsSuspendedEvent() {
        RequestType requestType = aRequestType();
        Request request = requestRepository.save(aRequest(requestType));
        Step suspendingStep = aStep(aWorkflowDefinition(requestType), true);

        slaSuspensionService.onStepEntered(request, suspendingStep, Instant.parse("2026-08-25T10:00:00Z"));

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getSlaEvents()).extracting(SlaEvent::getEventType)
                .containsExactly(SlaEventType.SUSPENDED);
    }

    @Test
    @DisplayName("leaving a suspend-SLA step while suspended appends RESUMED after the earlier SUSPENDED")
    void onStepEntered_leavingSuspendingStepWhileSuspended_persistsResumedEvent() {
        RequestType requestType = aRequestType();
        Request request = requestRepository.save(aRequest(requestType));
        WorkflowDefinition workflowDefinition = aWorkflowDefinition(requestType);
        Step suspendingStep = aStep(workflowDefinition, true);
        Step normalStep = aStep(workflowDefinition, false);

        slaSuspensionService.onStepEntered(request, suspendingStep, Instant.parse("2026-08-25T10:00:00Z"));
        Request afterSuspend = requestRepository.findById(request.getId()).orElseThrow();
        slaSuspensionService.onStepEntered(afterSuspend, normalStep, Instant.parse("2026-08-25T10:30:00Z"));

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getSlaEvents()).extracting(SlaEvent::getEventType)
                .containsExactly(SlaEventType.SUSPENDED, SlaEventType.RESUMED);
    }

    @Test
    @DisplayName("entering a non-suspending step while not suspended appends nothing")
    void onStepEntered_intoNonSuspendingStepWhileNotSuspended_persistsNothing() {
        RequestType requestType = aRequestType();
        Request request = requestRepository.save(aRequest(requestType));
        Step normalStep = aStep(aWorkflowDefinition(requestType), false);

        slaSuspensionService.onStepEntered(request, normalStep, Instant.parse("2026-08-25T10:00:00Z"));

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getSlaEvents()).isEmpty();
    }

    private RequestType aRequestType() {
        Department department = departmentRepository.save(new Department("IT", null));
        ServiceCatalog serviceCatalog = serviceCatalogRepository.save(new ServiceCatalog("Support", department));
        return requestTypeRepository.save(new RequestType(serviceCatalog, "Incident"));
    }

    private Request aRequest(RequestType requestType) {
        User requester = userRepository.save(new User("Ada", "Lovelace", "ada@example.com", "hash"));
        return new Request("DEM-2026-000001", requestType, requester, "Test request");
    }

    private WorkflowDefinition aWorkflowDefinition(RequestType requestType) {
        return workflowDefinitionRepository.save(new WorkflowDefinition(requestType, 1));
    }

    private Step aStep(WorkflowDefinition workflowDefinition, boolean suspendSla) {
        Step step = new Step(workflowDefinition, suspendSla ? "WAITING" : "IN_PROGRESS", "Step");
        step.setSuspendSla(suspendSla);
        return stepRepository.save(step);
    }
}
