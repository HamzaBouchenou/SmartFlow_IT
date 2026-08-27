package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.WorkflowDefinition;
import com.smartflow.backend.domain.enums.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

    // RG-03 - le workflow figé à la soumission est le workflow publié au moment T.
    Optional<WorkflowDefinition> findByRequestTypeIdAndStatus(Long requestTypeId, PublicationStatus status);
}
