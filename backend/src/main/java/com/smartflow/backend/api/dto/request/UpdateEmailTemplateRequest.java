package com.smartflow.backend.api.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateEmailTemplateRequest(@NotBlank String subject, @NotBlank String bodyHtml) {
}
