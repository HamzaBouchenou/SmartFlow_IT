package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.UpsertServiceCatalogRequest;
import com.smartflow.backend.api.dto.response.ServiceCatalogAdminResponse;
import com.smartflow.backend.api.mapper.ServiceCatalogAdminMapper;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** §6.2/§6.10 - fiches de catalogue, vue administration (inclut les désactivées). RG-02/
 * RG-12 - jamais de suppression physique, seulement activate/deactivate. */
@RestController
@RequestMapping("/api/v1/admin/services")
public class ServiceCatalogAdminController {

    private final CatalogAdminService catalogAdminService;

    public ServiceCatalogAdminController(CatalogAdminService catalogAdminService) {
        this.catalogAdminService = catalogAdminService;
    }

    @GetMapping
    public List<ServiceCatalogAdminResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        return catalogAdminService.listServices(principal.getUser()).stream()
                .map(ServiceCatalogAdminMapper::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceCatalogAdminResponse create(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                               @Valid @RequestBody UpsertServiceCatalogRequest body) {
        var created = catalogAdminService.createService(principal.getUser(), body.name(), body.description(),
                body.category(), body.departmentId(), body.displayOrder());
        return ServiceCatalogAdminMapper.toResponse(created);
    }

    @PutMapping("/{id}")
    public ServiceCatalogAdminResponse update(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                               @Valid @RequestBody UpsertServiceCatalogRequest body) {
        var updated = catalogAdminService.updateService(principal.getUser(), id, body.name(), body.description(),
                body.category(), body.departmentId(), body.displayOrder());
        return ServiceCatalogAdminMapper.toResponse(updated);
    }

    @PostMapping("/{id}/activate")
    public ServiceCatalogAdminResponse activate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return ServiceCatalogAdminMapper.toResponse(catalogAdminService.setServiceActive(principal.getUser(), id, true));
    }

    @PostMapping("/{id}/deactivate")
    public ServiceCatalogAdminResponse deactivate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return ServiceCatalogAdminMapper.toResponse(catalogAdminService.setServiceActive(principal.getUser(), id, false));
    }
}
