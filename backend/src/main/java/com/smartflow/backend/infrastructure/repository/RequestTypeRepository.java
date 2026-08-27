package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.RequestType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestTypeRepository extends JpaRepository<RequestType, Long> {

    // §6.2 - "Activation... et ordre d'affichage des types de demande" pour un service donné.
    List<RequestType> findByServiceCatalogIdAndActiveTrueOrderByDisplayOrderAsc(Long serviceCatalogId);
}
