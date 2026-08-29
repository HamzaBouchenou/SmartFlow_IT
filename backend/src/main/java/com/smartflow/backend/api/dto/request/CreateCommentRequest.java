package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** §6.4 - "Ajout de commentaires... " sur une demande. */
public record CreateCommentRequest(@NotBlank String body) {
}
