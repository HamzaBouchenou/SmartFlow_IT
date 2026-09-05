package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.UpdateEmailTemplateRequest;
import com.smartflow.backend.api.dto.response.EmailTemplateResponse;
import com.smartflow.backend.api.mapper.EmailTemplateMapper;
import com.smartflow.backend.application.service.EmailTemplateAdminService;
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

/** §6.8/§6.10 - modèles d'e-mail, un par NotificationType (catalogue fermé, jamais une clé arbitraire). */
@RestController
@RequestMapping("/api/v1/admin/email-templates")
public class EmailTemplateController {

    private final EmailTemplateAdminService emailTemplateAdminService;

    public EmailTemplateController(EmailTemplateAdminService emailTemplateAdminService) {
        this.emailTemplateAdminService = emailTemplateAdminService;
    }

    @GetMapping
    public List<EmailTemplateResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        return emailTemplateAdminService.listAll(principal.getUser()).stream()
                .map(EmailTemplateMapper::toResponse)
                .toList();
    }

    @PutMapping("/{code}")
    public EmailTemplateResponse update(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable String code,
                                         @Valid @RequestBody UpdateEmailTemplateRequest body) {
        return EmailTemplateMapper.toResponse(
                emailTemplateAdminService.update(principal.getUser(), code, body.subject(), body.bodyHtml()));
    }
}
