package com.smartflow.backend.application.service;

import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.exception.ProfileValidationException;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.TeamRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import com.smartflow.backend.infrastructure.repository.UserRoleAssignmentRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * §6.1 - libre-service : "Consultation et mise à jour des informations de profil
 * autorisées", et le changement de mot de passe qui accompagne toute authentification par
 * identifiant/mot de passe (§13 - "réinitialisation sécurisée"). Distinct de UserAdminService
 * (FUNCTIONAL_ADMIN, agit sur un tiers) : ici actingUser agit toujours sur lui-même - aucun
 * paramètre userId nulle part dans cette classe, par construction plutôt que par une garde
 * d'autorisation à vérifier à chaque appel. "Informations de profil autorisées" exclut
 * l'e-mail (identité de connexion), le service de rattachement et le responsable
 * hiérarchique - §6.1's own ligne suivante ("Gestion des rôles, du service de rattachement
 * et du responsable hiérarchique") les réserve à UserAdminService.updateUser.
 */
@Service
public class ProfileService {

    /** Garde-fou contre un parent mal configuré, pas une profondeur attendue - même borne
     * qu'AuthorizationService.ancestorIdsOf, qui remonte la même chaîne. */
    private static final int MAX_DEPARTMENT_DEPTH = 20;

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final DepartmentRepository departmentRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public ProfileService(UserRepository userRepository,
                           UserRoleAssignmentRepository userRoleAssignmentRepository,
                           DepartmentRepository departmentRepository, TeamRepository teamRepository,
                           PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.departmentRepository = departmentRepository;
        this.teamRepository = teamRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    /**
     * §6.1 - "Consultation ... des informations de profil autorisées" : le rattachement et
     * les habilitations de l'appelant, en lecture seule. Les mêmes données existent déjà
     * côté administration (UserAdminService, sur un tiers) ; ici elles ne sortent jamais du
     * compte de l'appelant, sans aucun paramètre userId - la même construction que le reste
     * de cette classe.
     *
     * Le compte est relu par son id plutôt que lu depuis `actingUser` : le principal vient
     * de la session HTTP (ADR-01), donc son entité est détachée et ses associations
     * paresseuses (`department`, `manager`) ne sont pas chargeables telles quelles.
     */
    @Transactional(readOnly = true)
    public ProfileView getProfile(User actingUser) {
        User user = userRepository.findById(actingUser.getId())
                .orElseThrow(() -> new EntityNotFoundException("Compte introuvable."));

        Department department = user.getDepartment();
        User manager = user.getManager();
        List<RoleView> roles = userRoleAssignmentRepository.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(assignment -> assignment.getRole().name()))
                .map(assignment -> new RoleView(assignment.getRole().name(), assignment.getScopeType().name(),
                        scopeLabelOf(assignment)))
                .toList();

        return new ProfileView(user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.isActive(),
                department != null ? department.getId() : null, department != null ? department.getName() : null,
                directionNameOf(department),
                manager != null ? manager.getFirstName() + " " + manager.getLastName() : null,
                roles);
    }

    /**
     * §5.1 - le nom de l'objet que désigne le périmètre d'une habilitation. OWN et GLOBAL ne
     * désignent aucun objet (UserRoleAssignment's own javadoc) et n'ont donc pas de libellé.
     */
    private String scopeLabelOf(UserRoleAssignment assignment) {
        Long scopeId = assignment.getScopeId();
        if (scopeId == null) {
            return null;
        }
        return switch (assignment.getScopeType()) {
            case TEAM -> teamRepository.findById(scopeId).map(team -> team.getName()).orElse(null);
            case DEPARTMENT, DIRECTION -> departmentRepository.findById(scopeId).map(Department::getName).orElse(null);
            case OWN, GLOBAL -> null;
        };
    }

    /**
     * §4.1 - la direction est la racine de la chaîne `Department.parent` (celle sans parent),
     * exactement comme ScopeRule.DIRECTION la définit. Un service directement rattaché à rien
     * est donc sa propre direction, et le rattachement affiche alors deux fois le même nom
     * plutôt que d'inventer un niveau qui n'existe pas dans le référentiel.
     */
    private String directionNameOf(Department department) {
        Department current = department;
        int depth = 0;
        while (current != null && current.getParent() != null && depth++ < MAX_DEPARTMENT_DEPTH) {
            current = departmentRepository.findById(current.getParent().getId()).orElse(null);
        }
        return current != null ? current.getName() : null;
    }

    @Transactional
    public User updateProfile(User actingUser, String firstName, String lastName) {
        actingUser.setFirstName(firstName);
        actingUser.setLastName(lastName);
        User saved = userRepository.save(actingUser);
        // RG-11 - modification d'un compte, même par son propre titulaire.
        auditService.record(actingUser, "UPDATE_PROFILE", "User", saved.getId().toString(), null);
        return saved;
    }

    /**
     * §13 - contrairement à UserAdminService.resetPassword (geste administratif, ne connaît
     * pas l'ancien mot de passe), exige et vérifie currentPassword avant d'accepter
     * newPassword - c'est ce qui distingue un changement volontaire d'une réinitialisation
     * imposée par un tiers habilité.
     */
    @Transactional
    public void changePassword(User actingUser, String currentPassword, String newPassword) {
        if (!passwordEncoder.matches(currentPassword, actingUser.getPasswordHash())) {
            throw new ProfileValidationException("INVALID_CURRENT_PASSWORD", "Le mot de passe actuel est incorrect.");
        }
        actingUser.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(actingUser);
        // RG-11 ; jamais le mot de passe lui-même dans le résumé d'audit (§13), comme
        // UserAdminService.resetPassword le fait déjà pour le geste administratif équivalent.
        auditService.record(actingUser, "CHANGE_PASSWORD", "User", actingUser.getId().toString(), null);
    }

    /** §6.1 - le profil de l'appelant tel qu'il a le droit de le consulter. */
    public record ProfileView(Long id, String firstName, String lastName, String email, boolean active,
                               Long departmentId, String departmentName, String directionName, String managerName,
                               List<RoleView> roles) {
    }

    /** §5.1 - une habilitation, avec le nom de l'objet que son périmètre désigne. */
    public record RoleView(String role, String scopeType, String scopeLabel) {
    }
}
