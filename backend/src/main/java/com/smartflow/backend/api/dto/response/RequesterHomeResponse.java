package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * §6.9 - "Vue demandeur : demandes en cours, dernières décisions et délais annoncés."
 * inProgressCount porte sur la totalité des demandes SUBMITTED de l'utilisateur, pas
 * seulement les 5 renvoyées dans requests (un résumé, pas "Mes demandes" - §6.9's own
 * étape 1 screen déjà bâti pour la liste complète paginée). slaDueAtResolution (RG-07) est
 * le "délai annoncé" lu tel quel depuis le modèle matérialisé, jamais recalculé
 * (CLAUDE.md - "Ce qu'il ne faut jamais faire").
 */
public record RequesterHomeResponse(
        long inProgressCount,
        List<RequesterRequestItem> requests,
        List<RequesterDecisionItem> recentDecisions) {

    public record RequesterRequestItem(
            Long id, String reference, String title, String status, String priority,
            String slaStatus, Instant slaDueAtResolution) {
    }

    /** action est VALIDATE/REJECT/RETURN (NotificationType.DECISION's own périmètre, WorkflowTransitionService). */
    public record RequesterDecisionItem(
            Long requestId, String reference, String action, Instant occurredAt, String comment) {
    }
}
