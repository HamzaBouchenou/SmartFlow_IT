package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/** §6.8 - une entrée du centre de notifications. */
public record NotificationResponse(Long id, String type, Long requestId, String requestReference, String title,
                                    String body, Instant readAt, Instant createdAt) {
}
