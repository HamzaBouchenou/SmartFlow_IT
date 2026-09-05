package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.Sla;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.Priority;
import com.smartflow.backend.domain.exception.AdministrationValidationException;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.SlaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * §6.7/§6.10 - "Définition d'un délai de prise en charge et d'un délai de résolution par
 * type de demande et priorité." La contrainte d'unicité `uq_sla_type_priority` (V2) fait de
 * (requestTypeId, priority) la clé naturelle d'un upsert, exactement comme SystemParameter
 * le fait par clé (SlaRepository.findByRequestTypeIdAndPriority). useBusinessCalendar n'est
 * jamais posé depuis cet écran (ADR-09, docs/DECISIONS.md - "aucun code ne la lit").
 */
@Service
public class SlaAdminService {

    private final SlaRepository slaRepository;
    private final RequestTypeRepository requestTypeRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public SlaAdminService(SlaRepository slaRepository, RequestTypeRepository requestTypeRepository,
                            AuthorizationService authorizationService, AuditService auditService) {
        this.slaRepository = slaRepository;
        this.requestTypeRepository = requestTypeRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Sla> listForRequestType(User actingUser, Long requestTypeId) {
        requireFunctionalAdmin(actingUser);
        getRequestType(requestTypeId);
        return slaRepository.findByRequestTypeId(requestTypeId);
    }

    /** RG-11 - une cible SLA est une donnée de configuration, journalisée à chaque écriture. */
    @Transactional
    public Sla upsert(User actingUser, Long requestTypeId, Priority priority, int firstResponseMinutes, int resolutionMinutes) {
        requireFunctionalAdmin(actingUser);
        RequestType requestType = getRequestType(requestTypeId);
        if (resolutionMinutes < firstResponseMinutes) {
            throw new AdministrationValidationException("INVALID_SLA_TARGETS",
                    "Le délai de résolution ne peut pas être inférieur au délai de prise en charge.");
        }

        Sla sla = slaRepository.findByRequestTypeIdAndPriority(requestTypeId, priority)
                .orElseGet(() -> new Sla(requestType, priority, firstResponseMinutes, resolutionMinutes));
        sla.setFirstResponseMinutes(firstResponseMinutes);
        sla.setResolutionMinutes(resolutionMinutes);
        sla = slaRepository.save(sla);

        auditService.record(actingUser, "UPDATE_SLA", "Sla", requestTypeId + "/" + priority,
                "firstResponseMinutes=" + firstResponseMinutes + ", resolutionMinutes=" + resolutionMinutes);
        return sla;
    }

    private RequestType getRequestType(Long requestTypeId) {
        return requestTypeRepository.findById(requestTypeId)
                .orElseThrow(() -> new EntityNotFoundException("Type de demande introuvable."));
    }

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }
}
