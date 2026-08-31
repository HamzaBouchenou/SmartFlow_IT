package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.AuditLogResponse;
import com.smartflow.backend.api.dto.response.PageResponse;
import com.smartflow.backend.api.mapper.AuditLogMapper;
import com.smartflow.backend.application.service.AuditLogFilter;
import com.smartflow.backend.application.service.AuditLogQueryService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * §6.10/§13.1 - "Journal d'audit consultable avec filtres par utilisateur, action, objet et
 * période." Lecture seule (RG-11's own writer, crosscutting/audit/AuditService, n'est
 * jamais exposé par un contrôleur - §13.1 : "protégées contre la modification depuis
 * l'interface").
 */
@RestController
@RequestMapping("/api/v1/admin/audit-log")
public class AuditLogController {

    private final AuditLogQueryService auditLogQueryService;

    public AuditLogController(AuditLogQueryService auditLogQueryService) {
        this.auditLogQueryService = auditLogQueryService;
    }

    @GetMapping
    public PageResponse<AuditLogResponse> search(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                   @ModelAttribute AuditLogFilterParams filterParams,
                                                   @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = auditLogQueryService.search(principal.getUser(), filterParams.toFilter(), pageable);
        return PageResponse.from(page.map(AuditLogMapper::toResponse));
    }

    /** Même raisonnement que TaskQueueController.TaskQueueFilterParams : un objet plutôt
     * que quatre @RequestParam répétés. */
    public record AuditLogFilterParams(Long actorId, String action, String objectType,
                                        Instant occurredFrom, Instant occurredTo) {

        AuditLogFilter toFilter() {
            return new AuditLogFilter(actorId, action, objectType, occurredFrom, occurredTo);
        }
    }
}
