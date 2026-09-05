package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.CreateDepartmentRequest;
import com.smartflow.backend.api.dto.request.UpdateDepartmentRequest;
import com.smartflow.backend.api.dto.response.DepartmentResponse;
import com.smartflow.backend.api.mapper.DepartmentMapper;
import com.smartflow.backend.application.service.OrganizationAdminService;
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

/** §4.1/§6.10 - directions et services. RG-02/RG-12 - jamais de suppression physique,
 * seulement activate/deactivate. */
@RestController
@RequestMapping("/api/v1/admin/departments")
public class DepartmentController {

    private final OrganizationAdminService organizationAdminService;

    public DepartmentController(OrganizationAdminService organizationAdminService) {
        this.organizationAdminService = organizationAdminService;
    }

    @GetMapping
    public List<DepartmentResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        return organizationAdminService.listDepartments(principal.getUser()).stream()
                .map(DepartmentMapper::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepartmentResponse create(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                      @Valid @RequestBody CreateDepartmentRequest body) {
        return DepartmentMapper.toResponse(
                organizationAdminService.createDepartment(principal.getUser(), body.name(), body.parentId(), body.leadId()));
    }

    @PutMapping("/{id}")
    public DepartmentResponse update(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                      @Valid @RequestBody UpdateDepartmentRequest body) {
        return DepartmentMapper.toResponse(
                organizationAdminService.updateDepartment(principal.getUser(), id, body.name(), body.parentId(), body.leadId()));
    }

    @PostMapping("/{id}/activate")
    public DepartmentResponse activate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return DepartmentMapper.toResponse(organizationAdminService.setDepartmentActive(principal.getUser(), id, true));
    }

    @PostMapping("/{id}/deactivate")
    public DepartmentResponse deactivate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return DepartmentMapper.toResponse(organizationAdminService.setDepartmentActive(principal.getUser(), id, false));
    }
}
