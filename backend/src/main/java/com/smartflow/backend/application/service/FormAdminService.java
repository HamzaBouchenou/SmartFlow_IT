package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.crosscutting.audit.AuditService;
import com.smartflow.backend.domain.entity.FieldOption;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.FormField;
import com.smartflow.backend.domain.entity.RequestType;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.enums.FieldType;
import com.smartflow.backend.domain.enums.PublicationStatus;
import com.smartflow.backend.domain.exception.AdministrationValidationException;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.infrastructure.repository.FieldOptionRepository;
import com.smartflow.backend.infrastructure.repository.FormDefinitionRepository;
import com.smartflow.backend.infrastructure.repository.FormFieldRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * §6.3/§10.1/§6.10 - administration versionnée des formulaires (ADR-17, docs/DECISIONS.md).
 * Un DRAFT à la fois par RequestType, librement modifiable (FormField/FieldOption) tant
 * qu'il reste DRAFT ; publier archive l'ancien PUBLISHED (jamais supprimé, RG-12) et rend
 * ce brouillon PUBLISHED et immuable. Ce service ne réimplémente jamais la résolution du
 * formulaire actif pour une demande (CatalogService.getPublishedForm reste l'unique
 * lecteur de ce côté-là) - il n'écrit que ce que ce lecteur consomme ensuite.
 */
@Service
public class FormAdminService {

    private final FormDefinitionRepository formDefinitionRepository;
    private final FormFieldRepository formFieldRepository;
    private final FieldOptionRepository fieldOptionRepository;
    private final CatalogAdminService catalogAdminService;
    private final AuthorizationService authorizationService;
    private final AuditService auditService;
    private final Clock clock;

    public FormAdminService(FormDefinitionRepository formDefinitionRepository, FormFieldRepository formFieldRepository,
                             FieldOptionRepository fieldOptionRepository, CatalogAdminService catalogAdminService,
                             AuthorizationService authorizationService, AuditService auditService, Clock clock) {
        this.formDefinitionRepository = formDefinitionRepository;
        this.formFieldRepository = formFieldRepository;
        this.fieldOptionRepository = fieldOptionRepository;
        this.catalogAdminService = catalogAdminService;
        this.authorizationService = authorizationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FormDefinition> listVersions(User actingUser, Long requestTypeId) {
        requireFunctionalAdmin(actingUser);
        catalogAdminService.getRequestType(requestTypeId);
        return formDefinitionRepository.findByRequestTypeIdOrderByVersionDesc(requestTypeId);
    }

    @Transactional(readOnly = true)
    public FormDefinitionView getVersion(User actingUser, Long formDefinitionId) {
        requireFunctionalAdmin(actingUser);
        return toView(getDefinition(formDefinitionId));
    }

    /** ADR-17 - un seul DRAFT à la fois par RequestType. */
    @Transactional
    public FormDefinition createDraft(User actingUser, Long requestTypeId) {
        requireFunctionalAdmin(actingUser);
        RequestType requestType = catalogAdminService.getRequestType(requestTypeId);
        List<FormDefinition> existing = formDefinitionRepository.findByRequestTypeIdOrderByVersionDesc(requestTypeId);
        if (existing.stream().anyMatch(d -> d.getStatus() == PublicationStatus.DRAFT)) {
            throw new AdministrationValidationException("DRAFT_ALREADY_EXISTS",
                    "Un brouillon de formulaire existe déjà pour ce type de demande.");
        }
        int nextVersion = existing.stream().mapToInt(FormDefinition::getVersion).max().orElse(0) + 1;
        FormDefinition draft = formDefinitionRepository.save(new FormDefinition(requestType, nextVersion));
        auditService.record(actingUser, "CREATE_DRAFT", "FormDefinition", draft.getId().toString(),
                "requestTypeId=" + requestTypeId + ", version=" + nextVersion);
        return draft;
    }

    /** ADR-17 - jamais publié ni référencé par une demande : suppression physique admise. */
    @Transactional
    public void deleteDraft(User actingUser, Long formDefinitionId) {
        requireFunctionalAdmin(actingUser);
        FormDefinition definition = getDefinition(formDefinitionId);
        requireDraft(definition);
        List<FormField> fields = formFieldRepository.findByFormDefinitionIdOrderByDisplayOrderAsc(definition.getId());
        fields.forEach(field -> fieldOptionRepository.deleteAll(fieldOptionRepository.findByFormFieldIdOrderByDisplayOrderAsc(field.getId())));
        formFieldRepository.deleteAll(fields);
        formDefinitionRepository.delete(definition);
        auditService.record(actingUser, "DELETE_DRAFT", "FormDefinition", formDefinitionId.toString(), null);
    }

    /** ADR-17 - archive l'actuel PUBLISHED (RG-12, jamais supprimé) puis publie ce brouillon. */
    @Transactional
    public FormDefinition publish(User actingUser, Long formDefinitionId) {
        requireFunctionalAdmin(actingUser);
        FormDefinition draft = getDefinition(formDefinitionId);
        requireDraft(draft);
        List<FormField> fields = formFieldRepository.findByFormDefinitionIdOrderByDisplayOrderAsc(draft.getId());
        if (fields.isEmpty()) {
            throw new AdministrationValidationException("EMPTY_FORM",
                    "Un formulaire sans aucun champ ne peut pas être publié.");
        }

        formDefinitionRepository.findByRequestTypeIdAndStatus(draft.getRequestType().getId(), PublicationStatus.PUBLISHED)
                .ifPresent(previous -> {
                    previous.setStatus(PublicationStatus.ARCHIVED);
                    formDefinitionRepository.save(previous);
                });

        draft.setStatus(PublicationStatus.PUBLISHED);
        draft.setPublishedAt(clock.instant());
        draft = formDefinitionRepository.save(draft);
        auditService.record(actingUser, "PUBLISH", "FormDefinition", draft.getId().toString(),
                "requestTypeId=" + draft.getRequestType().getId() + ", version=" + draft.getVersion());
        return draft;
    }

    // --- FormField -----------------------------------------------------------------------

    @Transactional
    public FormField addField(User actingUser, Long formDefinitionId, String code, String label, FieldType fieldType,
                               boolean required, int displayOrder, String helpText, String visibleWhenFieldCode,
                               String visibleWhenValue) {
        requireFunctionalAdmin(actingUser);
        FormDefinition definition = getDefinition(formDefinitionId);
        requireDraft(definition);
        FormField field = new FormField(definition, code, label, fieldType);
        applyFieldAttributes(field, required, displayOrder, helpText, visibleWhenFieldCode, visibleWhenValue);
        field = formFieldRepository.save(field);
        auditService.record(actingUser, "ADD_FIELD", "FormField", field.getId().toString(), "code=" + code);
        return field;
    }

    @Transactional
    public FormField updateField(User actingUser, Long formFieldId, String code, String label, FieldType fieldType,
                                  boolean required, int displayOrder, String helpText, String visibleWhenFieldCode,
                                  String visibleWhenValue) {
        requireFunctionalAdmin(actingUser);
        FormField field = getField(formFieldId);
        requireDraft(field.getFormDefinition());
        field.setCode(code);
        field.setLabel(label);
        field.setFieldType(fieldType);
        applyFieldAttributes(field, required, displayOrder, helpText, visibleWhenFieldCode, visibleWhenValue);
        field = formFieldRepository.save(field);
        auditService.record(actingUser, "UPDATE_FIELD", "FormField", field.getId().toString(), "code=" + code);
        return field;
    }

    /** Public, sans contrôle d'accès propre - même raisonnement que UserAdminService.getUser's
     * own javadoc : jamais exposé sans qu'un appel à requireFunctionalAdmin ait déjà eu lieu
     * juste avant dans le même appelant (FormFieldAdminController le fait toujours). */
    @Transactional(readOnly = true)
    public List<FieldOption> getOptions(Long formFieldId) {
        return fieldOptionRepository.findByFormFieldIdOrderByDisplayOrderAsc(formFieldId);
    }

    @Transactional
    public void deleteField(User actingUser, Long formFieldId) {
        requireFunctionalAdmin(actingUser);
        FormField field = getField(formFieldId);
        requireDraft(field.getFormDefinition());
        fieldOptionRepository.deleteAll(fieldOptionRepository.findByFormFieldIdOrderByDisplayOrderAsc(field.getId()));
        formFieldRepository.delete(field);
        auditService.record(actingUser, "DELETE_FIELD", "FormField", formFieldId.toString(), null);
    }

    // --- FieldOption ---------------------------------------------------------------------

    @Transactional
    public FieldOption addOption(User actingUser, Long formFieldId, String value, String label, int displayOrder) {
        requireFunctionalAdmin(actingUser);
        FormField field = getField(formFieldId);
        requireDraft(field.getFormDefinition());
        FieldOption option = fieldOptionRepository.save(new FieldOption(field, value, label));
        option.setDisplayOrder(displayOrder);
        option = fieldOptionRepository.save(option);
        auditService.record(actingUser, "ADD_OPTION", "FieldOption", option.getId().toString(), "value=" + value);
        return option;
    }

    @Transactional
    public FieldOption updateOption(User actingUser, Long fieldOptionId, String value, String label, int displayOrder) {
        requireFunctionalAdmin(actingUser);
        FieldOption option = getOption(fieldOptionId);
        requireDraft(option.getFormField().getFormDefinition());
        option.setValue(value);
        option.setLabel(label);
        option.setDisplayOrder(displayOrder);
        option = fieldOptionRepository.save(option);
        auditService.record(actingUser, "UPDATE_OPTION", "FieldOption", option.getId().toString(), "value=" + value);
        return option;
    }

    @Transactional
    public void deleteOption(User actingUser, Long fieldOptionId) {
        requireFunctionalAdmin(actingUser);
        FieldOption option = getOption(fieldOptionId);
        requireDraft(option.getFormField().getFormDefinition());
        fieldOptionRepository.delete(option);
        auditService.record(actingUser, "DELETE_OPTION", "FieldOption", fieldOptionId.toString(), null);
    }

    // --- shared --------------------------------------------------------------------------

    private void applyFieldAttributes(FormField field, boolean required, int displayOrder, String helpText,
                                       String visibleWhenFieldCode, String visibleWhenValue) {
        field.setRequired(required);
        field.setDisplayOrder(displayOrder);
        field.setHelpText(helpText);
        field.setVisibleWhenFieldCode(visibleWhenFieldCode);
        field.setVisibleWhenValue(visibleWhenValue);
    }

    private FormDefinitionView toView(FormDefinition definition) {
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

    private void requireDraft(FormDefinition definition) {
        if (definition.getStatus() != PublicationStatus.DRAFT) {
            throw new AdministrationValidationException("NOT_A_DRAFT",
                    "Une version publiée ou archivée n'est plus modifiable (ADR-17).");
        }
    }

    FormDefinition getDefinition(Long formDefinitionId) {
        return formDefinitionRepository.findById(formDefinitionId)
                .orElseThrow(() -> new EntityNotFoundException("Définition de formulaire introuvable."));
    }

    private FormField getField(Long formFieldId) {
        return formFieldRepository.findById(formFieldId)
                .orElseThrow(() -> new EntityNotFoundException("Champ introuvable."));
    }

    private FieldOption getOption(Long fieldOptionId) {
        return fieldOptionRepository.findById(fieldOptionId)
                .orElseThrow(() -> new EntityNotFoundException("Option introuvable."));
    }

    private void requireFunctionalAdmin(User actingUser) {
        if (!authorizationService.isFunctionalAdmin(actingUser)) {
            throw new EntityNotFoundException("Ressource introuvable.");
        }
    }

    public record FormDefinitionView(FormDefinition definition, List<FormFieldView> fields) {
    }

    public record FormFieldView(FormField field, List<FieldOption> options) {
    }
}
