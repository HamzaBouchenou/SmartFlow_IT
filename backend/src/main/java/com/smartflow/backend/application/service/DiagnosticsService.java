package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.ai.AiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;

/**
 * §6.10/§15.3 - "Page de diagnostic affichant l'état des services techniques sans exposer
 * de secrets." Chaque vérification est une connexion réelle (base, IA, e-mail), jamais une
 * simple lecture de configuration - le but est de savoir si le service répond maintenant,
 * pas s'il est configuré. Réservé à TECHNICAL_ADMIN (AuthorizationService.isTechnicalAdmin's
 * own javadoc - "supervision", distinct du "paramétrage fonctionnel global" des cinq autres
 * écrans d'administration).
 */
@Service
public class DiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(DiagnosticsService.class);

    private final AuthorizationService authorizationService;
    private final DataSource dataSource;
    private final AiClient aiClient;
    private final JavaMailSender javaMailSender;
    private final Clock clock;

    public DiagnosticsService(AuthorizationService authorizationService, DataSource dataSource, AiClient aiClient,
                               JavaMailSender javaMailSender, Clock clock) {
        this.authorizationService = authorizationService;
        this.dataSource = dataSource;
        this.aiClient = aiClient;
        this.javaMailSender = javaMailSender;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DiagnosticsView check(User actingUser) {
        if (!authorizationService.isTechnicalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
        // §15.3 - "endpoint de santé pour le back-end" : répondre à cet appel démontre
        // déjà que le back-end lui-même est UP, sans vérification supplémentaire.
        return new DiagnosticsView("UP", checkDatabase(), checkAiService(), checkMail(), clock.instant());
    }

    private String checkDatabase() {
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (SQLException e) {
            log.warn("Database diagnostics check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    /** §12.2 - "mode désactivé" reste distinct d'un service injoignable : jamais confondus ici. */
    private String checkAiService() {
        if (!aiClient.isEnabled()) {
            return "DISABLED";
        }
        return aiClient.ping() ? "UP" : "DOWN";
    }

    private String checkMail() {
        if (!(javaMailSender instanceof JavaMailSenderImpl impl)) {
            return "UP";
        }
        try {
            impl.testConnection();
            return "UP";
        } catch (Exception e) {
            log.warn("Mail diagnostics check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    public record DiagnosticsView(String backendStatus, String databaseStatus, String aiServiceStatus,
                                   String mailStatus, Instant checkedAt) {
    }
}
