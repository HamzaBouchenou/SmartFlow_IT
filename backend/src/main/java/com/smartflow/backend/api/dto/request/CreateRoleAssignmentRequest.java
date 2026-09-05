package com.smartflow.backend.api.dto.request;

import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import jakarta.validation.constraints.NotNull;

/** §5.1 - "Les droits seront attribués par rôle et, lorsque nécessaire, limités à un
 * service ou une direction." scopeId n'a de sens que pour TEAM/DEPARTMENT/DIRECTION
 * (UserRoleAssignment's own javadoc) - laissé `null` pour OWN/GLOBAL. */
public record CreateRoleAssignmentRequest(@NotNull Role role, @NotNull ScopeType scopeType, Long scopeId) {
}
