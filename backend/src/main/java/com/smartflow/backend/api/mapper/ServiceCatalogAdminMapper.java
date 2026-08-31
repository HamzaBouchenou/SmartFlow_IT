package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.ServiceCatalogAdminResponse;
import com.smartflow.backend.domain.entity.ServiceCatalog;

public final class ServiceCatalogAdminMapper {

    private ServiceCatalogAdminMapper() {
    }

    public static ServiceCatalogAdminResponse toResponse(ServiceCatalog service) {
        return new ServiceCatalogAdminResponse(service.getId(), service.getName(), service.getDescription(),
                service.getCategory(), service.getDepartment().getId(), service.getDepartment().getName(),
                service.getDisplayOrder(), service.isActive());
    }
}
