package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.RequestTypeAdminResponse;
import com.smartflow.backend.domain.entity.RequestType;

public final class RequestTypeAdminMapper {

    private RequestTypeAdminMapper() {
    }

    public static RequestTypeAdminResponse toResponse(RequestType requestType) {
        return new RequestTypeAdminResponse(requestType.getId(), requestType.getServiceCatalog().getId(),
                requestType.getName(), requestType.getDescription(), requestType.getTargetDelayDescription(),
                requestType.getRequiredDocuments(), requestType.getContactInfo(), requestType.isReopenAllowed(),
                requestType.getDisplayOrder(), requestType.isActive());
    }
}
