package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.RequestHistory;
import com.smartflow.backend.domain.enums.WorkflowAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequestHistoryRepository extends JpaRepository<RequestHistory, Long> {

    // §6.4 - "Affichage d'une frise d'avancement et de l'historique complet" ; §3.4 - 100%
    // des changements d'état tracés, dans l'ordre où ils se sont produits.
    List<RequestHistory> findByRequestIdOrderByOccurredAtAsc(Long requestId);

    // RG-08/ADR-14 - REOPEN reprend au fromStep de la dernière CLOSE de cette demande.
    Optional<RequestHistory> findFirstByRequestIdAndActionOrderByOccurredAtDesc(Long requestId, WorkflowAction action);

    // §6.9 - "taux de réouverture" : quelles demandes, parmi celles filtrées pour le tableau
    // de bord, ont au moins une ligne d'historique REOPEN (RG-08/ADR-14).
    List<RequestHistory> findByRequestIdInAndAction(List<Long> requestIds, WorkflowAction action);

    // §6.9/§9.4 - "vue demandeur" : "dernières décisions" sur les dossiers d'un demandeur.
    List<RequestHistory> findByRequestIdInAndActionIn(List<Long> requestIds, List<WorkflowAction> actions);
}
