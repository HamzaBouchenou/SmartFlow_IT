package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.FormDefinitionResponse;
import com.smartflow.backend.api.dto.response.RequestTypeResponse;
import com.smartflow.backend.api.mapper.FormDefinitionMapper;
import com.smartflow.backend.api.mapper.RequestTypeMapper;
import com.smartflow.backend.application.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** §6.2 (informations avant saisie) et §6.3 (formulaire configurable) d'un type de demande donné. */
@RestController
@RequestMapping("/api/v1/request-types")
public class RequestTypeController {

    private final CatalogService catalogService;

    public RequestTypeController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** §6.2 - "Affichage des informations utiles avant la saisie". */
    @GetMapping("/{id}")
    public RequestTypeResponse get(@PathVariable Long id) {
        return RequestTypeMapper.toResponse(catalogService.getActiveRequestType(id));
    }

    /** §6.3 - la définition de formulaire PUBLISHED active de ce type de demande. */
    @GetMapping("/{id}/form")
    public FormDefinitionResponse getForm(@PathVariable Long id) {
        return FormDefinitionMapper.toResponse(catalogService.getPublishedForm(id));
    }
}
