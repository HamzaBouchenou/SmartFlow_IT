package com.smartflow.backend.api.dto.response;

import java.time.Instant;

/**
 * §6.4/§9.4 - métadonnées d'une pièce jointe. Ne porte jamais storedFilename ni
 * storagePath (RG-09/§13 - "ne jamais exposer une URL de fichier devinable") : le contenu
 * ne se récupère que via GET .../attachments/{id}, toujours réévalué par
 * AttachmentService.download.
 */
public record AttachmentResponse(Long id, String originalFilename, String contentType, long sizeBytes,
                                  Long uploadedById, String uploadedByName, Instant uploadedAt) {
}
