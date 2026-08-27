package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.RequestHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestHistoryRepository extends JpaRepository<RequestHistory, Long> {

    // §6.4 - "Affichage d'une frise d'avancement et de l'historique complet" ; §3.4 - 100%
    // des changements d'état tracés, dans l'ordre où ils se sont produits.
    List<RequestHistory> findByRequestIdOrderByOccurredAtAsc(Long requestId);
}
