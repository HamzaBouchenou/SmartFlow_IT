package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.DepartmentResponse;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.User;

public final class DepartmentMapper {

    private DepartmentMapper() {
    }

    public static DepartmentResponse toResponse(Department department) {
        Department parent = department.getParent();
        User lead = department.getLead();
        return new DepartmentResponse(department.getId(), department.getName(),
                parent != null ? parent.getId() : null, parent != null ? parent.getName() : null,
                lead != null ? lead.getId() : null, lead != null ? lead.getFirstName() + " " + lead.getLastName() : null,
                department.isActive());
    }
}
