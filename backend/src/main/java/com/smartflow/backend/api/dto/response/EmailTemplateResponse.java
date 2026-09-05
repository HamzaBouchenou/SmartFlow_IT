package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/**
 * §6.10 - un gabarit d'e-mail (§6.8). `code` est toujours un `NotificationType.name()`
 * (MailService/NotificationService's own javadoc - "résolu par code égal à type.name()") :
 * ce catalogue est fermé aux sept types de notification existants, jamais une clé
 * arbitraire. `configured` distingue une ligne réellement en base d'un code sans gabarit
 * encore défini (MailService l'ignore alors silencieusement plutôt que d'échouer, §6.8).
 */
public record EmailTemplateResponse(String code, String subject, String bodyHtml, Instant updatedAt, boolean configured) {
}
