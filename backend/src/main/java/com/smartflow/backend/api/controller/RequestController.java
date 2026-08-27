package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.request.CreateRequestRequest;
import com.smartflow.backend.api.dto.request.ExecuteTransitionRequest;
import com.smartflow.backend.api.dto.request.UpdateRequestRequest;
import com.smartflow.backend.api.dto.response.RequestDetailResponse;
import com.smartflow.backend.api.mapper.RequestMapper;
import com.smartflow.backend.application.service.RequestService;
import com.smartflow.backend.application.service.WorkflowTransitionService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Request;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * §6.4 - Gestion des demandes : brouillon, modification, soumission, annulation avant
 * prise en charge. Chaque méthode ne fait qu'appeler RequestService puis relire l'état
 * courant via getDetail - la garde d'accès (RG-06, "son propre dossier") vit entièrement
 * dans le service, jamais ici (CLAUDE.md - pas de règle dupliquée au niveau contrôleur).
 */
@RestController
@RequestMapping("/api/v1/requests")
public class RequestController {

    private final RequestService requestService;
    private final WorkflowTransitionService workflowTransitionService;

    public RequestController(RequestService requestService, WorkflowTransitionService workflowTransitionService) {
        this.requestService = requestService;
        this.workflowTransitionService = workflowTransitionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RequestDetailResponse create(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                         @Valid @RequestBody CreateRequestRequest body) {
        Request created = requestService.createDraft(principal.getUser(), body.requestTypeId(), body.title(),
                body.description(), body.fieldValues());
        return detail(principal, created.getId());
    }

    @GetMapping("/{id}")
    public RequestDetailResponse get(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        return detail(principal, id);
    }

    @PutMapping("/{id}")
    public RequestDetailResponse update(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id,
                                         @Valid @RequestBody UpdateRequestRequest body) {
        requestService.updateDraft(principal.getUser(), id, body.title(), body.description(), body.fieldValues());
        return detail(principal, id);
    }

    @PostMapping("/{id}/submit")
    public RequestDetailResponse submit(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        requestService.submit(principal.getUser(), id);
        return detail(principal, id);
    }

    @PostMapping("/{id}/cancel")
    public RequestDetailResponse cancel(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long id) {
        requestService.cancel(principal.getUser(), id);
        return detail(principal, id);
    }

    /**
     * §6.5 - valider, rejeter, retourner, affecter, demander un complément ou clôturer. Ne
     * relit pas via getDetail (RG-06 "son propre dossier" ne s'applique pas ici : l'acteur
     * n'est typiquement pas le demandeur) - WorkflowTransitionService.execute a déjà
     * autorisé l'action via canAct, donc toDetailView peut construire la réponse sans
     * revérifier la propriété.
     */
    @PostMapping("/{id}/transitions")
    public RequestDetailResponse executeTransition(@AuthenticationPrincipal SmartFlowUserDetails principal,
                                                     @PathVariable Long id, @Valid @RequestBody ExecuteTransitionRequest body) {
        Request request = workflowTransitionService.execute(principal.getUser(), id, body.action(), body.comment(),
                body.closureReason(), body.closureSolution(), body.assignedUserId(), body.assignedTeamId());
        return RequestMapper.toResponse(requestService.toDetailView(principal.getUser(), request));
    }

    private RequestDetailResponse detail(SmartFlowUserDetails principal, Long id) {
        return RequestMapper.toResponse(requestService.getDetail(principal.getUser(), id));
    }
}
