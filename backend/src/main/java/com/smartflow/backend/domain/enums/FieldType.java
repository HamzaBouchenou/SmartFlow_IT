package com.smartflow.backend.domain.enums;

/**
 * Field kinds available when configuring a form (§6.3 - Formulaires configurables) :
 * "champs texte, nombre, date, liste, case à cocher, utilisateur, service et fichier".
 */
public enum FieldType {
    TEXT,
    NUMBER,
    DATE,
    LIST,
    CHECKBOX,
    USER,
    DEPARTMENT,
    FILE
}
