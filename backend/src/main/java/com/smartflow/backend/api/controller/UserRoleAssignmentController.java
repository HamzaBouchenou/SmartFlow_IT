package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.CreateRoleAssignmentRequest;
import com.smartflow.backend.api.dto.response.UserRoleAssignmentResponse;
import com.smartflow.backend.api.mapper.UserRoleAssignmentMapper;
import com.smartflow.backend.application.service.UserRoleAssignmentAdminService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.UserRoleAssignment;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** §5.1/§6.10 - affectations de rôle d'un utilisateur. Révocation réelle (pas de drapeau
 * `active` sur UserRoleAssignment) - voir UserRoleAssignmentAdminService's own javadoc. */
@RestController
@RequestMapping("/api/v1/admin/users/{userId}/role-assignments")
public class UserRoleAssignmentController {

    private final UserRoleAssignmentAdminService userRoleAssignmentAdminService;

    public UserRoleAssignmentController(UserRoleAssignmentAdminService userRoleAssignmentAdminService) {
        this.userRoleAssignmentAdminService = userRoleAssignmentAdminService;
    }

    @GetMapping
    public List<UserRoleAssignmentResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long userId) {
        return userRoleAssignmentAdminService.list(principal.getUser(), userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserRoleAssignmentResponse grant(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long userId,
                                             @Valid @RequestBody CreateRoleAssignmentRequest body) {
        var created = userRoleAssignmentAdminService.grant(principal.getUser(), userId, body.role(), body.scopeType(), body.scopeId());
        return toResponse(created);
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long userId,
                        @PathVariable Long assignmentId) {
        userRoleAssignmentAdminService.revoke(principal.getUser(), userId, assignmentId);
    }

    private UserRoleAssignmentResponse toResponse(UserRoleAssignment assignment) {
        String scopeName = userRoleAssignmentAdminService.resolveScopeName(assignment.getScopeType(), assignment.getScopeId());
        return UserRoleAssignmentMapper.toResponse(assignment, scopeName);
    }
}
