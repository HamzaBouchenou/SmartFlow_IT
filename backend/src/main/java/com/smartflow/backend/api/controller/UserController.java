package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.CreateUserRequest;
import com.smartflow.backend.api.dto.request.ResetPasswordRequest;
import com.smartflow.backend.api.dto.request.UpdateUserRequest;
import com.smartflow.backend.api.dto.response.UserResponse;
import com.smartflow.backend.api.mapper.UserMapper;
import com.smartflow.backend.application.service.UserAdminService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
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
import java.util.List;

/** §6.1/§6.10 - "Gestion des utilisateurs." RG-02 : jamais de suppression physique,
 * seulement activate/deactivate. Le mot de passe ne transite jamais par UserResponse. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserController {

    private final UserAdminService userAdminService;
    private final Clock clock;

    public UserController(UserAdminService userAdminService, Clock clock) {
        this.userAdminService = userAdminService;
        this.clock = clock;
    }

    @GetMapping
    public List<UserResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        var now = clock.instant();
        return userAdminService.listUsers(principal.getUser()).stream()
                .map(user -> UserMapper.toResponse(user, now))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@AuthenticationPrincipal SmartFlowUserDetails principal, @Valid @RequestBody CreateUserRequest body) {
        var created = userAdminService.createUser(principal.getUser(), body.firstName(), body.lastName(), body.email(),
                body.password(), body.departmentId(), body.managerId());
        return UserMapper.toResponse(created, clock.instant());
    }

    @PutMapping("/{id}")
    public UserResponse update(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                @Valid @RequestBody UpdateUserRequest body) {
        var updated = userAdminService.updateUser(principal.getUser(), id, body.firstName(), body.lastName(), body.email(),
                body.departmentId(), body.managerId());
        return UserMapper.toResponse(updated, clock.instant());
    }

    @PostMapping("/{id}/activate")
    public UserResponse activate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return UserMapper.toResponse(userAdminService.setUserActive(principal.getUser(), id, true), clock.instant());
    }

    @PostMapping("/{id}/deactivate")
    public UserResponse deactivate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return UserMapper.toResponse(userAdminService.setUserActive(principal.getUser(), id, false), clock.instant());
    }

    @PostMapping("/{id}/reset-password")
    public UserResponse resetPassword(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                       @Valid @RequestBody ResetPasswordRequest body) {
        userAdminService.resetPassword(principal.getUser(), id, body.newPassword());
        return UserMapper.toResponse(userAdminService.getUser(id), clock.instant());
    }

    /** ADR-13 - débloque un compte verrouillé par LoginAttemptListener avant l'expiration naturelle du verrou. */
    @PostMapping("/{id}/unlock")
    public UserResponse unlock(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return UserMapper.toResponse(userAdminService.unlockAccount(principal.getUser(), id), clock.instant());
    }
}
