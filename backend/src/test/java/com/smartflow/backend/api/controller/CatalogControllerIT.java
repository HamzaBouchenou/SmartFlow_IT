package com.smartflow.backend.api.controller;

import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.FieldOption;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.FormField;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.enums.FieldType;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.FieldOptionRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.FormFieldRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §6.2 (catalogue) et §6.3 (formulaire) : lecture seule, donc pas de cérémonie CSRF/session
 * comme AuthenticationIT - .with(user(...)) suffit pour passer l'authentification exigée par
 * SecurityConfig sans rejouer le flux de connexion, exactement ce que ces routes doivent
 * vérifier ici (leur propre comportement, pas celui d'ADR-01 déjà couvert ailleurs).
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogControllerIT {

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
    private DepartmentRepository departmentRepository;
    @Autowired
    private ServiceCatalogRepository serviceCatalogRepository;
    @Autowired
    private RequestTypeRepository requestTypeRepository;
    @Autowired
    private FormDefinitionRepository formDefinitionRepository;
    @Autowired
    private FormFieldRepository formFieldRepository;
    @Autowired
    private FieldOptionRepository fieldOptionRepository;

    @Test
    @DisplayName("GET /api/v1/services requires authentication")
    void listServicesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§6.2 - lists only active services, ordered by displayOrder, never an inactive one")
    void listsOnlyActiveServicesInDisplayOrder() throws Exception {
        // V5's own seed data already populates service_catalog (this test runs against the
        // real migrations, seed included) : scoping every assertion to a category unique to
        // this test is what keeps the count exact regardless of what else the schema holds.
        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog second = aService(department, "Achats", "QA-active-order", 2);
        ServiceCatalog first = aService(department, "Support Informatique", "QA-active-order", 1);
        ServiceCatalog inactive = aService(department, "Ancien service", "QA-active-order", 0);
        inactive.setActive(false);
        serviceCatalogRepository.save(inactive);

        mockMvc.perform(get("/api/v1/services").param("category", "QA-active-order").with(user("someone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(first.getId()))
                .andExpect(jsonPath("$[1].id").value(second.getId()));
    }

    @Test
    @DisplayName("§6.2 - keyword and category filters narrow the search and are combinable")
    void filtersByKeywordAndCategory() throws Exception {
        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog it = aService(department, "QA Support Informatique Unique", "QA-Cat-IT", 1);
        aService(department, "QA Achats et Approvisionnement Unique", "QA-Cat-Achats", 2);

        mockMvc.perform(get("/api/v1/services").param("q", "informatique unique").with(user("someone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(it.getId()));

        mockMvc.perform(get("/api/v1/services").param("category", "QA-Cat-Achats").with(user("someone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("QA Achats et Approvisionnement Unique"));

        mockMvc.perform(get("/api/v1/services").param("q", "informatique unique").param("category", "QA-Cat-Achats").with(user("someone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("§6.2 - request types under a service: only active ones, 404 for an unknown or inactive service")
    void listsRequestTypesUnderAService() throws Exception {
        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = aService(department, "Support Informatique", "IT", 1);
        RequestType active = aRequestType(service, "Demande de matériel", 1);
        RequestType inactive = aRequestType(service, "Ancien type", 0);
        inactive.setActive(false);
        requestTypeRepository.save(inactive);

        mockMvc.perform(get("/api/v1/services/{id}/request-types", service.getId()).with(user("someone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(active.getId()));

        mockMvc.perform(get("/api/v1/services/{id}/request-types", 999_999L).with(user("someone")))
                .andExpect(status().isNotFound());

        ServiceCatalog inactiveService = aService(department, "Service désactivé", "IT", 2);
        inactiveService.setActive(false);
        serviceCatalogRepository.save(inactiveService);
        mockMvc.perform(get("/api/v1/services/{id}/request-types", inactiveService.getId()).with(user("someone")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.2 - request type detail carries the pre-submission information, 404 when its service is inactive")
    void requestTypeDetailReflectsParentServiceActivation() throws Exception {
        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = aService(department, "Support Informatique", "IT", 1);
        RequestType requestType = aRequestType(service, "Demande de matériel", 1);
        requestType.setTargetDelayDescription("48h");
        requestType.setContactInfo("support-it@smartflow.local");
        requestTypeRepository.save(requestType);

        mockMvc.perform(get("/api/v1/request-types/{id}", requestType.getId()).with(user("someone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Demande de matériel"))
                .andExpect(jsonPath("$.targetDelayDescription").value("48h"))
                .andExpect(jsonPath("$.contactInfo").value("support-it@smartflow.local"));

        mockMvc.perform(get("/api/v1/request-types/{id}", 999_999L).with(user("someone")))
                .andExpect(status().isNotFound());

        service.setActive(false);
        serviceCatalogRepository.save(service);
        mockMvc.perform(get("/api/v1/request-types/{id}", requestType.getId()).with(user("someone")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§6.3 - published form: fields ordered, options grouped per field, conditional display carried through")
    void returnsPublishedFormWithOrderedFieldsAndOptions() throws Exception {
        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = aService(department, "Support Informatique", "IT", 1);
        RequestType requestType = aRequestType(service, "Demande de matériel", 1);

        FormDefinition definition = new FormDefinition(requestType, 1);
        definition.setStatus(PublicationStatus.PUBLISHED);
        formDefinitionRepository.save(definition);

        FormField urgency = new FormField(definition, "urgency", "Urgence", FieldType.LIST);
        urgency.setDisplayOrder(1);
        formFieldRepository.save(urgency);
        FormField justification = new FormField(definition, "urgent_justification", "Justification", FieldType.TEXT);
        justification.setDisplayOrder(2);
        justification.setVisibleWhenFieldCode("urgency");
        justification.setVisibleWhenValue("Urgent");
        formFieldRepository.save(justification);

        fieldOptionRepository.save(new FieldOption(urgency, "Urgent", "Urgent"));
        fieldOptionRepository.save(new FieldOption(urgency, "Normal", "Normal"));

        mockMvc.perform(get("/api/v1/request-types/{id}/form", requestType.getId()).with(user("someone")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestTypeId").value(requestType.getId()))
                .andExpect(jsonPath("$.fields.length()").value(2))
                .andExpect(jsonPath("$.fields[0].code").value("urgency"))
                .andExpect(jsonPath("$.fields[0].options.length()").value(2))
                .andExpect(jsonPath("$.fields[1].code").value("urgent_justification"))
                .andExpect(jsonPath("$.fields[1].options.length()").value(0))
                .andExpect(jsonPath("$.fields[1].visibleWhenFieldCode").value("urgency"))
                .andExpect(jsonPath("$.fields[1].visibleWhenValue").value("Urgent"));
    }

    @Test
    @DisplayName("§6.3 - 404 when only a DRAFT form definition exists, never an unpublished one")
    void returns404WhenNoFormIsPublished() throws Exception {
        Department department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = aService(department, "Support Informatique", "IT", 1);
        RequestType requestType = aRequestType(service, "Demande de matériel", 1);
        formDefinitionRepository.save(new FormDefinition(requestType, 1));

        mockMvc.perform(get("/api/v1/request-types/{id}/form", requestType.getId()).with(user("someone")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a non-numeric id (e.g. /request-types/abc) is a 400, not the 500 fallback")
    void nonNumericIdIsABadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/request-types/{id}", "abc").with(user("someone")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ServiceCatalog aService(Department department, String name, String category, int displayOrder) {
        ServiceCatalog service = new ServiceCatalog(name, department);
        service.setCategory(category);
        service.setDisplayOrder(displayOrder);
        return serviceCatalogRepository.save(service);
    }

    private RequestType aRequestType(ServiceCatalog service, String name, int displayOrder) {
        RequestType requestType = new RequestType(service, name);
        requestType.setDisplayOrder(displayOrder);
        return requestTypeRepository.save(requestType);
    }
}
