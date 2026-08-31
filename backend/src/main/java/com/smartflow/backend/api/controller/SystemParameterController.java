package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.UpdateSystemParameterRequest;
import com.smartflow.backend.api.dto.response.SystemParameterResponse;
import com.smartflow.backend.api.mapper.SystemParameterMapper;
import com.smartflow.backend.application.service.SystemParameterAdminService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** §6.10 - "Paramètres généraux" administrables (§5 - "Administrateur fonctionnel...
 * paramétrage fonctionnel global"). */
@RestController
@RequestMapping("/api/v1/admin/system-parameters")
public class SystemParameterController {

    private final SystemParameterAdminService systemParameterAdminService;

    public SystemParameterController(SystemParameterAdminService systemParameterAdminService) {
        this.systemParameterAdminService = systemParameterAdminService;
    }

    @GetMapping
    public List<SystemParameterResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        return systemParameterAdminService.listAll(principal.getUser()).stream()
                .map(SystemParameterMapper::toResponse)
                .toList();
    }

    @PutMapping("/{key}")
    public SystemParameterResponse update(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable String key,
                                           @Valid @RequestBody UpdateSystemParameterRequest body) {
        return SystemParameterMapper.toResponse(systemParameterAdminService.update(principal.getUser(), key, body.value()));
    }
}
