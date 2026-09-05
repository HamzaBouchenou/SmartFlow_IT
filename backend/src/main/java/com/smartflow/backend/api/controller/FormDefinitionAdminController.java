package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.FormDefinitionAdminResponse;
import com.smartflow.backend.api.mapper.FormDefinitionAdminMapper;
import com.smartflow.backend.application.service.FormAdminService;
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

/** §6.3/§10.1/§6.10 - versions d'un formulaire (ADR-17, docs/DECISIONS.md). Un DRAFT à la
 * fois par type de demande ; publier archive l'ancien PUBLISHED (RG-12). Sous /admin
 * plutôt qu'un seul préfixe de ressource : les routes portent deux racines distinctes
 * (par type de demande pour lister/créer, par id de version ensuite). */
@RestController
@RequestMapping("/api/v1/admin")
public class FormDefinitionAdminController {

    private final FormAdminService formAdminService;

    public FormDefinitionAdminController(FormAdminService formAdminService) {
        this.formAdminService = formAdminService;
    }

    @GetMapping("/request-types/{requestTypeId}/form-definitions")
    public List<FormDefinitionAdminResponse> listVersions(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                            @PathVariable Long requestTypeId) {
        return formAdminService.listVersions(principal.getUser(), requestTypeId).stream()
                .map(FormDefinitionAdminMapper::toSummary)
                .toList();
    }

    @PostMapping("/request-types/{requestTypeId}/form-definitions")
    @ResponseStatus(HttpStatus.CREATED)
    public FormDefinitionAdminResponse createDraft(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                     @PathVariable Long requestTypeId) {
        var draft = formAdminService.createDraft(principal.getUser(), requestTypeId);
        return FormDefinitionAdminMapper.toResponse(formAdminService.getVersion(principal.getUser(), draft.getId()));
    }

    @GetMapping("/form-definitions/{id}")
    public FormDefinitionAdminResponse getVersion(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return FormDefinitionAdminMapper.toResponse(formAdminService.getVersion(principal.getUser(), id));
    }

    @DeleteMapping("/form-definitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDraft(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        formAdminService.deleteDraft(principal.getUser(), id);
    }

    @PostMapping("/form-definitions/{id}/publish")
    public FormDefinitionAdminResponse publish(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        formAdminService.publish(principal.getUser(), id);
        return FormDefinitionAdminMapper.toResponse(formAdminService.getVersion(principal.getUser(), id));
    }
}
