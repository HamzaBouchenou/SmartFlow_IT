package com.smartflow.backend.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * RG-09/§13 - "stockage hors répertoire public" : écrit le contenu d'une pièce jointe dans
 * un répertoire jamais servi comme ressource statique (crosscutting/security/SecurityConfig
 * n'expose aucune route dessus) et atteint uniquement via le chemin de lecture autorisé
 * d'application/service/AttachmentService. storedFilename est un nom généré (UUID) par
 * l'appelant, jamais un chemin fourni par le client - cette classe ne fait donc jamais
 * confiance à une entrée utilisateur pour composer un chemin disque, mais reste défensive
 * (normalize + startsWith) au cas où cette garantie changerait un jour.
 */
@Component
public class AttachmentStorage {

    private final Path rootDirectory;

    public AttachmentStorage(@Value("${smartflow.attachments.storage-dir}") String storageDir) {
        this.rootDirectory = Path.of(storageDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootDirectory);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Impossible de créer le répertoire de stockage des pièces jointes: " + rootDirectory, e);
        }
    }

    /** Écrit content sous storedFilename et renvoie le chemin absolu à conserver sur Attachment.storagePath. */
    public String store(String storedFilename, byte[] content) {
        Path target = resolveWithinRoot(storedFilename);
        try (InputStream in = new ByteArrayInputStream(content)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture de la pièce jointe impossible.", e);
        }
        return target.toString();
    }

    public byte[] read(String storagePath) {
        try {
            return Files.readAllBytes(Path.of(storagePath));
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture de la pièce jointe impossible.", e);
        }
    }

    private Path resolveWithinRoot(String storedFilename) {
        Path target = rootDirectory.resolve(storedFilename).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new IllegalArgumentException("Nom de fichier de stockage invalide.");
        }
        return target;
    }
}
