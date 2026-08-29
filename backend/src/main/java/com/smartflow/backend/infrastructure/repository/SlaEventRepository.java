package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.SlaEvent;
import com.smartflow.backend.domain.enums.SlaEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SlaEventRepository extends JpaRepository<SlaEvent, Long> {

    // §6.9 - "taux de respect des SLA" (DashboardService) : quelles demandes, parmi un lot
    // donné, ont au moins un événement du type demandé - une requête groupée plutôt qu'un
    // N+1 sur Request.getSlaEvents() par demande.
    List<SlaEvent> findByRequestIdInAndEventType(List<Long> requestIds, SlaEventType eventType);
}
