package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    // §12.1 - recherche documentaire limitée aux documents actifs indexés.
    List<KnowledgeDocument> findByActiveTrue();

    List<KnowledgeDocument> findByServiceCatalogIdAndActiveTrue(Long serviceCatalogId);
}
