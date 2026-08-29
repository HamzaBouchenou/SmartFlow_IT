package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.DashboardResponse;
import com.smartflow.backend.application.service.DashboardService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** §6.9/§11.2 - GET /api/v1/dashboards/service : "Obtenir les indicateurs d'un service". */
@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/service")
    public DashboardResponse serviceDashboard(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                               @RequestParam Long serviceId,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return dashboardService.getServiceDashboard(principal.getUser(), serviceId, from, to);
    }

    /** §6.9 - "Export CSV des listes filtrées", mêmes filtres que le tableau de bord ci-dessus. */
    @GetMapping("/service/export")
    public ResponseEntity<byte[]> exportServiceRequests(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                          @RequestParam Long serviceId,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        byte[] csv = dashboardService.exportServiceRequestsCsv(principal.getUser(), serviceId, from, to);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"requests.csv\"")
                .body(csv);
    }
}
