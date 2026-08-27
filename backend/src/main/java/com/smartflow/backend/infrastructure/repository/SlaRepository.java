package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Sla;
import com.smartflow.backend.domain.enums.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaRepository extends JpaRepository<Sla, Long> {

    // §6.7 - délais de prise en charge/résolution par type de demande et priorité.
    Optional<Sla> findByRequestTypeIdAndPriority(Long requestTypeId, Priority priority);
}
