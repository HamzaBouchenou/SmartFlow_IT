package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.UpsertStepRequest;
import com.smartflow.backend.api.dto.request.UpsertTransitionRequest;
import com.smartflow.backend.api.dto.response.StepAdminResponse;
import com.smartflow.backend.api.dto.response.TransitionAdminResponse;
import com.smartflow.backend.api.mapper.WorkflowDefinitionAdminMapper;
import com.smartflow.backend.application.service.WorkflowAdminService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** §6.5/§6.10 - étapes et transitions d'un WorkflowDefinition DRAFT (ADR-17 - refusé dès
 * que la version est PUBLISHED/ARCHIVED). Éditeur structuré (tableaux), jamais un canevas
 * graphique (§4.2 exclut un "moteur BPMN complet"). */
@RestController
@RequestMapping("/api/v1/admin")
public class StepTransitionAdminController {

    private final WorkflowAdminService workflowAdminService;

    public StepTransitionAdminController(WorkflowAdminService workflowAdminService) {
        this.workflowAdminService = workflowAdminService;
    }

    @PostMapping("/workflow-definitions/{workflowDefinitionId}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepAdminResponse addStep(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                      @PathVariable Long workflowDefinitionId, @Valid @RequestBody UpsertStepRequest body) {
        var step = workflowAdminService.addStep(principal.getUser(), workflowDefinitionId, body.code(), body.name(),
                body.displayOrder(), body.responsibleRole(), body.responsibleTeamId(), body.isSuspendSla());
        return WorkflowDefinitionAdminMapper.toStepResponse(step);
    }

    @PutMapping("/steps/{id}")
    public StepAdminResponse updateStep(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                         @Valid @RequestBody UpsertStepRequest body) {
        var step = workflowAdminService.updateStep(principal.getUser(), id, body.code(), body.name(), body.displayOrder(),
                body.responsibleRole(), body.responsibleTeamId(), body.isSuspendSla());
        return WorkflowDefinitionAdminMapper.toStepResponse(step);
    }

    @DeleteMapping("/steps/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStep(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        workflowAdminService.deleteStep(principal.getUser(), id);
    }

    @PostMapping("/steps/{fromStepId}/transitions")
    @ResponseStatus(HttpStatus.CREATED)
    public TransitionAdminResponse addTransition(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                  @PathVariable Long fromStepId, @Valid @RequestBody UpsertTransitionRequest body) {
        var transition = workflowAdminService.addTransition(principal.getUser(), fromStepId, body.action(), body.toStepId(),
                body.conditionPriority(), body.conditionDepartmentId(), body.conditionFieldCode(), body.conditionFieldValue());
        return WorkflowDefinitionAdminMapper.toTransitionResponse(transition);
    }

    @PutMapping("/transitions/{id}")
    public TransitionAdminResponse updateTransition(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                                      @Valid @RequestBody UpsertTransitionRequest body) {
        var transition = workflowAdminService.updateTransition(principal.getUser(), id, body.action(), body.toStepId(),
                body.conditionPriority(), body.conditionDepartmentId(), body.conditionFieldCode(), body.conditionFieldValue());
        return WorkflowDefinitionAdminMapper.toTransitionResponse(transition);
    }

    @DeleteMapping("/transitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTransition(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        workflowAdminService.deleteTransition(principal.getUser(), id);
    }
}
