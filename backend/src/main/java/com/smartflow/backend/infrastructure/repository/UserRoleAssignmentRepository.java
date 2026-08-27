package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.UserRoleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, Long> {

    // canAct (application/security) evaluates every grant a user holds - a user can carry
    // several assignments, e.g. AGENT on one team and MANAGER on another (UserRoleAssignment
    // javadoc). Also backs the authorities loaded at login (crosscutting/security).
    List<UserRoleAssignment> findByUserId(Long userId);
}
