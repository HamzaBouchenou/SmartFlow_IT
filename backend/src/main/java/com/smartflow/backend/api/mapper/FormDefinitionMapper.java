package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.FieldOptionResponse;
import com.smartflow.backend.api.dto.response.FormDefinitionResponse;
import com.smartflow.backend.api.dto.response.FormFieldResponse;
import com.smartflow.backend.application.service.CatalogService.FormDefinitionView;
import com.smartflow.backend.application.service.CatalogService.FormFieldView;
import com.smartflow.backend.domain.entity.FieldOption;

public final class FormDefinitionMapper {

    private FormDefinitionMapper() {
    }

    public static FormDefinitionResponse toResponse(FormDefinitionView view) {
        return new FormDefinitionResponse(view.definition().getId(), view.definition().getRequestType().getId(),
                view.definition().getVersion(), view.fields().stream().map(FormDefinitionMapper::toFieldResponse).toList());
    }

    private static FormFieldResponse toFieldResponse(FormFieldView fieldView) {
        var field = fieldView.field();
        return new FormFieldResponse(field.getId(), field.getCode(), field.getLabel(), field.getFieldType(),
                field.isRequired(), field.getDisplayOrder(), field.getHelpText(), field.getVisibleWhenFieldCode(),
                field.getVisibleWhenValue(), fieldView.options().stream().map(FormDefinitionMapper::toOptionResponse).toList());
    }

    private static FieldOptionResponse toOptionResponse(FieldOption option) {
        return new FieldOptionResponse(option.getValue(), option.getLabel(), option.getDisplayOrder());
    }
}
