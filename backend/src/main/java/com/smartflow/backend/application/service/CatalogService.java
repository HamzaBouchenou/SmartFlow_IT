package com.smartflow.backend.application.service;

import com.smartflow.backend.domain.entity.FieldOption;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.FormField;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.ServiceCatalog;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.FieldOptionRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.FormFieldRepository;
import com.smartflow.backend.infrastructure.repository.RequestTypeRepository;
import com.smartflow.backend.infrastructure.repository.ServiceCatalogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * §6.2 (catalogue de services) et §6.3 (formulaires configurables) - lecture seule : rien
 * ici ne crée, ne modifie ni ne publie une fiche de catalogue, un type de demande ou un
 * formulaire (cette administration est un cas d'usage séparé, non construit ici). Aucun
 * appel à AuthorizationService.canAct : ce n'est pas une décision sur une Request, c'est un
 * catalogue de référence lisible par tout utilisateur authentifié (SecurityConfig l'exige
 * déjà pour toute route hors login/csrf/health/docs) - le CDC ne définit pas de règle de
 * visibilité par rôle/service pour le catalogue lui-même (§6.2 en évoque une possibilité
 * ["selon le rôle, l'entité ou le groupe"] sans la spécifier : ambiguïté non tranchée,
 * volontairement non implémentée ici, cf. CLAUDE.md règle numéro un).
 *
 * Une fiche ou un type de demande désactivé (§6.2 - "Activation, désactivation") se
 * comporte comme absent pour la consultation directe par id : aucun mécanisme de
 * contournement admin n'existe encore, donc "désactivé" et "inexistant" ne sont pas
 * distingués côté API, exactement comme CLAUDE.md l'exige déjà pour un périmètre non
 * couvert (404, jamais un état spécial exposé).
 */
@Service
public class CatalogService {

    private final ServiceCatalogRepository serviceCatalogRepository;
    private final RequestTypeRepository requestTypeRepository;
    private final FormDefinitionRepository formDefinitionRepository;
    private final FormFieldRepository formFieldRepository;
    private final FieldOptionRepository fieldOptionRepository;

    public CatalogService(ServiceCatalogRepository serviceCatalogRepository, RequestTypeRepository requestTypeRepository,
                           FormDefinitionRepository formDefinitionRepository, FormFieldRepository formFieldRepository,
                           FieldOptionRepository fieldOptionRepository) {
        this.serviceCatalogRepository = serviceCatalogRepository;
        this.requestTypeRepository = requestTypeRepository;
        this.formDefinitionRepository = formDefinitionRepository;
        this.formFieldRepository = formFieldRepository;
        this.fieldOptionRepository = fieldOptionRepository;
    }

    @Transactional(readOnly = true)
    public List<ServiceCatalog> searchServices(String keyword, String category, Long departmentId) {
        return serviceCatalogRepository.search(blankToNull(keyword), blankToNull(category), departmentId);
    }

    @Transactional(readOnly = true)
    public List<RequestType> listRequestTypes(Long serviceCatalogId) {
        getActiveServiceCatalog(serviceCatalogId);
        return requestTypeRepository.findByServiceCatalogIdAndActiveTrueOrderByDisplayOrderAsc(serviceCatalogId);
    }

    @Transactional(readOnly = true)
    public RequestType getActiveRequestType(Long requestTypeId) {
        RequestType requestType = requestTypeRepository.findById(requestTypeId)
                .filter(RequestType::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Type de demande introuvable."));
        getActiveServiceCatalog(requestType.getServiceCatalog().getId());
        return requestType;
    }

    @Transactional(readOnly = true)
    public FormDefinitionView getPublishedForm(Long requestTypeId) {
        getActiveRequestType(requestTypeId);
        FormDefinition definition = formDefinitionRepository
                .findByRequestTypeIdAndStatus(requestTypeId, PublicationStatus.PUBLISHED)
                .orElseThrow(() -> new EntityNotFoundException("Aucun formulaire publié pour ce type de demande."));

        List<FormField> fields = formFieldRepository.findByFormDefinitionIdOrderByDisplayOrderAsc(definition.getId());
        Map<Long, List<FieldOption>> optionsByFieldId = fieldOptionRepository
                .findByFormFieldIdInOrderByFormFieldIdAscDisplayOrderAsc(fields.stream().map(FormField::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(option -> option.getFormField().getId()));

        List<FormFieldView> fieldViews = fields.stream()
                .map(field -> new FormFieldView(field, optionsByFieldId.getOrDefault(field.getId(), List.of())))
                .toList();
        return new FormDefinitionView(definition, fieldViews);
    }

    private ServiceCatalog getActiveServiceCatalog(Long serviceCatalogId) {
        return serviceCatalogRepository.findById(serviceCatalogId)
                .filter(ServiceCatalog::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Service introuvable."));
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    /** A published FormDefinition together with its fields, each carrying its own options - api/mapper shapes this into FormDefinitionResponse. */
    public record FormDefinitionView(FormDefinition definition, List<FormFieldView> fields) {
    }

    public record FormFieldView(FormField field, List<FieldOption> options) {
    }
}
