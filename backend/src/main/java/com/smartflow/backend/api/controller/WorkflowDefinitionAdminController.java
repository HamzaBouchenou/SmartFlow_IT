package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.WorkflowDefinitionAdminResponse;
import com.smartflow.backend.api.mapper.WorkflowDefinitionAdminMapper;
import com.smartflow.backend.application.service.WorkflowAdminService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** §6.5/§10.1/§6.10 - versions d'un workflow (ADR-17, docs/DECISIONS.md ; RG-03). Un DRAFT
 * à la fois par type de demande ; publier archive l'ancien PUBLISHED (RG-12) sans jamais
 * toucher une demande déjà en cours sur lui (RG-03). */
@RestController
@RequestMapping("/api/v1/admin")
public class WorkflowDefinitionAdminController {

    private final WorkflowAdminService workflowAdminService;

    public WorkflowDefinitionAdminController(WorkflowAdminService workflowAdminService) {
        this.workflowAdminService = workflowAdminService;
    }

    @GetMapping("/request-types/{requestTypeId}/workflow-definitions")
    public List<WorkflowDefinitionAdminResponse> listVersions(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                                @PathVariable Long requestTypeId) {
        return workflowAdminService.listVersions(principal.getUser(), requestTypeId).stream()
                .map(WorkflowDefinitionAdminMapper::toSummary)
                .toList();
    }

    @PostMapping("/request-types/{requestTypeId}/workflow-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDefinitionAdminResponse createDraft(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                         @PathVariable Long requestTypeId) {
        var draft = workflowAdminService.createDraft(principal.getUser(), requestTypeId);
        return WorkflowDefinitionAdminMapper.toResponse(workflowAdminService.getVersion(principal.getUser(), draft.getId()));
    }

    @GetMapping("/workflow-definitions/{id}")
    public WorkflowDefinitionAdminResponse getVersion(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return WorkflowDefinitionAdminMapper.toResponse(workflowAdminService.getVersion(principal.getUser(), id));
    }

    @DeleteMapping("/workflow-definitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        workflowAdminService.deleteDraft(principal.getUser(), id);
    }

    @PostMapping("/workflow-definitions/{id}/publish")
    public WorkflowDefinitionAdminResponse publish(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        workflowAdminService.publish(principal.getUser(), id);
        return WorkflowDefinitionAdminMapper.toResponse(workflowAdminService.getVersion(principal.getUser(), id));
    }
}
