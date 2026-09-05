package com.smartflow.backend.api.dto.response;

import java.util.List;

/**
 * §6.6 - résultat d'une action en masse : chaque demande réussit ou échoue indépendamment
 * (un id invalide, ou hors périmètre pour actingUser, n'empêche pas les autres d'aboutir) -
 * jamais tout-ou-rien, exactement comme une file de travail qui mélange des dossiers de
 * légitimité différente pour l'appelant.
 */
public record BulkActionResultResponse(List<Item> results) {

    public record Item(Long requestId, boolean success, String errorCode) {
    }
}
