package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.AiAnalysis;
import com.smartflow.backend.domain.entity.Comment;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.AiAnalysisType;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import com.smartflow.backend.infrastructure.ai.AiClient;
import com.smartflow.backend.infrastructure.repository.AiAnalysisRepository;
import com.smartflow.backend.infrastructure.repository.CommentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADR-16 (docs/DECISIONS.md) - §12 : la seule classe qui parle à infrastructure/ai/AiClient
 * et écrit une AiAnalysis. RG-10 tient ici par construction : aucune méthode de cette
 * classe ne touche Request, RequestType ni RequestFieldValue - seulement AiAnalysis, une
 * entité entièrement séparée. Lecture (list) suit ADR-10's canView ; écriture (analyze,
 * validate) suit ADR-11's canAnnotate - "selon les droits" (§11.2) sans troisième matrice
 * de permissions.
 */
@Service
public class AiAnalysisService {

    /** §12.1 - "et de ses derniers échanges" : les N derniers commentaires du fil. */
    private static final int SUMMARY_RECENT_COMMENTS = 5;

    private final AiClient aiClient;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final RequestService requestService;
    private final CommentRepository commentRepository;
    private final AuthorizationService authorizationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AiAnalysisService(AiClient aiClient, AiAnalysisRepository aiAnalysisRepository, RequestService requestService,
                              CommentRepository commentRepository, AuthorizationService authorizationService,
                              ObjectMapper objectMapper, Clock clock) {
        this.aiClient = aiClient;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.requestService = requestService;
        this.commentRepository = commentRepository;
        this.authorizationService = authorizationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** ADR-16 : un canAnnotate refusé se traduit en 404 (voir CommentService pour le même raisonnement). */
    @Transactional
    public AiAnalysis analyze(User actingUser, Long requestId, AiAnalysisType analysisType) {
        Request request = requestService.getViewableRequest(actingUser, requestId);
        if (!authorizationService.canAnnotate(actingUser, request)) {
            throw new EntityNotFoundException("Demande introuvable.");
        }

        AiAnalysis analysis = switch (analysisType) {
            case CLASSIFICATION -> classify(request);
            case SUMMARY -> summarize(request);
            case DOCUMENT_SEARCH, REPLY_SUGGESTION -> throw new InvalidRequestStateException(
                    "AI_ANALYSIS_TYPE_NOT_SUPPORTED", "Cette fonction IA n'est pas encore disponible (§12.1, P2/Option).");
        };
        return aiAnalysisRepository.save(analysis);
    }

    /** RG-10 - ne pose que acceptedValue/validatedBy/validatedAt sur la ligne existante, jamais sur Request. */
    @Transactional
    public AiAnalysis validate(User actingUser, Long analysisId, String acceptedValue) {
        AiAnalysis analysis = aiAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new EntityNotFoundException("Analyse IA introuvable."));
        if (!authorizationService.canAnnotate(actingUser, analysis.getRequest())) {
            throw new EntityNotFoundException("Analyse IA introuvable.");
        }
        analysis.setAcceptedValue(acceptedValue);
        analysis.setValidatedBy(actingUser);
        analysis.setValidatedAt(clock.instant());
        return aiAnalysisRepository.save(analysis);
    }

    @Transactional(readOnly = true)
    public List<AiAnalysis> list(User actingUser, Long requestId) {
        Request request = requestService.getViewableRequest(actingUser, requestId);
        return aiAnalysisRepository.findByRequestIdOrderByCreatedAtDesc(request.getId());
    }

    private AiAnalysis classify(Request request) {
        AiClient.ClassificationResult result = aiClient.classify(request.getTitle(), request.getDescription());
        Map<String, Object> suggested = new LinkedHashMap<>();
        suggested.put("category", result.category());
        suggested.put("priority", result.priority());
        String suggestedValueJson = objectMapper.writeValueAsString(suggested);

        // §12.2/ADR-21/RG-10 - `rawResult` porte le détail par cible, `suggestedValue` reste
        // le strict couple {category, priority} que le formulaire de validation relit : ce
        // sont deux contrats distincts, et enrichir le premier ne doit pas déformer le second.
        //
        // Le détail est nécessaire depuis qu'un classifieur différent répond pour chaque
        // cible : la seule moyenne portée par `confidenceScore` peut cacher une suggestion
        // très sûre à côté d'une suggestion douteuse, et l'agent qui décide d'accepter ne
        // saurait pas laquelle des deux vérifier. La moyenne reste dans `confidenceScore`,
        // qui est une colonne unique et le demeure - c'est un résumé, pas la mesure.
        Map<String, Object> raw = new LinkedHashMap<>(suggested);
        raw.put("categoryConfidence", result.categoryConfidence());
        raw.put("priorityConfidence", result.priorityConfidence());
        raw.put("categoryMethod", result.categoryMethod());
        raw.put("priorityMethod", result.priorityMethod());

        AiAnalysis analysis = new AiAnalysis(request, AiAnalysisType.CLASSIFICATION, suggestedValueJson);
        analysis.setRawResult(objectMapper.writeValueAsString(raw));
        analysis.setConfidenceScore((result.categoryConfidence() + result.priorityConfidence()) / 2.0);
        return analysis;
    }

    private AiAnalysis summarize(Request request) {
        String input = buildSummaryInput(request);
        String summary = aiClient.summarize(input);

        AiAnalysis analysis = new AiAnalysis(request, AiAnalysisType.SUMMARY, summary);
        analysis.setRawResult(summary);
        return analysis;
    }

    private String buildSummaryInput(Request request) {
        StringBuilder input = new StringBuilder();
        if (request.getDescription() != null) {
            input.append(request.getDescription());
        }
        List<Comment> comments = commentRepository.findByRequestIdOrderByCreatedAtAsc(request.getId());
        int fromIndex = Math.max(0, comments.size() - SUMMARY_RECENT_COMMENTS);
        for (Comment comment : comments.subList(fromIndex, comments.size())) {
            input.append(' ').append(comment.getBody());
        }
        return input.toString();
    }
}
