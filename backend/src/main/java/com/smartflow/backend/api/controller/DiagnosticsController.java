package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.DiagnosticsResponse;
import com.smartflow.backend.application.service.DiagnosticsService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** §6.10/§15.3 - "Page de diagnostic affichant l'état des services techniques sans
 * exposer de secrets", réservée à TECHNICAL_ADMIN. Distincte de /actuator/health
 * (public, §15.1 - "endpoint de santé pour le back-end" nu) : cet écran ajoute la base,
 * le service IA et l'e-mail, avec un contrôle d'accès. */
@RestController
@RequestMapping("/api/v1/admin/diagnostics")
public class DiagnosticsController {

    private final DiagnosticsService diagnosticsService;

    public DiagnosticsController(DiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping
    public DiagnosticsResponse check(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        var view = diagnosticsService.check(principal.getUser());
        return new DiagnosticsResponse(view.backendStatus(), view.databaseStatus(), view.aiServiceStatus(),
                view.mailStatus(), view.checkedAt());
    }
}
