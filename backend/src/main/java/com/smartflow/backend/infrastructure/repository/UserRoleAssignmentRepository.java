package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
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

    // §6.6 - "affectation automatique" : les membres d'une équipe (scope TEAM), candidats à
    // AutoAssignmentRule. Même modèle que TaskQueueService.teamTasks pour identifier "qui
    // tient une affectation de rôle scope=TEAM sur cette équipe".
    List<UserRoleAssignment> findByScopeTypeAndScopeId(ScopeType scopeType, Long scopeId);
}
