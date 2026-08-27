package com.smartflow.backend.application.service;

import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.RequestStatus;

import java.time.Instant;

/**
 * §6.6 - "Filtres par statut, priorité, date, demandeur, catégorie et retard", tous
 * optionnels et combinables. "Retard" est overdue=true, lu directement sur
 * Request.slaStatus (matérialisé par le balayage SLA planifié, jamais recalculé ici -
 * CLAUDE.md : "Ce qu'il ne faut jamais faire - calculer un statut SLA à la volée"). "Date"
 * est la fenêtre de soumission (submittedFrom/submittedTo).
 */
public record TaskQueueFilter(RequestStatus status, Priority priority, Long requesterId, String category,
                               Boolean overdue, Instant submittedFrom, Instant submittedTo) {

    public static TaskQueueFilter none() {
        return new TaskQueueFilter(null, null, null, null, null, null, null);
    }
}
