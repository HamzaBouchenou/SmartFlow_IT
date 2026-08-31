package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.AuditLog;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * §6.10/§13.1 - lecture du journal d'audit ("consultable... protégées contre la
 * modification depuis l'interface" - cette classe n'expose donc jamais d'écriture, seul
 * crosscutting/audit/AuditService écrit). Nommée distinctement de ce dernier (suffixe
 * QueryService plutôt que Service) pour ne pas laisser croire que la lecture et
 * l'écriture du journal partagent un seul point d'entrée : ce sont deux cas d'usage
 * différents, avec des droits différents (AuthorizationService.canViewAuditLog).
 */
@Service
public class AuditLogQueryService {

    private final AuditLogRepository auditLogRepository;
    private final AuthorizationService authorizationService;

    public AuditLogQueryService(AuditLogRepository auditLogRepository, AuthorizationService authorizationService) {
        this.auditLogRepository = auditLogRepository;
        this.authorizationService = authorizationService;
    }

    /**
     * §11.1 - "404 et non 403 pour une ressource hors périmètre" : appliqué ici comme
     * partout ailleurs (voir AuthorizationService.canViewAuditLog's own javadoc et
     * DashboardService.getServiceDashboard pour le même choix sur un autre écran
     * d'administration) plutôt qu'un 403 qui confirmerait l'existence de l'écran à un
     * utilisateur non habilité.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> search(User actingUser, AuditLogFilter filter, Pageable pageable) {
        if (!authorizationService.canViewAuditLog(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
        return auditLogRepository.findAll(compose(filter), pageable);
    }

    private Specification<AuditLog> compose(AuditLogFilter filter) {
        List<Specification<AuditLog>> specs = new ArrayList<>();
        if (filter.actorId() != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("actor").get("id"), filter.actorId()));
        }
        if (filter.action() != null && !filter.action().isBlank()) {
            specs.add((root, query, cb) -> cb.equal(root.get("action"), filter.action()));
        }
        if (filter.objectType() != null && !filter.objectType().isBlank()) {
            specs.add((root, query, cb) -> cb.equal(root.get("objectType"), filter.objectType()));
        }
        if (filter.occurredFrom() != null) {
            specs.add((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), filter.occurredFrom()));
        }
        if (filter.occurredTo() != null) {
            specs.add((root, query, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), filter.occurredTo()));
        }
        return Specification.allOf(specs);
    }
}
