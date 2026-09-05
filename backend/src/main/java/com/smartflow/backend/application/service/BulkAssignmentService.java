package com.smartflow.backend.application.service;

import com.smartflow.backend.api.dto.response.BulkActionResultResponse;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.WorkflowAction;
import com.smartflow.backend.domain.exception.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * §6.6 - "Actions en masse limitées aux changements ne présentant pas de risque
 * fonctionnel." ASSIGN est la seule action que cette classe expose - jamais un paramètre
 * WorkflowAction générique - pour que "limité aux changements non risqués" tienne par
 * construction plutôt que par une liste blanche à vérifier à chaque appel. Chaque demande
 * passe par WorkflowTransitionService.execute individuellement - canAct, éligibilité,
 * historique RG-04, notification, audit RG-11 : rien de spécifique au "en masse" ne
 * contourne ces garanties, seule la boucle et l'agrégation des résultats sont propres à
 * cette classe. Un appel séparé par demande, chacun sa propre transaction Spring (pas de
 * @Transactional ici, volontairement : un échec sur un id ne doit jamais faire échouer ou
 * annuler les affectations déjà réussies des autres ids du même lot).
 */
@Service
public class BulkAssignmentService {

    private final WorkflowTransitionService workflowTransitionService;

    public BulkAssignmentService(WorkflowTransitionService workflowTransitionService) {
        this.workflowTransitionService = workflowTransitionService;
    }

    public BulkActionResultResponse bulkAssign(User actingUser, List<Long> requestIds, Long assignedUserId,
                                                Long assignedTeamId, boolean autoAssign) {
        List<BulkActionResultResponse.Item> results = requestIds.stream()
                .map(requestId -> assignOne(actingUser, requestId, assignedUserId, assignedTeamId, autoAssign))
                .toList();
        return new BulkActionResultResponse(results);
    }

    private BulkActionResultResponse.Item assignOne(User actingUser, Long requestId, Long assignedUserId,
                                                      Long assignedTeamId, boolean autoAssign) {
        try {
            workflowTransitionService.execute(actingUser, requestId, WorkflowAction.ASSIGN, null, null, null, null,
                    assignedUserId, assignedTeamId, autoAssign);
            return new BulkActionResultResponse.Item(requestId, true, null);
        } catch (BusinessException ex) {
            return new BulkActionResultResponse.Item(requestId, false, ex.getCode());
        }
    }
}
