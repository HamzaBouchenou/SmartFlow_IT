package com.smartflow.backend.application.service;

import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.TaskAssignment;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.domain.enums.SlaEventType;
import com.smartflow.backend.domain.enums.SlaStatus;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.NotificationRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.TaskAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §6.7 - "Notification avant échéance et escalade au responsable en cas de dépassement".
 * Exercises SlaEscalationService directly against a real Postgres/Notification pipeline,
 * with previousStatus/newStatus supplied explicitly rather than through
 * SlaSweepScheduler's own clock-driven computation (domain/rule/SlaThresholdTransitionRule
 * already covers the threshold-crossing logic in isolation).
 */
@Testcontainers
@SpringBootTest
@Transactional
class SlaEscalationServiceIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SlaEscalationService slaEscalationService;
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
    private TaskAssignmentRepository taskAssignmentRepository;
    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("ON_TRACK -> AT_RISK appends WARNING_TRIGGERED and notifies the actively assigned agent")
    void warningNotifiesAssignedAgent() {
        Department department = departmentRepository.save(new Department("Support", null));
        RequestType requestType = aRequestType(department);
        Request request = requestRepository.save(aRequest(requestType));
        User agent = userRepository.save(new User("Sara", "Bennis", "sara.escalation@example.com", "hash"));
        User assigner = userRepository.save(new User("Boss", "Assign", "boss.escalation@example.com", "hash"));
        taskAssignmentRepository.save(new TaskAssignment(request, agent, null, assigner));

        slaEscalationService.onStatusRecomputed(request, SlaStatus.ON_TRACK, SlaStatus.AT_RISK);

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getSlaEvents()).extracting("eventType").containsExactly(SlaEventType.WARNING_TRIGGERED);
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(agent.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent())
                .singleElement()
                .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.SLA_WARNING));
    }

    @Test
    @DisplayName("no active assignment - WARNING_TRIGGERED is still recorded but nobody is notified")
    void warningWithoutAssignmentRecordsEventButNoNotification() {
        Department department = departmentRepository.save(new Department("Support", null));
        RequestType requestType = aRequestType(department);
        Request request = requestRepository.save(aRequest(requestType));

        slaEscalationService.onStatusRecomputed(request, SlaStatus.ON_TRACK, SlaStatus.AT_RISK);

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getSlaEvents()).extracting("eventType").containsExactly(SlaEventType.WARNING_TRIGGERED);
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("AT_RISK -> OVERDUE appends BREACHED and ESCALATED and notifies every SERVICE_MANAGER whose scope covers the department")
    void breachNotifiesResponsibleManagers() {
        Department direction = departmentRepository.save(new Department("Direction IT", null));
        Department department = departmentRepository.save(new Department("Support", direction));
        RequestType requestType = aRequestType(department);
        Request request = requestRepository.save(aRequest(requestType));

        User departmentManager = userRepository.save(new User("Karim", "Manager", "karim.escalation@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(departmentManager, Role.SERVICE_MANAGER, ScopeType.DEPARTMENT, department.getId()));
        User directionManager = userRepository.save(new User("Dee", "Director", "dee.escalation@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(directionManager, Role.SERVICE_MANAGER, ScopeType.DIRECTION, direction.getId()));
        User unrelatedManager = userRepository.save(new User("Zak", "Outsider", "zak.escalation@example.com", "hash"));
        Department otherDepartment = departmentRepository.save(new Department("Achats", direction));
        userRoleAssignmentRepository.save(new UserRoleAssignment(unrelatedManager, Role.SERVICE_MANAGER, ScopeType.DEPARTMENT, otherDepartment.getId()));

        slaEscalationService.onStatusRecomputed(request, SlaStatus.AT_RISK, SlaStatus.OVERDUE);

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getSlaEvents()).extracting("eventType")
                .containsExactlyInAnyOrder(SlaEventType.BREACHED, SlaEventType.ESCALATED);

        assertThat(notificationsFor(departmentManager)).singleElement()
                .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.SLA_BREACH));
        assertThat(notificationsFor(directionManager)).singleElement()
                .satisfies(n -> assertThat(n.getType()).isEqualTo(NotificationType.SLA_BREACH));
        assertThat(notificationsFor(unrelatedManager)).isEmpty();
    }

    @Test
    @DisplayName("staying at the same status (AT_RISK -> AT_RISK) appends nothing and notifies nobody")
    void noThresholdCrossingIsANoop() {
        Department department = departmentRepository.save(new Department("Support", null));
        RequestType requestType = aRequestType(department);
        Request request = requestRepository.save(aRequest(requestType));

        slaEscalationService.onStatusRecomputed(request, SlaStatus.AT_RISK, SlaStatus.AT_RISK);

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getSlaEvents()).isEmpty();
        assertThat(notificationRepository.findAll()).isEmpty();
    }

    private List<com.smartflow.backend.domain.entity.Notification> notificationsFor(User user) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(user.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
    }

    private RequestType aRequestType(Department department) {
        ServiceCatalog serviceCatalog = serviceCatalogRepository.save(new ServiceCatalog("Support catalog", department));
        return requestTypeRepository.save(new RequestType(serviceCatalog, "Incident"));
    }

    private Request aRequest(RequestType requestType) {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina." + System.nanoTime() + "@example.com", "hash"));
        return new Request("DEM-2026-" + System.nanoTime() % 1_000_000, requestType, requester, "Test request");
    }
}
