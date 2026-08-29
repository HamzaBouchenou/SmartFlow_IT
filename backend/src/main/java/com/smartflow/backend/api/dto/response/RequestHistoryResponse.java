package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/**
 * §6.4 - une ligne de la "frise d'avancement et de l'historique complet" d'une demande.
 * §3.4 - une ligne exactement par transition exécutée (RequestHistory's own javadoc) :
 * cette liste est donc toujours la traduction fidèle de RequestHistoryRepository, jamais
 * recomposée à partir d'un autre état. fromStepName est `null` pour la première entrée
 * d'un dossier (ADR-03/ADR-14 : SUBMIT ne part d'aucune étape, REOPEN non plus).
 */
public record RequestHistoryResponse(
        Long id,
        String action,
        String fromStepName,
        String toStepName,
        Long actorId,
        String actorName,
        String comment,
        Instant occurredAt) {
}
