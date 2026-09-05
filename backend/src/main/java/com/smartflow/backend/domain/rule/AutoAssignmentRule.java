package com.smartflow.backend.domain.rule;

import java.util.Comparator;
import java.util.List;

/**
 * §6.6 - "Affectation automatique selon le service, la catégorie ou une règle de
 * répartition simple." Le service (WorkflowTransitionService) résout déjà "selon le
 * service, la catégorie" en restreignant les candidats à l'équipe responsable de l'étape
 * courante (elle-même déterminée par service/catégorie via le workflow configuré, §6.5) -
 * la "règle de répartition simple" qui reste à trancher ici, une fois les candidats connus,
 * est laquelle de ces personnes reçoit la demande. Choix : équilibrage de charge (le moins
 * de demandes activement affectées l'emporte), départagé par id croissant pour rester
 * déterministe - jamais un tour de rôle à état mutable à faire persister quelque part, ce
 * qui introduirait un état à corrompre pour un gain que le cahier des charges ne demande
 * pas ("simple"). Pure : aucun import Spring, aucun accès base - la charge de chaque
 * candidat est déjà résolue par l'appelant.
 */
public class AutoAssignmentRule {

    public record Candidate(Long userId, long currentLoad) {
    }

    public Long pickLeastLoaded(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("Aucun candidat à départager.");
        }
        return candidates.stream()
                .min(Comparator.comparingLong(Candidate::currentLoad).thenComparing(Candidate::userId))
                .map(Candidate::userId)
                .orElseThrow();
    }
}
