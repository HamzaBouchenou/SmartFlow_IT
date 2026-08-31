package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.Department;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.DepartmentRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * §6.2/§6.10 - "Gestion... du catalogue" : ServiceCatalog et RequestType eux-mêmes, en
 * écriture (CatalogService reste le seul lecteur public, lecture seule - son propre
 * javadoc annonçait déjà cette séparation). Ni l'un ni l'autre n'est versionné
 * (ADR-17, docs/DECISIONS.md - "catalogue et types de demande restent hors de ce
 * mécanisme") : même patron CRUD + activation/désactivation logique (RG-02/RG-12) que
 * OrganizationAdminService pour Department/Team.
 */
@Service
public class CatalogAdminService {

    private final ServiceCatalogRepository serviceCatalogRepository;
    private final RequestTypeRepository requestTypeRepository;
    private final DepartmentRepository departmentRepository;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;

    public CatalogAdminService(ServiceCatalogRepository serviceCatalogRepository, RequestTypeRepository requestTypeRepository,
                                DepartmentRepository departmentRepository, AuthorizationService authorizationService,
                                AuditService auditService) {
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.requestTypeRepository = requestTypeRepository;
        this.departmentRepository = departmentRepository;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
    }

    // --- ServiceCatalog ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ServiceCatalog> listServices(User actingUser) {
        requireFunctionalAdmin(actingUser);
        return serviceCatalogRepository.findAll();
    }

    @Transactional
    public ServiceCatalog createService(User actingUser, String name, String description, String category,
                                         Long departmentId, int displayOrder) {
        requireFunctionalAdmin(actingUser);
        ServiceCatalog service = new ServiceCatalog(name, getDepartment(departmentId));
        service.setDescription(description);
        service.setCategory(category);
        service.setDisplayOrder(displayOrder);
        service = serviceCatalogRepository.save(service);
        auditService.record(actingUser, "CREATE", "ServiceCatalog", service.getId().toString(), "name=" + name);
        return service;
    }

    @Transactional
    public ServiceCatalog updateService(User actingUser, Long serviceId, String name, String description, String category,
                                         Long departmentId, int displayOrder) {
        requireFunctionalAdmin(actingUser);
        ServiceCatalog service = getService(serviceId);
        service.setName(name);
        service.setDescription(description);
        service.setCategory(category);
        service.setDepartment(getDepartment(departmentId));
        service.setDisplayOrder(displayOrder);
        service = serviceCatalogRepository.save(service);
        auditService.record(actingUser, "UPDATE", "ServiceCatalog", service.getId().toString(), "name=" + name);
        return service;
    }

    @Transactional
    public ServiceCatalog setServiceActive(User actingUser, Long serviceId, boolean active) {
        requireFunctionalAdmin(actingUser);
        ServiceCatalog service = getService(serviceId);
        service.setActive(active);
        service = serviceCatalogRepository.save(service);
        auditService.record(actingUser, active ? "ACTIVATE" : "DEACTIVATE", "ServiceCatalog", service.getId().toString(), null);
        return service;
    }

    // --- RequestType -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<RequestType> listRequestTypes(User actingUser, Long serviceCatalogId) {
        requireFunctionalAdmin(actingUser);
        getService(serviceCatalogId);
        return requestTypeRepository.findByServiceCatalogIdOrderByDisplayOrderAsc(serviceCatalogId);
    }

    @Transactional
    public RequestType createRequestType(User actingUser, Long serviceCatalogId, String name, String description,
                                          String targetDelayDescription, String requiredDocuments, String contactInfo,
                                          boolean reopenAllowed, int displayOrder) {
        requireFunctionalAdmin(actingUser);
        RequestType requestType = new RequestType(getService(serviceCatalogId), name);
        applyFields(requestType, description, targetDelayDescription, requiredDocuments, contactInfo, reopenAllowed, displayOrder);
        requestType = requestTypeRepository.save(requestType);
        auditService.record(actingUser, "CREATE", "RequestType", requestType.getId().toString(), "name=" + name);
        return requestType;
    }

    @Transactional
    public RequestType updateRequestType(User actingUser, Long requestTypeId, String name, String description,
                                          String targetDelayDescription, String requiredDocuments, String contactInfo,
                                          boolean reopenAllowed, int displayOrder) {
        requireFunctionalAdmin(actingUser);
        RequestType requestType = getRequestType(requestTypeId);
        requestType.setName(name);
        applyFields(requestType, description, targetDelayDescription, requiredDocuments, contactInfo, reopenAllowed, displayOrder);
        requestType = requestTypeRepository.save(requestType);
        auditService.record(actingUser, "UPDATE", "RequestType", requestType.getId().toString(), "name=" + name);
        return requestType;
    }

    @Transactional
    public RequestType setRequestTypeActive(User actingUser, Long requestTypeId, boolean active) {
        requireFunctionalAdmin(actingUser);
        RequestType requestType = getRequestType(requestTypeId);
        requestType.setActive(active);
        requestType = requestTypeRepository.save(requestType);
        auditService.record(actingUser, active ? "ACTIVATE" : "DEACTIVATE", "RequestType", requestType.getId().toString(), null);
        return requestType;
    }

    private void applyFields(RequestType requestType, String description, String targetDelayDescription,
                              String requiredDocuments, String contactInfo, boolean reopenAllowed, int displayOrder) {
        requestType.setDescription(description);
        requestType.setTargetDelayDescription(targetDelayDescription);
        requestType.setRequiredDocuments(requiredDocuments);
        requestType.setContactInfo(contactInfo);
        requestType.setReopenAllowed(reopenAllowed);
        requestType.setDisplayOrder(displayOrder);
    }

    RequestType getRequestType(Long requestTypeId) {
        return requestTypeRepository.findById(requestTypeId)
                .orElseThrow(() -> new EntityNotFoundException("Type de demande introuvable."));
    }

    private ServiceCatalog getService(Long serviceId) {
        return serviceCatalogRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service introuvable."));
    }

    private Department getDepartment(Long departmentId) {
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new EntityNotFoundException("Direction/service introuvable."));
    }

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }
}
