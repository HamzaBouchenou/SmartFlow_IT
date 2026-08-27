package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.Step;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StepRepository extends JpaRepository<Step, Long> {

    List<Step> findByWorkflowDefinitionIdOrderByDisplayOrderAsc(Long workflowDefinitionId);
}
