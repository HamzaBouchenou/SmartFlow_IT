package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.Team;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.enums.Role;
import com.smartflow.backend.domain.enums.ScopeType;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * §5.1/§6.10 - "Les droits seront attribués par rôle et, lorsque nécessaire, limités à un
 * service ou une direction" ; "Les opérations d'administration et les changements de
 * droits seront journalisés." Distinct de UserAdminService (le compte) : accorder ou
 * révoquer un rôle est un cas d'usage séparé. Contrairement aux référentiels du reste du
 * §6.10 (jamais de suppression physique, RG-02/RG-12), une UserRoleAssignment n'a pas de
 * drapeau `active` et révoquer un droit est une suppression réelle, pas une désactivation
 * logique - RG-02 protège une "demande soumise", pas une affectation de rôle ; RG-11 est
 * satisfaite par la ligne d'audit écrite ici, jamais par la conservation de la ligne.
 */
@Service
public class UserRoleAssignmentAdminService {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final UserAdminService userAdminService;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public UserRoleAssignmentAdminService(UserRoleAssignmentRepository userRoleAssignmentRepository,
                                           UserAdminService userAdminService, DepartmentRepository departmentRepository,
                                           TeamRepository teamRepository, AuthorizationService authorizationService,
                                           AuditService auditService) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.userAdminService = userAdminService;
        this.departmentRepository = departmentRepository;
        this.teamRepository = teamRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<UserRoleAssignment> list(User actingUser, Long userId) {
        requireFunctionalAdmin(actingUser);
        userAdminService.getUser(userId);
        return userRoleAssignmentRepository.findByUserId(userId);
    }

    @Transactional
    public UserRoleAssignment grant(User actingUser, Long userId, Role role, ScopeType scopeType, Long scopeId) {
        requireFunctionalAdmin(actingUser);
        User target = userAdminService.getUser(userId);
        validateScope(scopeType, scopeId);

        UserRoleAssignment assignment = userRoleAssignmentRepository.save(new UserRoleAssignment(target, role, scopeType, scopeId));
        auditService.record(actingUser, "GRANT_ROLE", "UserRoleAssignment", assignment.getId().toString(),
                "user=" + userId + ", role=" + role + ", scope=" + scopeType + "/" + scopeId);
        return assignment;
    }

    @Transactional
    public void revoke(User actingUser, Long userId, Long assignmentId) {
        requireFunctionalAdmin(actingUser);
        UserRoleAssignment assignment = userRoleAssignmentRepository.findById(assignmentId)
                .filter(candidate -> candidate.getUser().getId().equals(userId))
                .orElseThrow(() -> new EntityNotFoundException("Affectation de rôle introuvable."));
        userRoleAssignmentRepository.delete(assignment);
        auditService.record(actingUser, "REVOKE_ROLE", "UserRoleAssignment", assignmentId.toString(),
                "user=" + userId + ", role=" + assignment.getRole());
    }

    /** Résout un id de périmètre en libellé lisible ; `null` pour OWN/GLOBAL ou une entité
     * depuis supprimée - jamais une exception, cet écran reste lisible même sur des
     * données orphelines. */
    public String resolveScopeName(ScopeType scopeType, Long scopeId) {
        if (scopeId == null) {
            return null;
        }
        return switch (scopeType) {
            case TEAM -> teamRepository.findById(scopeId).map(Team::getName).orElse(null);
            case DEPARTMENT, DIRECTION -> departmentRepository.findById(scopeId).map(Department::getName).orElse(null);
            case OWN, GLOBAL -> null;
        };
    }

    private void validateScope(ScopeType scopeType, Long scopeId) {
        if (scopeId == null) {
            return;
        }
        switch (scopeType) {
            case TEAM -> teamRepository.findById(scopeId)
                    .orElseThrow(() -> new EntityNotFoundException("Équipe introuvable."));
            case DEPARTMENT, DIRECTION -> departmentRepository.findById(scopeId)
                    .orElseThrow(() -> new EntityNotFoundException("Direction/service introuvable."));
            case OWN, GLOBAL -> { }
        }
    }

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }
}
