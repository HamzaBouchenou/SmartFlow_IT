package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.EmailTemplateResponse;
import com.smartflow.backend.application.service.EmailTemplateAdminService.TemplateView;

public final class EmailTemplateMapper {

    private EmailTemplateMapper() {
    }

    public static EmailTemplateResponse toResponse(TemplateView view) {
        return new EmailTemplateResponse(view.code(), view.subject(), view.bodyHtml(), view.updatedAt(), view.configured());
    }
}
