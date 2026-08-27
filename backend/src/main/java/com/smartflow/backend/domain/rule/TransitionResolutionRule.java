package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.enums.Priority;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * §6.5 - "Conditions simples basées sur la catégorie, la priorité, le service ou une
 * valeur du formulaire" : quand plusieurs Transition partagent le même (fromStep, action),
 * décide laquelle s'applique réellement. Pure - pas de Spring, pas de base :
 * application/service résout la liste de candidats et le contexte depuis le graphe
 * persisté avant d'appeler resolve().
 *
 * Un candidat dont chaque condition non nulle correspond l'emporte sur le candidat par
 * défaut (toutes conditions nulles) - ce défaut ne s'applique que si aucun candidat
 * conditionné ne correspond. Une égalité entre plusieurs candidats conditionnés
 * correspondant tous les deux se résout par transitionId croissant : un choix
 * déterministe, pas un ordre de préséance revendiqué - un workflow avec des conditions qui
 * se chevauchent sur la même action est une configuration qu'un administrateur fonctionnel
 * doit éviter.
 */
public class TransitionResolutionRule {

    public Optional<CandidateTransition> resolve(List<CandidateTransition> candidates, ResolutionContext context) {
        return candidates.stream()
                .filter(candidate -> candidate.hasAnyCondition() && matches(candidate, context))
                .min(Comparator.comparing(CandidateTransition::transitionId))
                .or(() -> candidates.stream().filter(candidate -> !candidate.hasAnyCondition()).findFirst());
    }

    private boolean matches(CandidateTransition candidate, ResolutionContext context) {
        return (candidate.conditionPriority() == null || candidate.conditionPriority() == context.requestPriority())
                && (candidate.conditionDepartmentId() == null
                        || candidate.conditionDepartmentId().equals(context.requestDepartmentId()))
                && (candidate.conditionFieldCode() == null
                        || Objects.equals(context.fieldValues().get(candidate.conditionFieldCode()), candidate.conditionFieldValue()));
    }

    /** toStepId is null exactly when the underlying Transition's action is CLOSE (terminal, ADR-03). */
    public record CandidateTransition(Long transitionId, Long toStepId, Priority conditionPriority,
                                       Long conditionDepartmentId, String conditionFieldCode, String conditionFieldValue) {

        boolean hasAnyCondition() {
            return conditionPriority != null || conditionDepartmentId != null || conditionFieldCode != null;
        }
    }

    public record ResolutionContext(Priority requestPriority, Long requestDepartmentId, Map<String, String> fieldValues) {
    }
}
