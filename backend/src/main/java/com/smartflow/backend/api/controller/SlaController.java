package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.UpsertSlaRequest;
import com.smartflow.backend.api.dto.response.SlaResponse;
import com.smartflow.backend.api.mapper.SlaMapper;
import com.smartflow.backend.application.service.SlaAdminService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.enums.Priority;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** §6.7/§6.10 - cibles SLA d'un type de demande, une par Priority. */
@RestController
@RequestMapping("/api/v1/admin/request-types/{requestTypeId}/sla")
public class SlaController {

    private final SlaAdminService slaAdminService;

    public SlaController(SlaAdminService slaAdminService) {
        this.slaAdminService = slaAdminService;
    }

    @GetMapping
    public List<SlaResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long requestTypeId) {
        return slaAdminService.listForRequestType(principal.getUser(), requestTypeId).stream()
                .map(SlaMapper::toResponse)
                .toList();
    }

    @PutMapping("/{priority}")
    public SlaResponse upsert(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long requestTypeId,
                               @PathVariable Priority priority, @Valid @RequestBody UpsertSlaRequest body) {
        return SlaMapper.toResponse(slaAdminService.upsert(principal.getUser(), requestTypeId, priority,
                body.firstResponseMinutes(), body.resolutionMinutes()));
    }
}
