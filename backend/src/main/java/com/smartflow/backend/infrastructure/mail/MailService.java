package com.smartflow.backend.infrastructure.mail;

import com.smartflow.backend.domain.entity.EmailTemplate;
import com.smartflow.backend.infrastructure.repository.EmailTemplateRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * §6.8 - "Envoi d'e-mails pour les événements importants" ; §6.8/CLAUDE.md - "Exécution
 * asynchrone pour ne pas ralentir les actions utilisateur". The Notification row itself
 * (application/service/NotificationService) is written synchronously, in the same
 * transaction as the triggering action, so it never survives a rollback the action itself
 * didn't - only the actual SMTP round-trip below runs off the caller's thread.
 *
 * The template is resolved by code from EmailTemplate (§6.10 - "modèles d'e-mail"
 * administrables) ; a missing template logs a warning and skips the send rather than
 * failing - a misconfigured or not-yet-created template must never break the workflow
 * action that triggered it (the in-app Notification, §6.8's other channel, is unaffected).
 * A send failure (SMTP unreachable, ...) is caught and logged for the same reason.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender javaMailSender;
    private final EmailTemplateRepository emailTemplateRepository;
    private final String fromAddress;

    public MailService(JavaMailSender javaMailSender, EmailTemplateRepository emailTemplateRepository,
                        @Value("${smartflow.mail.from}") String fromAddress) {
        this.javaMailSender = javaMailSender;
        this.emailTemplateRepository = emailTemplateRepository;
        this.fromAddress = fromAddress;
    }

    @Async
    public void sendAsync(String toAddress, String templateCode, Map<String, String> variables) {
        Optional<EmailTemplate> template = emailTemplateRepository.findByCode(templateCode);
        if (template.isEmpty()) {
            log.warn("No EmailTemplate configured for code={}, skipping e-mail to {}", templateCode, toAddress);
            return;
        }
        String subject = interpolate(template.get().getSubject(), variables);
        String bodyHtml = interpolate(template.get().getBodyHtml(), variables);
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(toAddress);
            helper.setSubject(subject);
            helper.setText(bodyHtml, true);
            javaMailSender.send(message);
        } catch (MessagingException | MailException e) {
            log.warn("Failed to send e-mail (code={}) to {}: {}", templateCode, toAddress, e.getMessage());
        }
    }

    private static String interpolate(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return result;
    }
}
