package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.RequestFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RequestFieldValueRepository extends JpaRepository<RequestFieldValue, Long> {

    // §6.3 - "Conservation des valeurs saisies lorsque la demande est sauvegardée en
    // brouillon" : relire toutes les valeurs déjà saisies pour ré-afficher ou revalider un brouillon.
    List<RequestFieldValue> findByRequestId(Long requestId);

    Optional<RequestFieldValue> findByRequestIdAndFormFieldId(Long requestId, Long formFieldId);
}
