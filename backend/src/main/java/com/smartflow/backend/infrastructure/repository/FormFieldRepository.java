package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.FormField;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FormFieldRepository extends JpaRepository<FormField, Long> {

    List<FormField> findByFormDefinitionIdOrderByDisplayOrderAsc(Long formDefinitionId);
}
