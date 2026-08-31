package com.smartflow.backend.api.dto.response;

/** §4.1/§6.10/§6.6 - une équipe, cible d'affectation (§6.6) et niveau EQUIPE du périmètre (§5.1). */
public record TeamResponse(
        Long id,
        String name,
        Long departmentId,
        String departmentName,
        Long leadId,
        String leadName,
        boolean active) {
}
