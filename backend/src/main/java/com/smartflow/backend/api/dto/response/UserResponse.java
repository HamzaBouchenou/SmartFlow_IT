package com.smartflow.backend.api.dto.response;

/** §6.1/§6.10 - un compte utilisateur, sans jamais son mot de passe (§13). `locked`
 * reflète ADR-13 (`lockedUntil` dans le futur), distinct d'`active` (RG-02 - désactivation
 * logique). */
public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        Long departmentId,
        String departmentName,
        Long managerId,
        String managerName,
        boolean active,
        boolean locked) {
}
