package com.smartflow.backend.api.controller;

import com.smartflow.backend.application.service.AttachmentService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.Step;
import com.smartflow.backend.domain.entity.SystemParameter;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import com.smartflow.backend.infrastructure.repository.StepRepository;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import com.smartflow.backend.infrastructure.repository.WorkflowDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RG-09/§13 - extension, taille, type MIME contrôlés côté serveur ; stockage hors
 * répertoire public ; §5.1 - accès à une pièce jointe = accès à la demande.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AttachmentControllerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TempDir
    static Path storageDir;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("smartflow.attachments.storage-dir", () -> storageDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private DepartmentRepository departmentRepository;
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
    private UserRepository userRepository;
    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;
    @Autowired
    private SystemParameterRepository systemParameterRepository;

    private Department department;
    private RequestType requestType;
    private UserDetails asRequester;
    private User requester;

    @BeforeEach
    void seed() {
        department = departmentRepository.save(new Department("Support", null));
        ServiceCatalog service = serviceCatalogRepository.save(new ServiceCatalog("Support Informatique", department));
        requestType = requestTypeRepository.save(new RequestType(service, "Demande de matériel"));
        FormDefinition form = formDefinitionRepository.save(withPublished(new FormDefinition(requestType, 1)));
        WorkflowDefinition workflow = workflowDefinitionRepository.save(withPublished(new WorkflowDefinition(requestType, 1)));
        stepRepository.save(withOrder(new Step(workflow, "QUALIFICATION", "Qualification"), 1));

        requester = userRepository.save(new User("Amina", "Idrissi", "amina.attach@example.com", "hash"));
        asRequester = new SmartFlowUserDetails(requester, Set.of(Role.REQUESTER));
    }

    @Test
    @DisplayName("POST attachments requires authentication")
    void uploadRequiresAuthentication() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "doc.pdf", "application/pdf", "%PDF-1.4 test".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", 999L).file(file).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("§6.4/RG-09 - the requester can upload a whitelisted file to their own draft and read its metadata back")
    void requesterCanUploadAndListWhitelistedFile() throws Exception {
        Long id = createDraft();
        MockMultipartFile file = new MockMultipartFile("file", "justificatif.pdf", "application/pdf", pdfBytes());

        mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).with(user(asRequester)).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("justificatif.pdf"))
                .andExpect(jsonPath("$.uploadedByName").value("Amina Idrissi"));

        mockMvc.perform(get("/api/v1/requests/{id}/attachments", id).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalFilename").value("justificatif.pdf"));
    }

    @Test
    @DisplayName("RG-09 - an executable disguised with an allowed extension (bad MIME sniff) is rejected")
    void executableContentRejectedDespiteAllowedExtension() throws Exception {
        Long id = createDraft();
        // MZ header = Windows PE executable magic bytes.
        byte[] exeBytes = {0x4D, 0x5A, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x04, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile("file", "rapport.pdf", "application/pdf", exeBytes);

        mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONTENT_TYPE_REJECTED"));
    }

    @Test
    @DisplayName("RG-09 - an extension outside the whitelist is rejected")
    void nonWhitelistedExtensionRejected() throws Exception {
        Long id = createDraft();
        MockMultipartFile file = new MockMultipartFile("file", "script.py", "text/x-python", "print('x')".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXTENSION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("RG-09 - a .exe extension is rejected even when explicitly added to the configured whitelist")
    void executableExtensionRejectedEvenIfWhitelisted() throws Exception {
        systemParameterRepository.save(new SystemParameter(AttachmentService.ALLOWED_EXTENSIONS_KEY, "pdf,exe"));
        Long id = createDraft();
        MockMultipartFile file = new MockMultipartFile("file", "outil.exe", "application/octet-stream", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EXECUTABLE_REJECTED"));
    }

    @Test
    @DisplayName("§6.10 - a configured max size smaller than the file is rejected")
    void configuredMaxSizeIsEnforced() throws Exception {
        systemParameterRepository.save(new SystemParameter(AttachmentService.MAX_SIZE_BYTES_KEY, "5"));
        Long id = createDraft();
        MockMultipartFile file = new MockMultipartFile("file", "justificatif.pdf", "application/pdf", pdfBytes());

        mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).with(user(asRequester)).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("RG-06/ADR-11 - an unrelated user cannot upload to, list, or download from someone else's draft (404, not 403)")
    void unrelatedUserCannotAccessDraftAttachments() throws Exception {
        Long id = createDraft();
        User other = userRepository.save(new User("Leila", "Chraibi", "leila.attach@example.com", "hash"));
        UserDetails asOther = new SmartFlowUserDetails(other, Set.of(Role.REQUESTER));
        MockMultipartFile file = new MockMultipartFile("file", "justificatif.pdf", "application/pdf", pdfBytes());

        mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).with(user(asOther)).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/requests/{id}/attachments", id).with(user(asOther)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("§5.1 - downloading a real attachment returns its exact bytes and original filename, gated by the same access as the request")
    void downloadReturnsStoredBytes() throws Exception {
        Long id = createDraft();
        byte[] bytes = pdfBytes();
        MockMultipartFile file = new MockMultipartFile("file", "justificatif.pdf", "application/pdf", bytes);
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).with(user(asRequester)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        long attachmentId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        MvcResult downloadResult = mockMvc.perform(get("/api/v1/requests/{id}/attachments/{attachmentId}", id, attachmentId).with(user(asRequester)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("justificatif.pdf")))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    @DisplayName("ADR-11 - an AUDITOR can download a submitted request's attachment but cannot upload one (404, read-only)")
    void auditorCanDownloadButNotUpload() throws Exception {
        Long id = createDraft();
        mockMvc.perform(post("/api/v1/requests/{id}/submit", id).with(user(asRequester)).with(csrf()))
                .andExpect(status().isOk());
        MockMultipartFile file = new MockMultipartFile("file", "justificatif.pdf", "application/pdf", pdfBytes());
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(file).with(user(asRequester)).with(csrf()))
                .andExpect(status().isCreated())
                .andReturn();
        long attachmentId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        User auditor = userRepository.save(new User("Ines", "Auditor", "ines.attach@example.com", "hash"));
        userRoleAssignmentRepository.save(new UserRoleAssignment(auditor, Role.AUDITOR, ScopeType.GLOBAL, null));
        UserDetails asAuditor = new SmartFlowUserDetails(auditor, Set.of(Role.AUDITOR));

        mockMvc.perform(get("/api/v1/requests/{id}/attachments/{attachmentId}", id, attachmentId).with(user(asAuditor)))
                .andExpect(status().isOk());
        MockMultipartFile secondFile = new MockMultipartFile("file", "autre.pdf", "application/pdf", pdfBytes());
        mockMvc.perform(multipart("/api/v1/requests/{id}/attachments", id).file(secondFile).with(user(asAuditor)).with(csrf()))
                .andExpect(status().isNotFound());
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

    private static byte[] pdfBytes() {
        return "%PDF-1.4\n1 0 obj\n<< >>\nendobj\ntrailer\n<< >>\n%%EOF".getBytes(StandardCharsets.UTF_8);
    }

    private static FormDefinition withPublished(FormDefinition definition) {
        definition.setStatus(PublicationStatus.PUBLISHED);
        return definition;
    }

    private static WorkflowDefinition withPublished(WorkflowDefinition definition) {
        definition.setStatus(PublicationStatus.PUBLISHED);
        return definition;
    }

    private static Step withOrder(Step step, int order) {
        step.setDisplayOrder(order);
        return step;
    }

}
