package com.smartflow.backend.api.dto.response;

import java.util.List;

/** §6.3 - la définition de formulaire PUBLISHED active d'un type de demande, champs triés par displayOrder. */
public record FormDefinitionResponse(Long id, Long requestTypeId, int version, List<FormFieldResponse> fields) {
}
