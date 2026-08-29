package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * §6.9 - "Vue responsable : volumes par statut, catégorie, agent et période" et les quatre
 * indicateurs. reopenRatePercent reste toujours 0.0 - RG-08 (réouverture) n'est pas encore
 * implémentée (docs/DECISIONS.md n'a pas encore tranché sa durée paramétrable) ; le champ
 * existe déjà pour ne pas casser ce contrat une fois RG-08 posée. average*Minutes et
 * slaComplianceRatePercent sont `null` quand aucune donnée n'existe encore pour la période
 * (pas de division par zéro déguisée en 0%).
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
