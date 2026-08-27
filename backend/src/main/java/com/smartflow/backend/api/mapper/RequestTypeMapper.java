package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.RequestTypeResponse;
import com.smartflow.backend.domain.entity.RequestType;

public final class RequestTypeMapper {

    private RequestTypeMapper() {
    }

    public static RequestTypeResponse toResponse(RequestType requestType) {
        return new RequestTypeResponse(requestType.getId(), requestType.getServiceCatalog().getId(),
                requestType.getName(), requestType.getDescription(), requestType.getTargetDelayDescription(),
                requestType.getRequiredDocuments(), requestType.getContactInfo(), requestType.getDisplayOrder());
    }
}
