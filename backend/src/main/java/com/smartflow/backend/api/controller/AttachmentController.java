package com.smartflow.backend.api.controller;

import com.smartflow.backend.api.dto.response.AttachmentResponse;
import com.smartflow.backend.api.mapper.AttachmentMapper;
import com.smartflow.backend.application.service.AttachmentService;
import com.smartflow.backend.crosscutting.security.SmartFlowUserDetails;
import com.smartflow.backend.domain.entity.Attachment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * §6.4/§11.2 - POST .../attachments ("Ajouter une pièce jointe contrôlée"), RG-09. Le
 * téléchargement ne passe jamais par une URL de fichier statique (RG-09/§13) : chaque octet
 * transite par AttachmentService.download, qui réévalue l'accès à chaque appel.
 */
@RestController
@RequestMapping("/api/v1/requests/{requestId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse upload(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long requestId,
                                      @RequestParam("file") MultipartFile file) {
        return AttachmentMapper.toResponse(attachmentService.upload(principal.getUser(), requestId, file));
    }

    @GetMapping
    public List<AttachmentResponse> list(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long requestId) {
        return attachmentService.list(principal.getUser(), requestId).stream().map(AttachmentMapper::toResponse).toList();
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal SmartFlowUserDetails principal, @PathVariable Long requestId,
                                            @PathVariable Long attachmentId) {
        AttachmentService.DownloadableAttachment downloadable =
                attachmentService.download(principal.getUser(), requestId, attachmentId);
        Attachment attachment = downloadable.attachment();
        MediaType mediaType = parseOrOctetStream(attachment.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + attachment.getOriginalFilename() + "\"")
                .body(downloadable.content());
    }

    private static MediaType parseOrOctetStream(String contentType) {
        try {
            return MediaType.parseMediaType(contentType);
        } catch (org.springframework.http.InvalidMediaTypeException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
