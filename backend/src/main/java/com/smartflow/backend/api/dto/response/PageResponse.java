package com.smartflow.backend.api.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * CLAUDE.md - "pagination et tri normalisés" (§11.1) : la forme commune de toute liste qui
 * grandit avec l'activité (contrairement au catalogue, §6.2, volontairement non paginé -
 * voir ServiceCatalogController). Première utilisatrice : les files de travail (§6.6).
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
