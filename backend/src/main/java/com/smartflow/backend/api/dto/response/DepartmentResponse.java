package com.smartflow.backend.api.dto.response;

/** §4.1/§6.10 - une direction (parentId `null`) ou un service en dessous d'elle. */
public record DepartmentResponse(
        Long id,
        String name,
        Long parentId,
        String parentName,
        Long leadId,
        String leadName,
        boolean active) {
}
