package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.Comment;
import com.smartflow.backend.domain.entity.CommentMention;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.NotificationType;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.rule.MentionParsingRule;
import com.smartflow.backend.infrastructure.repository.CommentMentionRepository;
import com.smartflow.backend.infrastructure.repository.CommentRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * §6.4 - "Ajout de commentaires, mentions... autorisés" sur une demande. Reading follows the
 * same access surface as the request detail screen (RequestService.getViewableRequest,
 * ADR-10, §9.4 - comments share the detail screen with the rest of the dossier); writing
 * follows ADR-11's canAnnotate, strictly narrower (excludes AUDITOR - §5, "lecture seule").
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final RequestService requestService;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final MentionParsingRule mentionParsingRule;

    public CommentService(CommentRepository commentRepository, CommentMentionRepository commentMentionRepository,
                           RequestRepository requestRepository, UserRepository userRepository,
                           RequestService requestService, AuthorizationService authorizationService,
                           NotificationService notificationService, MentionParsingRule mentionParsingRule) {
        this.commentRepository = commentRepository;
        this.commentMentionRepository = commentMentionRepository;
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.requestService = requestService;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.mentionParsingRule = mentionParsingRule;
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
        Comment comment = commentRepository.save(new Comment(request, actingUser, body));
        recordMentions(actingUser, request, comment);
        return comment;
    }

    @Transactional(readOnly = true)
    public List<Comment> listComments(User actingUser, Long requestId) {
        Request request = requestService.getViewableRequest(actingUser, requestId);
        return commentRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());
    }

    /**
     * §6.4/§9.4 - les personnes retenues par {@link #recordMentions}, par identifiant de
     * commentaire, pour que l'écran affiche qui a réellement été mentionné. Lecture bornée
     * par l'appelant : cette méthode ne s'appelle qu'après listComments, donc après
     * getViewableRequest.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<User>> findMentionsByComment(List<Long> commentIds) {
        Map<Long, List<User>> byComment = new LinkedHashMap<>();
        if (commentIds.isEmpty()) {
            return byComment;
        }
        for (CommentMention mention : commentMentionRepository.findByCommentIdIn(commentIds)) {
            byComment.computeIfAbsent(mention.getComment().getId(), id -> new ArrayList<>())
                    .add(mention.getMentionedUser());
        }
        return byComment;
    }

    /**
     * ADR-24 (docs/DECISIONS.md) - le serveur relit le texte enregistré et résout lui-même
     * les adresses : le client ne fournit jamais la liste des destinataires, sans quoi il
     * déciderait qui reçoit le titre et la référence d'un dossier (§11.1, RG-06).
     *
     * Une adresse inconnue, un compte qui ne peut pas lire la demande (canView), ou l'auteur
     * se mentionnant lui-même sont ignorés **en silence** : le commentaire est accepté tel
     * quel. Répondre « cette personne n'a pas accès » serait un oracle d'appartenance, la
     * version "mention" du 403 que CLAUDE.md interdit déjà au profit du 404.
     */
    private void recordMentions(User actingUser, Request request, Comment comment) {
        for (String email : mentionParsingRule.parse(comment.getBody())) {
            userRepository.findByEmail(email)
                    .filter(mentioned -> !mentioned.getId().equals(actingUser.getId()))
                    .filter(User::isActive)
                    .filter(mentioned -> authorizationService.canView(mentioned, request))
                    .ifPresent(mentioned -> {
                        commentMentionRepository.save(new CommentMention(comment, mentioned));
                        notificationService.notify(mentioned, NotificationType.MENTION, request,
                                actingUser.getFirstName() + " " + actingUser.getLastName()
                                        + " vous a mentionné sur " + request.getReference(),
                                comment.getBody(),
                                Map.of("reference", request.getReference(), "title", request.getTitle(),
                                        "author", actingUser.getFirstName() + " " + actingUser.getLastName()));
                    });
        }
    }
}
