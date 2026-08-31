package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.UpsertFieldOptionRequest;
import com.smartflow.backend.api.dto.request.UpsertFormFieldRequest;
import com.smartflow.backend.api.dto.response.FieldOptionAdminResponse;
import com.smartflow.backend.api.dto.response.FormFieldAdminResponse;
import com.smartflow.backend.api.mapper.FormDefinitionAdminMapper;
import com.smartflow.backend.application.service.FormAdminService;
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

import java.util.List;

/** §6.3/§6.10 - champs et valeurs possibles d'une FormDefinition DRAFT (ADR-17 - refusé
 * dès que la version est PUBLISHED/ARCHIVED). */
@RestController
@RequestMapping("/api/v1/admin")
public class FormFieldAdminController {

    private final FormAdminService formAdminService;

    public FormFieldAdminController(FormAdminService formAdminService) {
        this.formAdminService = formAdminService;
    }

    @PostMapping("/form-definitions/{formDefinitionId}/fields")
    @ResponseStatus(HttpStatus.CREATED)
    public FormFieldAdminResponse addField(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                            @PathVariable Long formDefinitionId, @Valid @RequestBody UpsertFormFieldRequest body) {
        var field = formAdminService.addField(principal.getUser(), formDefinitionId, body.code(), body.label(), body.fieldType(),
                body.required(), body.displayOrder(), body.helpText(), body.visibleWhenFieldCode(), body.visibleWhenValue());
        return FormDefinitionAdminMapper.toFieldResponse(field, List.of());
    }

    @PutMapping("/form-fields/{id}")
    public FormFieldAdminResponse updateField(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                               @Valid @RequestBody UpsertFormFieldRequest body) {
        var field = formAdminService.updateField(principal.getUser(), id, body.code(), body.label(), body.fieldType(),
                body.required(), body.displayOrder(), body.helpText(), body.visibleWhenFieldCode(), body.visibleWhenValue());
        return FormDefinitionAdminMapper.toFieldResponse(field, formAdminService.getOptions(field.getId()));
    }

    @DeleteMapping("/form-fields/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteField(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        formAdminService.deleteField(principal.getUser(), id);
    }

    @PostMapping("/form-fields/{formFieldId}/options")
    @ResponseStatus(HttpStatus.CREATED)
    public FieldOptionAdminResponse addOption(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                               @PathVariable Long formFieldId, @Valid @RequestBody UpsertFieldOptionRequest body) {
        var option = formAdminService.addOption(principal.getUser(), formFieldId, body.value(), body.label(), body.displayOrder());
        return FormDefinitionAdminMapper.toOptionResponse(option);
    }

    @PutMapping("/field-options/{id}")
    public FieldOptionAdminResponse updateOption(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                                  @Valid @RequestBody UpsertFieldOptionRequest body) {
        var option = formAdminService.updateOption(principal.getUser(), id, body.value(), body.label(), body.displayOrder());
        return FormDefinitionAdminMapper.toOptionResponse(option);
    }

    @DeleteMapping("/field-options/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteOption(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        formAdminService.deleteOption(principal.getUser(), id);
    }
}
