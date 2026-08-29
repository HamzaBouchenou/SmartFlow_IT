package com.smartflow.backend.application.service;

import com.smartflow.backend.application.security.AuthorizationService;
import com.smartflow.backend.domain.entity.Attachment;
import com.smartflow.backend.domain.entity.Request;
import com.smartflow.backend.domain.entity.SystemParameter;
import com.smartflow.backend.domain.entity.User;
import com.smartflow.backend.domain.exception.EntityNotFoundException;
import com.smartflow.backend.domain.rule.AttachmentValidationRule;
import com.smartflow.backend.infrastructure.repository.AttachmentRepository;
import com.smartflow.backend.infrastructure.repository.RequestRepository;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import com.smartflow.backend.infrastructure.storage.AttachmentStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * §6.4 - "pièces jointes autorisées" ; RG-09 - extension, taille et type MIME contrôlés
 * côté serveur ; §13 - liste blanche, renommage interne, stockage hors répertoire public.
 * §5.1 - "L'accès à une pièce jointe doit respecter les mêmes règles que l'accès à la
 * demande concernée" : lecture/téléchargement suivent RequestService.getViewableRequest
 * (ADR-10), exactement comme la demande elle-même ; l'ajout suit ADR-11's canAnnotate
 * (docs/DECISIONS.md), plus strict (jamais AUDITOR).
 */
@Service
public class AttachmentService {

    /** §6.10 - "formats acceptés" administrable, valeur CSV d'extensions en minuscules. */
    public static final String ALLOWED_EXTENSIONS_KEY = "attachments.allowed-extensions";
    /** §6.10 - "taille maximale des fichiers" administrable, en octets. */
    public static final String MAX_SIZE_BYTES_KEY = "attachments.max-size-bytes";

    private static final Set<String> DEFAULT_ALLOWED_EXTENSIONS = Set.of(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "png", "jpg", "jpeg", "gif", "txt", "csv", "zip");
    private static final long DEFAULT_MAX_SIZE_BYTES = 10_000_000L;

    private final AttachmentRepository attachmentRepository;
    private final RequestRepository requestRepository;
    private final RequestService requestService;
    private final AuthorizationService authorizationService;
    private final AttachmentValidationRule attachmentValidationRule;
    private final AttachmentStorage attachmentStorage;
    private final SystemParameterRepository systemParameterRepository;

    public AttachmentService(AttachmentRepository attachmentRepository, RequestRepository requestRepository,
                              RequestService requestService, AuthorizationService authorizationService,
                              AttachmentValidationRule attachmentValidationRule, AttachmentStorage attachmentStorage,
                              SystemParameterRepository systemParameterRepository) {
        this.attachmentRepository = attachmentRepository;
        this.requestRepository = requestRepository;
        this.requestService = requestService;
        this.authorizationService = authorizationService;
        this.attachmentValidationRule = attachmentValidationRule;
        this.attachmentStorage = attachmentStorage;
        this.systemParameterRepository = systemParameterRepository;
    }

    /** ADR-11 : un canAnnotate refusé se traduit en 404, jamais 403 (voir CommentService). */
    @Transactional
    public Attachment upload(User actingUser, Long requestId, MultipartFile file) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException("Demande introuvable."));
        if (!authorizationService.canAnnotate(actingUser, request)) {
            throw new EntityNotFoundException("Demande introuvable.");
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        byte[] content = readAllBytes(file);
        String sniffedContentType = sniff(content);

        attachmentValidationRule.validate(extension, sniffedContentType, content.length,
                configuredAllowedExtensions(), configuredMaxSizeBytes());

        String storedFilename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        String storagePath = attachmentStorage.store(storedFilename, content);
        String contentType = resolveContentType(sniffedContentType, file.getContentType());

        Attachment attachment = new Attachment(request, actingUser, originalFilename, storedFilename, contentType,
                extension, content.length, storagePath);
        return attachmentRepository.save(attachment);
    }

    @Transactional(readOnly = true)
    public List<Attachment> list(User actingUser, Long requestId) {
        Request request = requestService.getViewableRequest(actingUser, requestId);
        return attachmentRepository.findByRequestIdOrderByUploadedAtAsc(request.getId());
    }

    @Transactional(readOnly = true)
    public DownloadableAttachment download(User actingUser, Long requestId, Long attachmentId) {
        Request request = requestService.getViewableRequest(actingUser, requestId);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getRequest().getId().equals(request.getId()))
                .orElseThrow(() -> new EntityNotFoundException("Pièce jointe introuvable."));
        return new DownloadableAttachment(attachment, attachmentStorage.read(attachment.getStoragePath()));
    }

    private Set<String> configuredAllowedExtensions() {
        return systemParameterRepository.findByKey(ALLOWED_EXTENSIONS_KEY)
                .map(SystemParameter::getValue)
                .map(csv -> Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .filter(s -> !s.isBlank())
                        .collect(Collectors.toUnmodifiableSet()))
                .orElse(DEFAULT_ALLOWED_EXTENSIONS);
    }

    private long configuredMaxSizeBytes() {
        return systemParameterRepository.findByKey(MAX_SIZE_BYTES_KEY)
                .map(SystemParameter::getValue)
                .map(Long::parseLong)
                .orElse(DEFAULT_MAX_SIZE_BYTES);
    }

    private static String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "fichier";
        }
        // Strip any directory component a browser might still send, and control characters
        // that would otherwise reach the Content-Disposition header on download.
        String base = rawFilename.replace("\\", "/");
        base = base.substring(base.lastIndexOf('/') + 1);
        return base.replaceAll("[\\r\\n\"]", "_");
    }

    private static String extractExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    /**
     * §13 - "contrôle MIME" : java.net.URLConnection's own sniffer only recognizes a
     * handful of image/text formats, not executable signatures - sniffDangerousMagicBytes
     * below checks the specific ones RG-09 cares about first, so a renamed .exe/ELF/script
     * saved under an innocuous extension cannot slip past the whitelist. Falls back to the
     * JDK sniffer only when no dangerous signature matched.
     */
    private static String sniff(byte[] content) {
        String dangerousSignature = sniffDangerousMagicBytes(content);
        if (dangerousSignature != null) {
            return dangerousSignature;
        }
        try (var in = new ByteArrayInputStream(content)) {
            return URLConnection.guessContentTypeFromStream(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static String sniffDangerousMagicBytes(byte[] content) {
        if (startsWith(content, 0x4D, 0x5A)) { // "MZ" - Windows PE (.exe/.dll)
            return "application/x-msdownload";
        }
        if (startsWith(content, 0x7F, 'E', 'L', 'F')) { // Linux ELF binary
            return "application/x-elf";
        }
        if (startsWith(content, '#', '!')) { // interpreter shebang (shell/python/... script)
            return "application/x-sh";
        }
        return null;
    }

    private static boolean startsWith(byte[] content, int... signature) {
        if (content.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((content[i] & 0xFF) != (signature[i] & 0xFF)) {
                return false;
            }
        }
        return true;
    }

    private static String resolveContentType(String sniffedContentType, String declaredContentType) {
        if (sniffedContentType != null) {
            return sniffedContentType;
        }
        if (declaredContentType != null && !declaredContentType.isBlank()) {
            return declaredContentType;
        }
        return "application/octet-stream";
    }

    private static byte[] readAllBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la pièce jointe entrante impossible.", e);
        }
    }

    public record DownloadableAttachment(Attachment attachment, byte[] content) {
    }
}
