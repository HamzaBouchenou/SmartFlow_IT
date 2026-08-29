package com.smartflow.backend.api.mapper;

import com.smartflow.backend.api.dto.response.AttachmentResponse;
import com.smartflow.backend.domain.entity.Attachment;
import com.smartflow.backend.domain.entity.User;

public final class AttachmentMapper {

    private AttachmentMapper() {
    }

    public static AttachmentResponse toResponse(Attachment attachment) {
        User uploadedBy = attachment.getUploadedBy();
        return new AttachmentResponse(attachment.getId(), attachment.getOriginalFilename(), attachment.getContentType(),
                attachment.getSizeBytes(), uploadedBy.getId(), uploadedBy.getFirstName() + " " + uploadedBy.getLastName(),
                attachment.getUploadedAt());
    }
}
