package com.smartflow.backend.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A file attached to a Request (§6.4). RG-09: extension, size and MIME type are checked
 * server-side before a row is ever created here. storedFilename is a non-guessable
 * generated name; storagePath sits outside the public web root ("Ce qu'il ne faut jamais
 * faire" - never expose a guessable or uncontrolled file URL). Access to the file must
 * follow the same authorization check as the parent Request (§5.1).
 */
@Entity
@Table(name = "attachments")
public class Attachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false)
    private Request request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "stored_filename", nullable = false, unique = true)
    private String storedFilename;

    @Column(name = "content_type", nullable = false, length = 255)
    private String contentType;

    @Column(nullable = false, length = 20)
    private String extension;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_path", nullable = false)
    private String storagePath;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = Instant.now();
    }

    protected Attachment() {
    }

    public Attachment(Request request, User uploadedBy, String originalFilename, String storedFilename,
                       String contentType, String extension, long sizeBytes, String storagePath) {
        this.request = request;
        this.uploadedBy = uploadedBy;
        this.originalFilename = originalFilename;
        this.storedFilename = storedFilename;
        this.contentType = contentType;
        this.extension = extension;
        this.sizeBytes = sizeBytes;
        this.storagePath = storagePath;
    }

    public Request getRequest() {
        return request;
    }

    public User getUploadedBy() {
        return uploadedBy;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
