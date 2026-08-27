package com.smartflow.backend.domain.exception;

/**
 * A resource does not exist, or exists outside the caller's authorization perimeter.
 * CLAUDE.md: "404 et non 403 pour une ressource hors périmètre : un 403 confirme
 * l'existence du dossier et permet d'énumérer ceux des autres services." canAct
 * (application/security) must throw this - not a 403-mapped exception - whenever a caller
 * is outside a resource's scope, exactly as it does when the resource is genuinely absent,
 * so the HTTP response never lets the two cases be told apart.
 */
public class EntityNotFoundException extends BusinessException {

    public EntityNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
