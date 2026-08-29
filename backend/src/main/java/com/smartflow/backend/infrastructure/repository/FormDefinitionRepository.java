package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.enums.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {

    // §11.2 - GET /request-types/{id}/form : "récupérer la définition du formulaire actif".
    Optional<FormDefinition> findByRequestTypeIdAndStatus(Long requestTypeId, PublicationStatus status);

    // §6.10/ADR-17 - toutes les versions (DRAFT/PUBLISHED/ARCHIVED) d'un type de demande,
    // pour l'écran d'administration ; la plus récente d'abord.
    List<FormDefinition> findByRequestTypeIdOrderByVersionDesc(Long requestTypeId);
}
