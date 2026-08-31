package com.smartflow.backend.application.service;

import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.ProfileValidationException;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public ProfileService(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
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
}
