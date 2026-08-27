package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.RequestTypeResponse;
import com.smartflow.backend.api.dto.response.ServiceCatalogResponse;
import com.smartflow.backend.api.mapper.RequestTypeMapper;
import com.smartflow.backend.api.mapper.ServiceCatalogMapper;
import com.smartflow.backend.application.service.CatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * §6.2 - Catalogue de services. Pas de pagination ici (CLAUDE.md - "pagination et tri
 * normalisés" pour les listes) : le catalogue est un référentiel borné configuré par
 * l'administration fonctionnelle, pas une liste qui grandit avec l'activité (contrairement
 * aux futures listes de demandes / files de travail, §6.6, §6.9) - c'est aussi pourquoi
 * ServiceCatalogRepository/RequestTypeRepository renvoient des List, jamais des Page.
 */
@RestController
@RequestMapping("/api/v1/services")
public class ServiceCatalogController {

    private final CatalogService catalogService;

    public ServiceCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** §6.2 - "Recherche par mot-clé, catégorie ou service responsable", filtres optionnels et combinables. */
    @GetMapping
    public List<ServiceCatalogResponse> list(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) String category,
                                                         @RequestParam(required = false) Long departmentId) {
        return catalogService.searchServices(q, category, departmentId).stream()
                .map(ServiceCatalogMapper::toResponse)
                .toList();
    }

    /** §6.2 - types de demande actifs d'un service, dans l'ordre d'affichage configuré. */
    @GetMapping("/{serviceCatalogId}/request-types")
    public List<RequestTypeResponse> listRequestTypes(@PathVariable Long serviceCatalogId) {
        return catalogService.listRequestTypes(serviceCatalogId).stream()
                .map(RequestTypeMapper::toResponse)
                .toList();
    }
}
