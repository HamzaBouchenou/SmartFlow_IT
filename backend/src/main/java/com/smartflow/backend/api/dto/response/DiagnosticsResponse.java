package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/**
 * §6.10/§15.3 - "Page de diagnostic affichant l'état des services techniques sans exposer
 * de secrets." Chaque statut est "UP", "DOWN" ou "DISABLED" (le service IA seulement,
 * §12.2 - mode désactivé) ; jamais un hôte, un port ou une chaîne de connexion.
 */
public record DiagnosticsResponse(
        String backendStatus,
        String databaseStatus,
        String aiServiceStatus,
        String mailStatus,
        Instant checkedAt) {
}
