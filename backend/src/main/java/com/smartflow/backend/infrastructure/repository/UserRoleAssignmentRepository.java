package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {

    // canAct (application/security) evaluates every grant a user holds - a user can carry
    // several assignments, e.g. AGENT on one team and MANAGER on another (UserRoleAssignment
    // javadoc). Also backs the authorities loaded at login (crosscutting/security).
    List<UserRoleAssignment> findByUserId(Long userId);

    // §6.7 - "escalade au responsable" (AuthorizationService.findResponsibleManagers) :
    // every assignment of one role, whatever the user, to be filtered by scope afterwards.
    List<UserRoleAssignment> findByRole(Role role);
}
