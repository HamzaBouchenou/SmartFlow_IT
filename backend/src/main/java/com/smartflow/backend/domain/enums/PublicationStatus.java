package com.smartflow.backend.domain.enums;

/**
 * Versioning status of a FormDefinition or WorkflowDefinition (§10.1 - "Versionnement des
 * définitions de formulaire et de workflow"). Once PUBLISHED, a version is immutable;
 * corrections go through a new version, never an edit in place.
 */
public enum PublicationStatus {
    DRAFT,
    PUBLISHED,
    ARCHIVED
}
