package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.AnalyzeRequest;
import com.smartflow.backend.api.dto.request.ValidateAiAnalysisRequest;
import com.smartflow.backend.api.dto.response.AiAnalysisResponse;
import com.smartflow.backend.api.mapper.AiAnalysisMapper;
import com.smartflow.backend.application.service.AiAnalysisService;
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

/** §11.2/§12 - module d'intelligence artificielle : une aide, jamais une écriture directe (RG-10). */
@RestController
@RequestMapping("/api/v1/ai")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    public AiAnalysisController(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @PostMapping("/requests/{id}/analyze")
    @ResponseStatus(HttpStatus.CREATED)
    public AiAnalysisResponse analyze(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                       @Valid @RequestBody AnalyzeRequest body) {
        return AiAnalysisMapper.toResponse(aiAnalysisService.analyze(principal.getUser(), id, body.analysisType()));
    }

    @GetMapping("/requests/{id}/analyses")
    public List<AiAnalysisResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return aiAnalysisService.list(principal.getUser(), id).stream().map(AiAnalysisMapper::toResponse).toList();
    }

    /** RG-10 - validation humaine d'une suggestion existante ; n'écrit jamais sur la demande elle-même. */
    @PostMapping("/analyses/{id}/validate")
    public AiAnalysisResponse validate(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                        @Valid @RequestBody ValidateAiAnalysisRequest body) {
        return AiAnalysisMapper.toResponse(aiAnalysisService.validate(principal.getUser(), id, body.acceptedValue()));
    }
}
