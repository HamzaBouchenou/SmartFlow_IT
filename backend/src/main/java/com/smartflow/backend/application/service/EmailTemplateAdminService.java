package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.EmailTemplate;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.domain.exception.AdministrationValidationException;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.EmailTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * §6.10 - "Gestion... des modèles d'e-mail" (§6.8). Catalogue fermé aux
 * NotificationType (EmailTemplateResponse's own javadoc) : un administrateur ajuste le
 * sujet/corps d'un type de notification existant, il n'en crée jamais un nouveau depuis cet
 * écran (CLAUDE.md, règle numéro un). MailService reste le seul lecteur en dehors de ce
 * service ; celui-ci n'écrit jamais l'envoi lui-même.
 */
@Service
public class EmailTemplateAdminService {

    private final EmailTemplateRepository emailTemplateRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public EmailTemplateAdminService(EmailTemplateRepository emailTemplateRepository,
                                      AuthorizationService authorizationService, AuditService auditService) {
        this.emailTemplateRepository = emailTemplateRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TemplateView> listAll(User actingUser) {
        requireFunctionalAdmin(actingUser);
        return Arrays.stream(NotificationType.values())
                .map(type -> emailTemplateRepository.findByCode(type.name())
                        .map(template -> new TemplateView(template.getCode(), template.getSubject(),
                                template.getBodyHtml(), template.getUpdatedAt(), true))
                        .orElse(new TemplateView(type.name(), null, null, null, false)))
                .toList();
    }

    /** RG-11 - un gabarit d'e-mail est une donnée de configuration, journalisée. */
    @Transactional
    public TemplateView update(User actingUser, String code, String subject, String bodyHtml) {
        requireFunctionalAdmin(actingUser);
        if (!isKnownCode(code)) {
            throw new AdministrationValidationException("UNKNOWN_TEMPLATE_CODE",
                    "Ce code ne correspond à aucun type de notification (§6.8).");
        }
        EmailTemplate template = emailTemplateRepository.findByCode(code)
                .orElse(new EmailTemplate(code, subject, bodyHtml));
        template.setSubject(subject);
        template.setBodyHtml(bodyHtml);
        template = emailTemplateRepository.save(template);

        auditService.record(actingUser, "UPDATE_EMAIL_TEMPLATE", "EmailTemplate", code, "subject=" + subject);
        return new TemplateView(template.getCode(), template.getSubject(), template.getBodyHtml(), template.getUpdatedAt(), true);
    }

    private boolean isKnownCode(String code) {
        return Arrays.stream(NotificationType.values()).anyMatch(type -> type.name().equals(code));
    }

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }

    public record TemplateView(String code, String subject, String bodyHtml,
                                Instant updatedAt, boolean configured) {
    }
}
