package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/**
 * §6.6 - une ligne de file de travail ("Mes tâches" / file d'équipe). Plus léger que
 * RequestDetailResponse : une liste n'a pas besoin des valeurs de champ ni du calcul
 * availableActions[] par ligne.
 */
public record RequestSummaryResponse(
        Long id,
        String reference,
        String title,
        String status,
        String priority,
        String category,
        String requesterName,
        String currentStepName,
        Instant submittedAt,
        String slaStatus) {
}
