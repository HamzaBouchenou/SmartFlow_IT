package com.smartflow.backend.application.service;

import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.NotificationPreference;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import com.smartflow.backend.domain.rule.MandatoryNotificationRule;
import com.smartflow.backend.infrastructure.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §6.8 - "Préférences de notification limitées pour éviter la désactivation des alertes
 * obligatoires". Jusqu'à ce lot, `NotificationPreference` était une table sans écrivain :
 * NotificationService la lisait déjà (l'absence de ligne valant "activé", ADR-12) mais rien
 * ne permettait à un utilisateur de la renseigner - REC-SCN-24 l'avait relevé en recette.
 *
 * Libre-service strict, même construction que ProfileService : `actingUser` agit toujours
 * sur lui-même, aucun paramètre `userId` nulle part dans cette classe, donc aucune garde
 * d'autorisation à vérifier à chaque appel. La limite du §6.8 ("éviter la désactivation des
 * alertes obligatoires") n'est pas réécrite ici : elle est déléguée à
 * MandatoryNotificationRule, exactement la même règle pure que NotificationService consulte
 * au moment d'émettre - un seul point de vérité pour "ce type est-il obligatoire ?", jamais
 * deux listes qui pourraient diverger.
 */
@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final MandatoryNotificationRule mandatoryNotificationRule;
    private final AuditService auditService;

    public NotificationPreferenceService(NotificationPreferenceRepository notificationPreferenceRepository,
                                          MandatoryNotificationRule mandatoryNotificationRule,
                                          AuditService auditService) {
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.mandatoryNotificationRule = mandatoryNotificationRule;
        this.auditService = auditService;
    }

    /**
     * Toutes les valeurs de NotificationType, avec leur état effectif pour cet utilisateur -
     * pas seulement les lignes réellement présentes en base : sans cela l'écran ne saurait
     * pas distinguer "jamais renseigné" (donc activé, ADR-12) d'un type inconnu. Un type
     * obligatoire est retourné avec `mandatory=true` pour que l'interface l'affiche verrouillé
     * plutôt que d'offrir une bascule qui serait refusée à l'envoi.
     */
    @Transactional(readOnly = true)
    public List<PreferenceView> list(User actingUser) {
        Map<NotificationType, Boolean> stored = new LinkedHashMap<>();
        for (NotificationType type : NotificationType.values()) {
            notificationPreferenceRepository.findByUserIdAndNotificationType(actingUser.getId(), type)
                    .ifPresent(preference -> stored.put(type, preference.isEmailEnabled()));
        }
        return Arrays.stream(NotificationType.values())
                .map(type -> new PreferenceView(type, stored.getOrDefault(type, true),
                        mandatoryNotificationRule.isMandatory(type)))
                .toList();
    }

    /**
     * §6.8 - refuse explicitement de désactiver un type obligatoire plutôt que d'accepter
     * puis d'ignorer silencieusement le choix : l'utilisateur doit savoir que l'alerte
     * partira quand même (ADR-12 - §6.7 fait de l'escalade un engagement non négociable).
     * Réactiver un type obligatoire reste accepté (c'est déjà son état effectif).
     */
    @Transactional
    public PreferenceView setEmailEnabled(User actingUser, NotificationType type, boolean emailEnabled) {
        if (!emailEnabled && mandatoryNotificationRule.isMandatory(type)) {
            throw new InvalidRequestStateException("NOTIFICATION_TYPE_MANDATORY",
                    "Ce type de notification est obligatoire et ne peut pas être désactivé (§6.8).");
        }
        NotificationPreference preference = notificationPreferenceRepository
                .findByUserIdAndNotificationType(actingUser.getId(), type)
                .orElseGet(() -> new NotificationPreference(actingUser, type));
        preference.setEmailEnabled(emailEnabled);
        notificationPreferenceRepository.save(preference);
        // RG-11 - une préférence de notification est une configuration de compte : le journal
        // garde qui l'a changée et quand, comme pour toute autre modification de compte.
        auditService.record(actingUser, "UPDATE_NOTIFICATION_PREFERENCE", "User", actingUser.getId().toString(),
                type.name() + ": emailEnabled=" + emailEnabled);
        return new PreferenceView(type, emailEnabled, mandatoryNotificationRule.isMandatory(type));
    }

    /** Un type de notification et son état effectif pour un utilisateur donné. */
    public record PreferenceView(NotificationType notificationType, boolean emailEnabled, boolean mandatory) {
    }
}
