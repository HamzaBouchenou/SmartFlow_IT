package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Transition;
import com.smartflow.backend.domain.enums.WorkflowAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransitionRepository extends JpaRepository<Transition, Long> {

    // §6.5 - les transitions légales hors d'une étape ; canAct's "etapeAutoriseAction"
    // clause (application/security) en dépend.
    List<Transition> findByFromStepId(Long fromStepId);

    // §6.5 - candidats pour une action déjà confirmée légale par canAct : peut en renvoyer
    // plusieurs quand des conditions simples se partagent la même (fromStep, action) -
    // application/service/WorkflowTransitionService les passe à TransitionResolutionRule
    // pour choisir celle qui s'applique réellement.
    List<Transition> findByFromStepIdAndAction(Long fromStepId, WorkflowAction action);

    // §6.10/ADR-17 - toutes les transitions d'un WorkflowDefinition (une par Step de son
    // graphe), pour l'écran d'administration.
    List<Transition> findByFromStepIdIn(List<Long> fromStepIds);
}
