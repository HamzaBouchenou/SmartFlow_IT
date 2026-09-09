package com.smartflow.backend.application.service;

import com.smartflow.backend.domain.entity.Notification;
import com.smartflow.backend.domain.entity.NotificationPreference;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.rule.MandatoryNotificationRule;
import com.smartflow.backend.infrastructure.mail.MailService;
import com.smartflow.backend.infrastructure.repository.NotificationPreferenceRepository;
import com.smartflow.backend.infrastructure.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

/**
 * §6.8 - "Centre de notifications... avec statut lu/non lu" + envoi d'e-mail pour les
 * événements importants. The Notification row is always created, synchronously, in the
 * caller's own transaction (RequestService.submit, WorkflowTransitionService.execute) - it
 * must never survive a rollback of the action that triggered it. Only the e-mail itself
 * (infrastructure/mail/MailService.sendAsync) runs off-thread, per CLAUDE.md's own
 * "Exécution asynchrone pour ne pas ralentir les actions utilisateur".
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final MandatoryNotificationRule mandatoryNotificationRule;
    private final MailService mailService;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
                                NotificationPreferenceRepository notificationPreferenceRepository,
                                MandatoryNotificationRule mandatoryNotificationRule, MailService mailService, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.mandatoryNotificationRule = mandatoryNotificationRule;
        this.mailService = mailService;
        this.clock = clock;
    }

    /**
     * emailVariables feeds infrastructure/mail/MailService's {{placeholder}} substitution
     * against the EmailTemplate whose code equals type.name() (V6 migration seeds one per
     * NotificationType). ADR-12 (docs/DECISIONS.md) - SLA_WARNING/SLA_BREACH always send an
     * e-mail regardless of NotificationPreference ; every other type respects it, and the
     * absence of a NotificationPreference row is not an opt-out (default true).
     */
    @Transactional
    public Notification notify(User recipient, NotificationType type, Request request, String title, String body,
                                Map<String, String> emailVariables) {
        Notification notification = new Notification(recipient, type, request, title);
        notification.setBody(body);
        notification = notificationRepository.save(notification);

        if (emailEnabledFor(recipient, type)) {
            mailService.sendAsync(recipient.getEmail(), type.name(), emailVariables);
        }
        return notification;
    }

    @Transactional
    public void markRead(User actingUser, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(n -> n.getRecipient().getId().equals(actingUser.getId()))
                .orElseThrow(() -> new EntityNotFoundException("Notification introuvable."));
        if (notification.getReadAt() == null) {
            notification.setReadAt(clock.instant());
            notificationRepository.save(notification);
        }
    }

    /**
     * §6.8 - "avec statut lu/non lu" : le centre affiche les deux onglets, donc le filtre
     * appartient au serveur. Le faire côté écran sur une page déjà paginée donnerait un
     * compteur faux dès la deuxième page.
     */
    @Transactional(readOnly = true)
    public Page<Notification> list(User actingUser, Pageable pageable, boolean unreadOnly) {
        if (unreadOnly) {
            return notificationRepository.findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(actingUser.getId(), pageable);
        }
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(actingUser.getId(), pageable);
    }

    /**
     * §6.8 - "tout marquer comme lu", en une écriture plutôt qu'un appel par ligne : borné
     * aux notifications de l'appelant par la requête elle-même (RG-06), donc sans garde
     * d'autorisation à oublier. Retourne le nombre réellement marqué.
     */
    @Transactional
    public int markAllRead(User actingUser) {
        var unread = notificationRepository.findByRecipientIdAndReadAtIsNull(actingUser.getId());
        unread.forEach(notification -> notification.setReadAt(clock.instant()));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    @Transactional(readOnly = true)
    public long unreadCount(User actingUser) {
        return notificationRepository.countByRecipientIdAndReadAtIsNull(actingUser.getId());
    }

    private boolean emailEnabledFor(User recipient, NotificationType type) {
        if (mandatoryNotificationRule.isMandatory(type)) {
            return true;
        }
        return notificationPreferenceRepository.findByUserIdAndNotificationType(recipient.getId(), type)
                .map(NotificationPreference::isEmailEnabled)
                .orElse(true);
    }
}
