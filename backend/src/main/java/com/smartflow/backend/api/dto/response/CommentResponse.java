package com.smartflow.backend.api.dto.response;

import java.time.Instant;
import java.util.List;

/** §6.4/§9.4 - un commentaire du fil de discussion d'une demande.
 *
 * `mentions` porte les personnes **effectivement retenues** (ADR-24) : une adresse inconnue
 * ou hors périmètre n'y figure pas, ce qui suffit à l'auteur pour constater qu'elle n'a pas
 * été notifiée, sans rien lui apprendre sur la raison. */
public record CommentResponse(Long id, Long authorId, String authorName, String body, Instant createdAt,
                               List<MentionResponse> mentions) {

    /** Une personne mentionnée, telle que l'écran l'affiche. Jamais l'adresse e-mail : le
     * jeton de saisie est l'adresse (ADR-24), mais l'afficher en retour publierait l'annuaire
     * à qui lit le fil. */
    public record MentionResponse(Long userId, String name) {
    }
}
