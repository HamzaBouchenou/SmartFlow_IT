package com.smartflow.backend.infrastructure.scheduler;

import com.smartflow.backend.domain.entity.AuditLog;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.SystemParameter;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.RequestStatus;
import com.smartflow.backend.infrastructure.repository.AuditLogRepository;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RG-12/ADR-15 (docs/DECISIONS.md) - le balayage d'archivage planifié.
 * requests.archive-after-months=0 rend le test indépendant de l'horloge système (le seuil
 * devient "maintenant", donc tout closedAt/updatedAt déjà écrit est nécessairement dans le
 * passé) ; JdbcTemplate contourne le @PreUpdate de Request pour figer un updated_at ancien
 * (le cas CANCELLED, qui n'a pas de closedAt), ce que Hibernate écraserait sinon à chaque save.
 */
@Testcontainers
@SpringBootTest
@Transactional
class RequestArchivalSchedulerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private RequestArchivalScheduler scheduler;
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
    private SystemParameterRepository systemParameterRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("a CLOSED request older than the retention period is archived, and the archival is audited with a null (system) actor")
    void closedRequestPastRetentionIsArchived() {
        systemParameterRepository.save(new SystemParameter(RequestArchivalScheduler.ARCHIVE_AFTER_MONTHS_KEY, "0"));
        RequestType requestType = aRequestType();
        Request request = requestRepository.save(aRequest(requestType));
        request.setStatus(RequestStatus.CLOSED);
        request.setClosedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        requestRepository.save(request);

        scheduler.sweep();

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RequestStatus.ARCHIVED);

        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).anySatisfy(entry -> {
            assertThat(entry.getAction()).isEqualTo("ARCHIVE");
            assertThat(entry.getObjectId()).isEqualTo(request.getId().toString());
            assertThat(entry.getActor()).isNull();
            assertThat(entry.getSummary()).contains("CLOSED").contains("ARCHIVED");
        });
    }

    @Test
    @DisplayName("a CLOSED request still within the default 24-month retention is left untouched")
    void closedRequestWithinDefaultRetentionIsNotArchived() {
        RequestType requestType = aRequestType();
        Request request = requestRepository.save(aRequest(requestType));
        request.setStatus(RequestStatus.CLOSED);
        request.setClosedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        requestRepository.save(request);

        scheduler.sweep();

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RequestStatus.CLOSED);
    }

    @Test
    @DisplayName("a CANCELLED request (no closedAt) uses updatedAt and is archived once past retention")
    void cancelledRequestPastRetentionUsesUpdatedAt() {
        systemParameterRepository.save(new SystemParameter(RequestArchivalScheduler.ARCHIVE_AFTER_MONTHS_KEY, "0"));
        RequestType requestType = aRequestType();
        Request request = requestRepository.save(aRequest(requestType));
        request.setStatus(RequestStatus.CANCELLED);
        // saveAndFlush, not save: the JDBC update and entityManager.clear() below must see
        // (and not lose) this status change once it lands in the database - a plain save()
        // only queues it, and clear() would silently drop the still-pending change.
        requestRepository.saveAndFlush(request);
        // Backdate updated_at directly in SQL: Request.onUpdate() would otherwise reset it
        // to "now" on every JPA save, making an "old enough to archive" CANCELLED row
        // impossible to construct through the entity API alone.
        jdbcTemplate.update("update requests set updated_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), request.getId());
        // The JDBC update above bypasses Hibernate entirely: without clearing the
        // persistence context, findByStatusIn below would return the identity-mapped
        // instance already in this session, still carrying the pre-backdate updatedAt.
        entityManager.clear();

        scheduler.sweep();

        Request reloaded = requestRepository.findById(request.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RequestStatus.ARCHIVED);
    }

    @Test
    @DisplayName("a DRAFT or SUBMITTED request is never archived, however low the retention threshold")
    void draftAndSubmittedRequestsAreNeverArchived() {
        systemParameterRepository.save(new SystemParameter(RequestArchivalScheduler.ARCHIVE_AFTER_MONTHS_KEY, "0"));
        RequestType requestType = aRequestType();
        Request draft = requestRepository.save(aRequest(requestType));

        scheduler.sweep();

        Request reloaded = requestRepository.findById(draft.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(RequestStatus.DRAFT);
    }

    private RequestType aRequestType() {
        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog serviceCatalog = serviceCatalogRepository.save(new ServiceCatalog("Support catalog", department));
        return requestTypeRepository.save(new RequestType(serviceCatalog, "Incident"));
    }

    private Request aRequest(RequestType requestType) {
        User requester = userRepository.save(new User("Amina", "Idrissi", "amina." + System.nanoTime() + "@example.com", "hash"));
        return new Request("DEM-2026-" + (System.nanoTime() % 1_000_000), requestType, requester, "Test request");
    }
}
