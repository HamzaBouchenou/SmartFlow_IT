package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.ServiceCatalogResponse;
import com.smartflow.backend.domain.entity.ServiceCatalog;

public final class ServiceCatalogMapper {

    private ServiceCatalogMapper() {
    }

    public static ServiceCatalogResponse toResponse(ServiceCatalog serviceCatalog) {
        return new ServiceCatalogResponse(serviceCatalog.getId(), serviceCatalog.getName(),
                serviceCatalog.getDescription(), serviceCatalog.getCategory(),
                serviceCatalog.getDepartment().getId(), serviceCatalog.getDisplayOrder());
    }
}
