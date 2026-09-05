package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * §6.9 - "Vue responsable : volumes par statut, catégorie, agent et période" et les quatre
 * indicateurs. reopenRatePercent (RG-08/ADR-14) est la part des demandes déjà clôturées au
 * moins une fois sur la période qui portent une ligne d'historique REOPEN. average*Minutes,
 * slaComplianceRatePercent et reopenRatePercent sont `null` quand aucune donnée n'existe
 * encore pour la période (pas de division par zéro déguisée en 0%).
 */
public record DashboardResponse(
        Long serviceId,
        String serviceName,
        Instant from,
        Instant to,
        Map<String, Long> volumesByStatus,
        Map<String, Long> volumesByCategory,
        Map<String, Long> volumesByAgent,
        Double averageFirstResponseMinutes,
        Double averageResolutionMinutes,
        Double slaComplianceRatePercent,
        Double reopenRatePercent) {
}
