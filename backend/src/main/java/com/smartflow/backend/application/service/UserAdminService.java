package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.AdministrationValidationException;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * §6.1/§6.10 - "Gestion des utilisateurs" : création, mise à jour, activation/
 * désactivation logique (RG-02), réinitialisation de mot de passe et déverrouillage
 * (ADR-13). Toujours un geste d'administration sur un tiers, réservé à FUNCTIONAL_ADMIN -
 * un compte est toujours provisionné par un administrateur (§4.2 ne demande aucun flux
 * d'auto-inscription), exactement comme le jeu de données de démonstration (V5) l'a
 * toujours fait par SQL direct jusqu'ici. Le pendant en libre-service (l'utilisateur agit
 * sur son propre compte : profil, mot de passe) est ProfileService, volontairement séparé -
 * deux autorisations différentes pour une même table. La gestion des rôles
 * (UserRoleAssignment) est un service voisin, UserRoleAssignmentAdminService, gardée
 * séparée : deux cas d'usage distincts (§5.1 le compte lui-même vs les droits qu'il porte).
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(UserRepository userRepository, DepartmentRepository departmentRepository,
                             AuthorizationService authorizationService, AuditService auditService,
                             PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<User> listUsers(User actingUser) {
        requireFunctionalAdmin(actingUser);
        return userRepository.findAll();
    }

    @Transactional
    public User createUser(User actingUser, String firstName, String lastName, String email, String rawPassword,
                            Long departmentId, Long managerId) {
        requireFunctionalAdmin(actingUser);
        requireEmailAvailable(email, null);
        User created = new User(firstName, lastName, email, passwordEncoder.encode(rawPassword));
        created.setDepartment(resolveDepartment(departmentId));
        created.setManager(resolveUser(managerId));
        created = userRepository.save(created);
        auditService.record(actingUser, "CREATE", "User", created.getId().toString(), "email=" + email);
        return created;
    }

    @Transactional
    public User updateUser(User actingUser, Long userId, String firstName, String lastName, String email,
                            Long departmentId, Long managerId) {
        requireFunctionalAdmin(actingUser);
        User target = getUser(userId);
        requireEmailAvailable(email, userId);
        target.setFirstName(firstName);
        target.setLastName(lastName);
        target.setEmail(email);
        target.setDepartment(resolveDepartment(departmentId));
        target.setManager(resolveUser(managerId));
        target = userRepository.save(target);
        auditService.record(actingUser, "UPDATE", "User", target.getId().toString(), "email=" + email);
        return target;
    }

    @Transactional
    public User setUserActive(User actingUser, Long userId, boolean active) {
        requireFunctionalAdmin(actingUser);
        User target = getUser(userId);
        target.setActive(active);
        target = userRepository.save(target);
        auditService.record(actingUser, active ? "ACTIVATE" : "DEACTIVATE", "User", target.getId().toString(), null);
        return target;
    }

    /** §6.1/§13 - jamais le mot de passe lui-même dans le résumé d'audit. */
    @Transactional
    public void resetPassword(User actingUser, Long userId, String newRawPassword) {
        requireFunctionalAdmin(actingUser);
        User target = getUser(userId);
        target.setPasswordHash(passwordEncoder.encode(newRawPassword));
        userRepository.save(target);
        auditService.record(actingUser, "RESET_PASSWORD", "User", target.getId().toString(), null);
    }

    /** ADR-13 - un administrateur fonctionnel peut débloquer un compte verrouillé avant
     * l'expiration naturelle du verrou. */
    @Transactional
    public User unlockAccount(User actingUser, Long userId) {
        requireFunctionalAdmin(actingUser);
        User target = getUser(userId);
        target.setFailedLoginAttempts(0);
        target.setLockedUntil(null);
        target = userRepository.save(target);
        auditService.record(actingUser, "UNLOCK", "User", target.getId().toString(), null);
        return target;
    }

    /**
     * Public, sans contrôle d'accès propre - comme RequestService.toDetailView's own
     * javadoc l'explique pour le même motif : jamais exposé par un contrôleur sans qu'un
     * appel à requireFunctionalAdmin ait déjà eu lieu juste avant dans le même appelant
     * (UserController le fait systématiquement ; UserRoleAssignmentAdminService aussi).
     */
    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable."));
    }

    private void requireEmailAvailable(String email, Long excludingUserId) {
        userRepository.findByEmail(email)
                .filter(existing -> !existing.getId().equals(excludingUserId))
                .ifPresent(existing -> {
                    throw new AdministrationValidationException("EMAIL_ALREADY_USED",
                            "Cette adresse e-mail est déjà utilisée par un autre compte.");
                });
    }

    private Department resolveDepartment(Long departmentId) {
        return departmentId != null
                ? departmentRepository.findById(departmentId)
                        .orElseThrow(() -> new EntityNotFoundException("Direction/service introuvable."))
                : null;
    }

    private User resolveUser(Long userId) {
        return userId != null ? getUser(userId) : null;
    }

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }
}
