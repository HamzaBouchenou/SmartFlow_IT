package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.ChangePasswordRequest;
import com.smartflow.backend.api.dto.request.UpdateProfileRequest;
import com.smartflow.backend.api.dto.response.UserResponse;
import com.smartflow.backend.api.mapper.UserMapper;
import com.smartflow.backend.application.service.ProfileService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

/**
 * §6.1 - libre-service sur son propre compte : "Consultation et mise à jour des
 * informations de profil autorisées" (GET reste /api/v1/auth/me, AuthController - cet
 * écran n'a rien à ajouter à la lecture) et changement de mot de passe. Distinct de
 * /api/v1/admin/users (UserController, FUNCTIONAL_ADMIN sur un tiers) : ici aucun
 * :id dans l'URL - toujours principal.getUser() lui-même, jamais un paramètre de chemin.
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final Clock clock;

    public ProfileController(ProfileService profileService, Clock clock) {
        this.profileService = profileService;
        this.clock = clock;
    }

    @PutMapping
    public UserResponse updateProfile(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                       @Valid @RequestBody UpdateProfileRequest body) {
        var updated = profileService.updateProfile(principal.getUser(), body.firstName(), body.lastName());
        return UserMapper.toResponse(updated, clock.instant());
    }

    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                @Valid @RequestBody ChangePasswordRequest body) {
        profileService.changePassword(principal.getUser(), body.currentPassword(), body.newPassword());
    }
}
