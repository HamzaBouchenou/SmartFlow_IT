package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.FieldOption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FieldOptionRepository extends JpaRepository<FieldOption, Long> {

    // §6.3 - toutes les options de tous les champs LIST d'un formulaire, en une seule
    // requête plutôt qu'une par champ (CatalogService les regroupe ensuite par formFieldId).
    List<FieldOption> findByFormFieldIdInOrderByFormFieldIdAscDisplayOrderAsc(List<Long> formFieldIds);
}
