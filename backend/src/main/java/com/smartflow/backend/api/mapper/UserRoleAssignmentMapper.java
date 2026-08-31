package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.UserRoleAssignmentResponse;
import com.smartflow.backend.domain.entity.UserRoleAssignment;

public final class UserRoleAssignmentMapper {

    private UserRoleAssignmentMapper() {
    }

    /** scopeName est résolu par l'appelant (UserRoleAssignmentAdminService.resolveScopeName)
     * plutôt qu'ici : ce mapper reste une fonction pure, sans accès repository. */
    public static UserRoleAssignmentResponse toResponse(UserRoleAssignment assignment, String scopeName) {
        return new UserRoleAssignmentResponse(assignment.getId(), assignment.getUser().getId(),
                assignment.getRole().name(), assignment.getScopeType().name(), assignment.getScopeId(), scopeName);
    }
}
