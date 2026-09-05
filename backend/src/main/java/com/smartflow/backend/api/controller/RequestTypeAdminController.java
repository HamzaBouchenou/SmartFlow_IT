package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.UpsertRequestTypeRequest;
import com.smartflow.backend.api.dto.response.RequestTypeAdminResponse;
import com.smartflow.backend.api.mapper.RequestTypeAdminMapper;
import com.smartflow.backend.application.service.CatalogAdminService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** §6.2/§6.10 - types de demande, vue administration (inclut les désactivés). RG-02/RG-12 -
 * jamais de suppression physique, seulement activate/deactivate. */
@RestController
@RequestMapping("/api/v1/admin/request-types")
public class RequestTypeAdminController {

    private final CatalogAdminService catalogAdminService;

    public RequestTypeAdminController(CatalogAdminService catalogAdminService) {
        this.catalogAdminService = catalogAdminService;
    }

    @GetMapping
    public List<RequestTypeAdminResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                @RequestParam Long serviceCatalogId) {
        return catalogAdminService.listRequestTypes(principal.getUser(), serviceCatalogId).stream()
                .map(RequestTypeAdminMapper::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestTypeAdminResponse create(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                            @Valid @RequestBody UpsertRequestTypeRequest body) {
        var created = catalogAdminService.createRequestType(principal.getUser(), body.serviceCatalogId(), body.name(),
                body.description(), body.targetDelayDescription(), body.requiredDocuments(), body.contactInfo(),
                body.isReopenAllowed(), body.displayOrder());
        return RequestTypeAdminMapper.toResponse(created);
    }

    @PutMapping("/{id}")
    public RequestTypeAdminResponse update(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                            @Valid @RequestBody UpsertRequestTypeRequest body) {
        var updated = catalogAdminService.updateRequestType(principal.getUser(), id, body.name(), body.description(),
                body.targetDelayDescription(), body.requiredDocuments(), body.contactInfo(), body.isReopenAllowed(),
                body.displayOrder());
        return RequestTypeAdminMapper.toResponse(updated);
    }

    @PostMapping("/{id}/activate")
    public RequestTypeAdminResponse activate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return RequestTypeAdminMapper.toResponse(catalogAdminService.setRequestTypeActive(principal.getUser(), id, true));
    }

    @PostMapping("/{id}/deactivate")
    public RequestTypeAdminResponse deactivate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return RequestTypeAdminMapper.toResponse(catalogAdminService.setRequestTypeActive(principal.getUser(), id, false));
    }
}
