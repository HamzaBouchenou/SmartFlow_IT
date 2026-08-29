package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.Comment;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.CommentRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * §6.4 - "Ajout de commentaires... autorisés" sur une demande. Reading follows the same
 * access surface as the request detail screen (RequestService.getViewableRequest, ADR-10,
 * §9.4 - comments share the detail screen with the rest of the dossier); writing follows
 * ADR-11's canAnnotate, strictly narrower (excludes AUDITOR - §5, "lecture seule").
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final RequestRepository requestRepository;
    private final RequestService requestService;
    private final AuthorizationService authorizationService;

    public CommentService(CommentRepository commentRepository, RequestRepository requestRepository,
                           RequestService requestService, AuthorizationService authorizationService) {
        this.commentRepository = commentRepository;
        this.requestRepository = requestRepository;
        this.requestService = requestService;
        this.authorizationService = authorizationService;
    }

    /**
     * ADR-11 : un canAnnotate refusé se traduit en 404, exactement comme un canAct refusé
     * (AuthorizationService's own javadoc) - jamais un 403 qui confirmerait à un appelant
     * hors périmètre, ou à un AUDITOR en lecture seule, que la demande existe et dans quel
     * état elle se trouve.
     */
    @Transactional
    public Comment addComment(User actingUser, Long requestId, String body) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable."));
        if (!authorizationService.canAnnotate(actingUser, request)) {
            throw new EntityNotFoundException("Demande introuvable.");
        }
        return commentRepository.save(new Comment(request, actingUser, body));
    }

    @Transactional(readOnly = true)
    public List<Comment> listComments(User actingUser, Long requestId) {
        Request request = requestService.getViewableRequest(actingUser, requestId);
        return commentRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());
    }
}
