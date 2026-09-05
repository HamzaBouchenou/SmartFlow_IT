package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Ne touche jamais `active` : voir CatalogAdminService.activate/deactivateRequestType.
 *
 * reopenAllowed/displayOrder sont tous deux `Boolean`/`Integer`, jamais `boolean`/`int` :
 * même piège Jackson que UpsertStepRequest/UpsertFormFieldRequest (CLAUDE.md). displayOrder
 * reste `@NotNull` ; reopenAllowed a un défaut sensé (absent = autorisé, RG-08's own
 * default) via isReopenAllowed().
 */
public record UpsertRequestTypeRequest(
        @NotNull Long serviceCatalogId,
        @NotBlank String name,
        String description,
        String targetDelayDescription,
        String requiredDocuments,
        String contactInfo,
        Boolean reopenAllowed,
        @NotNull Integer displayOrder) {

    /** Absent dans le JSON signifie "autorisé" (RG-08 - le défaut du type §8__reopen_window.sql). */
    public boolean isReopenAllowed() {
        return !Boolean.FALSE.equals(reopenAllowed);
    }
}
