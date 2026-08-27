package com.smartflow.backend.api.dto.response;

import java.util.List;

/**
 * §6.1 login response. Never the User entity itself (CLAUDE.md - "Les entités ne sont
 * jamais exposées comme DTO d'API") - notably omits passwordHash. roles is deliberately
 * just the role names, not their scope: the frontend uses it to decide what to *show*, the
 * server alone decides what a call may *do*, through canAct.
 */
public record LoginResponse(Long id, String firstName, String lastName, String email, List<String> roles) {
}
