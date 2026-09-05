package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.SystemParameterResponse;
import com.smartflow.backend.application.service.SystemParameterAdminService.ParameterView;

public final class SystemParameterMapper {

    private SystemParameterMapper() {
    }

    public static SystemParameterResponse toResponse(ParameterView view) {
        var descriptor = view.descriptor();
        return new SystemParameterResponse(descriptor.key(), descriptor.label(), descriptor.description(),
                descriptor.type().name(), view.value(), view.overridden());
    }
}
