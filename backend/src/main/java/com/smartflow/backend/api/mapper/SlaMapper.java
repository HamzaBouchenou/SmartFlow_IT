package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.SlaResponse;
import com.smartflow.backend.domain.entity.Sla;

public final class SlaMapper {

    private SlaMapper() {
    }

    public static SlaResponse toResponse(Sla sla) {
        return new SlaResponse(sla.getId(), sla.getRequestType().getId(), sla.getPriority().name(),
                sla.getFirstResponseMinutes(), sla.getResolutionMinutes(), sla.isUseBusinessCalendar());
    }
}
