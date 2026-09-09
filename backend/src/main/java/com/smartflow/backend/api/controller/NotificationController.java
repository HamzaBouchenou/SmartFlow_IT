package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.NotificationResponse;
import com.smartflow.backend.api.dto.response.PageResponse;
import com.smartflow.backend.api.mapper.NotificationMapper;
import com.smartflow.backend.application.service.NotificationService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** §6.8 - "Centre de notifications... avec statut lu/non lu". */
@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** §6.8 - `unread=true` restreint à l'onglet "Non lues" ; sans paramètre, tout l'historique. */
    @GetMapping
    public PageResponse<NotificationResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                     @RequestParam(defaultValue = "false") boolean unread,
                                                     @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return PageResponse.from(notificationService.list(principal.getUser(), pageable, unread)
                .map(NotificationMapper::toResponse));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        return Map.of("count", notificationService.unreadCount(principal.getUser()));
    }

    /** §6.8 - "tout marquer comme lu" : le nombre de lignes effectivement marquées. */
    @PostMapping("/read-all")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal SmartFlowUserDetails principal) {
        return Map.of("marked", notificationService.markAllRead(principal.getUser()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        notificationService.markRead(principal.getUser(), id);
    }
}
