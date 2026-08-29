package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/** §6.4/§9.4 - un commentaire du fil de discussion d'une demande. */
public record CommentResponse(Long id, Long authorId, String authorName, String body, Instant createdAt) {
}
