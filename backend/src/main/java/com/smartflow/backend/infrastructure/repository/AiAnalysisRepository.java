package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.AiAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    // §12.1/§9.4 - analyses IA d'une demande, les plus récentes d'abord.
    List<AiAnalysis> findByRequestIdOrderByCreatedAtDesc(Long requestId);
}
