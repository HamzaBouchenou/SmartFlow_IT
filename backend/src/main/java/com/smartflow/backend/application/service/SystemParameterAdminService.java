package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.crosscutting.security.LoginAttemptListener;
import com.smartflow.backend.domain.entity.SystemParameter;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.AdministrationValidationException;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.scheduler.RequestArchivalScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * §6.10 - "Paramètres généraux : formats acceptés, taille maximale des fichiers, durée des
 * sessions et seuils d'alerte." (la durée des sessions elle-même n'est pas encore
 * administrable, voir CATALOG's own comment) plus les autres seuils/fenêtres déjà
 * administrables via SystemParameter ailleurs dans le code (RG-08, RG-09, RG-12, §13). Ce
 * service n'ajoute aucune nouvelle règle métier : chaque clé du catalogue reste lue,
 * parsée et par défaut exactement comme avant, dans la classe qui la consomme
 * (AttachmentService, LoginAttemptListener, WorkflowTransitionService,
 * RequestArchivalScheduler, AuthorizationService) - CATALOG n'est qu'une vue
 * d'administration par-dessus SystemParameterRepository, sa propre copie du libellé/
 * défaut/type de chaque clé étant strictement informative (à tenir synchronisée
 * manuellement si l'une de ces classes change son défaut).
 */
@Service
public class SystemParameterAdminService {

    /** Type de valeur attendu pour une clé administrable, pour la validation ici et le
     * contrôle de saisie côté front - jamais utilisé par le code qui consomme la clé. */
    public enum ParameterType {
        INTEGER, LONG, BOOLEAN, TEXT
    }

    public record ParameterDescriptor(String key, String label, String description, ParameterType type,
                                       String defaultValue) {
    }

    // §6.10 - liste fermée des clés administrables : un administrateur fonctionnel ne peut
    // créer une clé arbitraire, seulement ajuster celles que le code consomme réellement
    // (CLAUDE.md, règle numéro un - rien qui ne soit rattachable à une règle de gestion).
    private static final List<ParameterDescriptor> CATALOG = List.of(
            new ParameterDescriptor(LoginAttemptListener.MAX_ATTEMPTS_KEY,
                    "Tentatives de connexion avant verrouillage",
                    "§13 - nombre d'échecs de mot de passe consécutifs avant le verrouillage temporaire du compte (ADR-13).",
                    ParameterType.INTEGER, "5"),
            new ParameterDescriptor(LoginAttemptListener.LOCKOUT_MINUTES_KEY,
                    "Durée du verrouillage (minutes)",
                    "§13 - durée pendant laquelle un compte reste verrouillé après le seuil ci-dessus (ADR-13).",
                    ParameterType.INTEGER, "15"),
            new ParameterDescriptor(AuthorizationService.SEPARATION_OF_DUTIES_KEY,
                    "Séparation des tâches activée",
                    "§5.1 - un demandeur ne peut valider/rejeter sa propre demande tant que ce paramètre est actif.",
                    ParameterType.BOOLEAN, "true"),
            new ParameterDescriptor(AttachmentService.ALLOWED_EXTENSIONS_KEY,
                    "Extensions de pièces jointes autorisées",
                    "RG-09 - liste blanche, séparée par des virgules, sans le point (ex. pdf,docx,png).",
                    ParameterType.TEXT, "pdf,doc,docx,xls,xlsx,ppt,pptx,png,jpg,jpeg,gif,txt,csv,zip"),
            new ParameterDescriptor(AttachmentService.MAX_SIZE_BYTES_KEY,
                    "Taille maximale d'une pièce jointe (octets)",
                    "RG-09 - taille maximale contrôlée côté serveur, en octets.",
                    ParameterType.LONG, "10000000"),
            new ParameterDescriptor(WorkflowTransitionService.REOPEN_WINDOW_DAYS_KEY,
                    "Fenêtre de réouverture (jours)",
                    "RG-08 - durée pendant laquelle une demande clôturée peut être rouverte.",
                    ParameterType.INTEGER, "30"),
            new ParameterDescriptor(RequestArchivalScheduler.ARCHIVE_AFTER_MONTHS_KEY,
                    "Délai avant archivage (mois)",
                    "RG-12 - ancienneté, en mois, à partir de laquelle une demande close/annulée est archivée.",
                    ParameterType.INTEGER, "24")
    );

    private final SystemParameterRepository systemParameterRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public SystemParameterAdminService(SystemParameterRepository systemParameterRepository,
                                        AuthorizationService authorizationService, AuditService auditService) {
        this.systemParameterRepository = systemParameterRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    /** §11.1 - 404 et non 403 pour un écran hors périmètre (voir AuthorizationService.
     * isFunctionalAdmin's own javadoc). */
    @Transactional(readOnly = true)
    public List<ParameterView> listAll(User actingUser) {
        requireFunctionalAdmin(actingUser);
        return CATALOG.stream()
                .map(descriptor -> {
                    var override = systemParameterRepository.findByKey(descriptor.key());
                    String value = override.map(SystemParameter::getValue).orElse(descriptor.defaultValue());
                    return new ParameterView(descriptor, value, override.isPresent());
                })
                .toList();
    }

    /** RG-11 - un changement de paramètre est un changement de configuration, journalisé.
     * Renvoie l'état à jour pour que l'appelant n'ait pas besoin d'un second GET. */
    @Transactional
    public ParameterView update(User actingUser, String key, String rawValue) {
        requireFunctionalAdmin(actingUser);
        ParameterDescriptor descriptor = CATALOG.stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AdministrationValidationException("UNKNOWN_PARAMETER",
                        "Ce paramètre n'existe pas ou n'est pas administrable."));
        validate(descriptor, rawValue);

        String previousValue = currentValue(descriptor);
        SystemParameter parameter = systemParameterRepository.findByKey(key).orElse(new SystemParameter(key, rawValue));
        parameter.setValue(rawValue);
        parameter.setDescription(descriptor.description());
        systemParameterRepository.save(parameter);

        auditService.record(actingUser, "UPDATE_PARAMETER", "SystemParameter", key,
                "value: " + previousValue + " -> " + rawValue);
        return new ParameterView(descriptor, rawValue, true);
    }

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }

    private String currentValue(ParameterDescriptor descriptor) {
        return systemParameterRepository.findByKey(descriptor.key())
                .map(SystemParameter::getValue)
                .orElse(descriptor.defaultValue());
    }

    private void validate(ParameterDescriptor descriptor, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new AdministrationValidationException("INVALID_PARAMETER_VALUE", "La valeur ne peut pas être vide.");
        }
        switch (descriptor.type()) {
            case INTEGER -> {
                try {
                    Integer.parseInt(rawValue.trim());
                } catch (NumberFormatException e) {
                    throw new AdministrationValidationException("INVALID_PARAMETER_VALUE",
                            "« " + rawValue + " » n'est pas un nombre entier valide.");
                }
            }
            case LONG -> {
                try {
                    Long.parseLong(rawValue.trim());
                } catch (NumberFormatException e) {
                    throw new AdministrationValidationException("INVALID_PARAMETER_VALUE",
                            "« " + rawValue + " » n'est pas un nombre entier valide.");
                }
            }
            case BOOLEAN -> {
                String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
                if (!normalized.equals("true") && !normalized.equals("false")) {
                    throw new AdministrationValidationException("INVALID_PARAMETER_VALUE",
                            "La valeur doit être « true » ou « false ».");
                }
            }
            case TEXT -> {
                // RG-09 - une liste d'extensions vide n'a pas de sens ici (elle refuserait
                // tous les fichiers) ; le contenu de chaque extension reste ensuite validé
                // par AttachmentValidationRule/AttachmentService, pas dupliqué ici.
            }
        }
    }

    public record ParameterView(ParameterDescriptor descriptor, String value, boolean overridden) {
    }
}
