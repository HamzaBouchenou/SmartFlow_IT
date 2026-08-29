package com.smartflow.backend.crosscutting.audit;

import com.smartflow.backend.crosscutting.logging.TraceIdFilter;
import com.smartflow.backend.domain.entity.AuditLog;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.infrastructure.repository.AuditLogRepository;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RG-11 - "Les changements de rôle, de statut, d'affectation et de configuration sont
 * inscrits dans le journal d'audit." §13.1 fixes the minimum content : "l'utilisateur, la
 * date, l'action, le type d'objet, l'identifiant concerné, le résultat et un résumé des
 * changements." The single write path every use case that produces one of RG-11's four
 * change categories calls into (application/service/RequestService.submit/cancel,
 * WorkflowTransitionService.execute) - CLAUDE.md's "intercepteur d'audit" for the
 * `audit_log` table (§9.3's crosscutting layer) nobody wrote to until now.
 *
 * Records only recorded changes (result="SUCCESS") : a canAct/canAnnotate refusal never
 * reaches these call sites (it throws before any state mutation, inside the same
 * transaction that would have written this row, so it rolls back together) - RG-11 asks for
 * "changements", not every denied attempt, and CLAUDE.md's rule number one is to not go
 * beyond what is anchored.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(User actor, String action, String objectType, String objectId, String summary) {
        AuditLog entry = new AuditLog(actor, action, objectType, objectId, "SUCCESS");
        entry.setSummary(summary);
        // §8 - le traceId de la requête HTTP courante (crosscutting/logging/TraceIdFilter),
        // pour recouper une ligne d'audit avec les logs applicatifs du même appel.
        entry.setTraceId(MDC.get(TraceIdFilter.MDC_KEY));
        auditLogRepository.save(entry);
    }
}
