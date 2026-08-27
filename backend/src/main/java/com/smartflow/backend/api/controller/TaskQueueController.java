package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.PageResponse;
import com.smartflow.backend.api.dto.response.RequestSummaryResponse;
import com.smartflow.backend.api.mapper.RequestMapper;
import com.smartflow.backend.application.service.TaskQueueFilter;
import com.smartflow.backend.application.service.TaskQueueService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.enums.RequestStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * §6.6 - "File personnelle « Mes tâches » et file d'équipe", avec les filtres qu'elle
 * décrit (statut, priorité, demandeur, catégorie, retard, date de soumission). Contrairement
 * au catalogue (§6.2, ServiceCatalogController), ces listes grandissent avec l'activité :
 * paginées et triées normalement (CLAUDE.md, §11.1), d'où PageResponse plutôt qu'un simple
 * tableau JSON.
 */
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskQueueController {

    private final TaskQueueService taskQueueService;

    public TaskQueueController(TaskQueueService taskQueueService) {
        this.taskQueueService = taskQueueService;
    }

    @GetMapping("/mine")
    public PageResponse<RequestSummaryResponse> mine(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                       @ModelAttribute TaskQueueFilterParams filterParams,
                                                       @PageableDefault(size = 20, sort = "submittedAt") Pageable pageable) {
        var page = taskQueueService.myTasks(principal.getUser(), filterParams.toFilter(), pageable);
        return PageResponse.from(page.map(RequestMapper::toSummary));
    }

    @GetMapping("/team")
    public PageResponse<RequestSummaryResponse> team(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                       @ModelAttribute TaskQueueFilterParams filterParams,
                                                       @PageableDefault(size = 20, sort = "submittedAt") Pageable pageable) {
        var page = taskQueueService.teamTasks(principal.getUser(), filterParams.toFilter(), pageable);
        return PageResponse.from(page.map(RequestMapper::toSummary));
    }

    /**
     * §6.6 - un objet plutôt que sept @RequestParam répétés sur chaque méthode ; Spring lie
     * chaque composant du record à son paramètre de requête homonyme (liaison par
     * constructeur, absent -> null, jamais une erreur de validation).
     */
    public record TaskQueueFilterParams(RequestStatus status, Priority priority, Long requesterId, String category,
                                         Boolean overdue, Instant submittedFrom, Instant submittedTo) {

        TaskQueueFilter toFilter() {
            return new TaskQueueFilter(status, priority, requesterId, category, overdue, submittedFrom, submittedTo);
        }
    }
}
