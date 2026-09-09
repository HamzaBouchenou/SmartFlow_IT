package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.ChangePasswordRequest;
import com.smartflow.backend.api.dto.request.UpdateNotificationPreferenceRequest;
import com.smartflow.backend.api.dto.request.UpdateProfileRequest;
import com.smartflow.backend.api.dto.response.MyProfileResponse;
import com.smartflow.backend.api.dto.response.NotificationPreferenceResponse;
import com.smartflow.backend.api.dto.response.RoleAssignmentResponse;
import com.smartflow.backend.api.dto.response.UserResponse;
import com.smartflow.backend.api.mapper.UserMapper;
import com.smartflow.backend.application.service.NotificationPreferenceService;
import com.smartflow.backend.application.service.ProfileService;
import com.smartflow.backend.crosscutting.security.SessionActivityFilter;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.enums.NotificationType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * §6.1 - libre-service sur son propre compte : "Consultation et mise à jour des
 * informations de profil autorisées" et changement de mot de passe. Distinct de
 * /api/v1/admin/users (UserController, FUNCTIONAL_ADMIN sur un tiers) : ici aucun
 * :id dans l'URL - toujours principal.getUser() lui-même, jamais un paramètre de chemin.
 *
 * La lecture se partage entre deux routes qui ne servent pas le même besoin :
 * GET /api/v1/auth/me dit "qui est connecté" au SPA à chaque chargement (ADR-01, réponse
 * volontairement minimale) ; GET /api/v1/profile, appelée par le seul écran de profil,
 * porte le rattachement, les habilitations et l'expiration de session.
 */
@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final NotificationPreferenceService notificationPreferenceService;
    private final Clock clock;

    public ProfileController(ProfileService profileService,
                              NotificationPreferenceService notificationPreferenceService, Clock clock) {
        this.profileService = profileService;
        this.notificationPreferenceService = notificationPreferenceService;
        this.clock = clock;
    }

    /**
     * §6.1 - "Consultation ... des informations de profil autorisées" et "Expiration de
     * session" : ce que l'utilisateur a le droit de lire de son propre compte, en une seule
     * lecture. GET /api/v1/auth/me reste la source de "qui est connecté" pour le SPA
     * (ADR-01, appelée à chaque chargement) ; cette route-ci, appelée par le seul écran de
     * profil, porte le rattachement et les habilitations qu'`AuthController` n'a pas à
     * transporter à chaque rafraîchissement de page.
     */
    @GetMapping
    public MyProfileResponse getProfile(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                         HttpServletRequest httpRequest) {
        var profile = profileService.getProfile(principal.getUser());
        List<RoleAssignmentResponse> roles = profile.roles().stream()
                .map(role -> new RoleAssignmentResponse(role.role(), role.scopeType(), role.scopeLabel()))
                .toList();
        return new MyProfileResponse(profile.id(), profile.firstName(), profile.lastName(), profile.email(),
                profile.active(), profile.departmentId(), profile.departmentName(), profile.directionName(),
                profile.managerName(), roles, sessionExpiresAt(httpRequest));
    }

    /**
     * §6.1 - l'instant d'expiration de la session courante : dernier geste réel de
     * l'utilisateur + délai d'inactivité posé par SessionTimeoutListener (donc la durée
     * administrable du §6.10, jamais une constante recopiée côté écran). Un délai nul ou
     * négatif signifie "n'expire pas" au sens de l'API servlet - la réponse ne porte alors
     * aucune échéance plutôt qu'une date inventée.
     *
     * L'origine est `lastInteractionAt` (SessionActivityFilter/ADR-22), pas
     * `getLastAccessedTime()` : c'est cette horloge-là qui décide réellement de
     * l'expiration, et elle seule ignore les appels périodiques d'arrière-plan. Prise sur
     * le dernier accès, l'échéance affichée serait repoussée par le rafraîchissement même
     * qui vient la lire - un compte à rebours qui ne descend jamais.
     */
    private static Instant sessionExpiresAt(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null || session.getMaxInactiveInterval() <= 0) {
            return null;
        }
        Object lastInteraction = session.getAttribute(SessionActivityFilter.LAST_INTERACTION_ATTRIBUTE);
        long from = lastInteraction instanceof Long millis ? millis : session.getLastAccessedTime();
        return Instant.ofEpochMilli(from).plusSeconds(session.getMaxInactiveInterval());
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

    /** §6.8 - préférences de notification, toujours celles de l'appelant (aucun :id ici). */
    @GetMapping("/notification-preferences")
    public List<NotificationPreferenceResponse> listNotificationPreferences(
            @AuthenticationPrincipal SmartFlowUserDetails principal) {
        return notificationPreferenceService.list(principal.getUser()).stream()
                .map(view -> new NotificationPreferenceResponse(view.notificationType().name(),
                        view.emailEnabled(), view.mandatory()))
                .toList();
    }

    @PutMapping("/notification-preferences/{notificationType}")
    public NotificationPreferenceResponse updateNotificationPreference(
            @AuthenticationPrincipal SmartFlowUserDetails principal,
            @PathVariable NotificationType notificationType,
            @Valid @RequestBody UpdateNotificationPreferenceRequest body) {
        var updated = notificationPreferenceService.setEmailEnabled(principal.getUser(), notificationType,
                body.emailEnabled());
        return new NotificationPreferenceResponse(updated.notificationType().name(), updated.emailEnabled(),
                updated.mandatory());
    }
}
