package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.NotificationResponse;
import com.smartflow.backend.domain.entity.Notification;
import com.smartflow.backend.domain.entity.Request;

public final class NotificationMapper {

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(Notification notification) {
        Request request = notification.getRequest();
        return new NotificationResponse(notification.getId(), notification.getType().name(),
                request != null ? request.getId() : null, request != null ? request.getReference() : null,
                notification.getTitle(), notification.getBody(), notification.getReadAt(), notification.getCreatedAt());
    }
}
