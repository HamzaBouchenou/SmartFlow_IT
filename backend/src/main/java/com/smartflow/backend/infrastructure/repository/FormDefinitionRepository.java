package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.enums.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {

    // §11.2 - GET /request-types/{id}/form : "récupérer la définition du formulaire actif".
    Optional<FormDefinition> findByRequestTypeIdAndStatus(Long requestTypeId, PublicationStatus status);
}
