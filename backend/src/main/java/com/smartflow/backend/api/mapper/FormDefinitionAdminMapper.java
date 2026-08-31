package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.FieldOptionAdminResponse;
import com.smartflow.backend.api.dto.response.FormDefinitionAdminResponse;
import com.smartflow.backend.api.dto.response.FormFieldAdminResponse;
import com.smartflow.backend.application.service.FormAdminService.FormDefinitionView;
import com.smartflow.backend.application.service.FormAdminService.FormFieldView;
import com.smartflow.backend.domain.entity.FieldOption;
import com.smartflow.backend.domain.entity.FormDefinition;
import com.smartflow.backend.domain.entity.FormField;

import java.util.List;

public final class FormDefinitionAdminMapper {

    private FormDefinitionAdminMapper() {
    }

    public static FormDefinitionAdminResponse toResponse(FormDefinitionView view) {
        FormDefinition definition = view.definition();
        return new FormDefinitionAdminResponse(definition.getId(), definition.getRequestType().getId(),
                definition.getVersion(), definition.getStatus().name(), definition.getPublishedAt(),
                definition.getCreatedAt(), view.fields().stream()
                        .map(fieldView -> toFieldResponse(fieldView.field(), fieldView.options()))
                        .toList());
    }

    /** Sans les champs (`fields` = null) - pour une ligne d'une liste de versions, où le
     * détail complet n'a pas besoin d'être chargé (voir FormAdminController.listVersions). */
    public static FormDefinitionAdminResponse toSummary(FormDefinition definition) {
        return new FormDefinitionAdminResponse(definition.getId(), definition.getRequestType().getId(),
                definition.getVersion(), definition.getStatus().name(), definition.getPublishedAt(),
                definition.getCreatedAt(), null);
    }

    public static FormFieldAdminResponse toFieldResponse(FormField field, List<FieldOption> options) {
        return new FormFieldAdminResponse(field.getId(), field.getCode(), field.getLabel(), field.getFieldType(),
                field.isRequired(), field.getDisplayOrder(), field.getHelpText(), field.getVisibleWhenFieldCode(),
                field.getVisibleWhenValue(), options.stream().map(FormDefinitionAdminMapper::toOptionResponse).toList());
    }

    public static FieldOptionAdminResponse toOptionResponse(FieldOption option) {
        return new FieldOptionAdminResponse(option.getId(), option.getValue(), option.getLabel(), option.getDisplayOrder());
    }
}
