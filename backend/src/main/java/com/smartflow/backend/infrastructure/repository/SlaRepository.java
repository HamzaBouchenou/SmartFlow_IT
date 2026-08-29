package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Sla;
import com.smartflow.backend.domain.enums.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SlaRepository extends JpaRepository<Sla, Long> {

    // §6.7 - délais de prise en charge/résolution par type de demande et priorité.
    Optional<Sla> findByRequestTypeIdAndPriority(Long requestTypeId, Priority priority);

    // §6.10 - écran d'administration : les cibles SLA (jusqu'à 4, une par Priority) d'un type de demande.
    List<Sla> findByRequestTypeId(Long requestTypeId);
}
