package com.smartflow.backend.api.dto.response;

/** §6.7/§6.10 - cibles SLA d'un (RequestType, Priority). */
public record SlaResponse(
        Long id,
        Long requestTypeId,
        String priority,
        int firstResponseMinutes,
        int resolutionMinutes,
        boolean useBusinessCalendar) {
}
