package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.CreateCommentRequest;
import com.smartflow.backend.api.dto.response.CommentResponse;
import com.smartflow.backend.api.mapper.CommentMapper;
import com.smartflow.backend.application.service.CommentService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** §6.4/§11.2 - POST /api/v1/requests/{id}/comments ; §9.4 les affiche dans l'écran détail. */
@RestController
@RequestMapping("/api/v1/requests/{requestId}/comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse create(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long requestId,
                                   @Valid @RequestBody CreateCommentRequest body) {
        return CommentMapper.toResponse(commentService.addComment(principal.getUser(), requestId, body.body()));
    }

    @GetMapping
    public List<CommentResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long requestId) {
        return commentService.listComments(principal.getUser(), requestId).stream().map(CommentMapper::toResponse).toList();
    }
}
