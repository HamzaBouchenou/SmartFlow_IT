package com.smartflow.backend.infrastructure.repository;

import com.smartflow.backend.domain.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    // §13.1, §6.10 - "Journal d'audit consultable avec filtres par utilisateur, action,
    // objet et période" : filtres composés en application/service via Specification plutôt
    // que par une méthode dérivée par combinaison de champs.
}
