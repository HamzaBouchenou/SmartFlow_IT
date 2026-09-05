package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

/** §6.10 - nouvelle valeur d'un paramètre administrable. Le format attendu (entier,
 * booléen, CSV) dépend de la clé ; validé par SystemParameterAdminService, pas ici -
 * @NotBlank est la seule contrainte commune à toutes les clés. */
public record UpdateSystemParameterRequest(@NotBlank String value) {
}
