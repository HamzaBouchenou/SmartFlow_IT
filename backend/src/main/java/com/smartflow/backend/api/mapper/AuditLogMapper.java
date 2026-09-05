package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.AuditLogResponse;
import com.smartflow.backend.domain.entity.AuditLog;
import com.smartflow.backend.domain.entity.User;

public final class AuditLogMapper {

    private AuditLogMapper() {
    }

    public static AuditLogResponse toResponse(AuditLog entry) {
        User actor = entry.getActor();
        return new AuditLogResponse(entry.getId(), actor != null ? actor.getId() : null,
                actor != null ? actor.getFirstName() + " " + actor.getLastName() : null,
                entry.getAction(), entry.getObjectType(), entry.getObjectId(), entry.getResult(),
                entry.getSummary(), entry.getTraceId(), entry.getOccurredAt());
    }
}
