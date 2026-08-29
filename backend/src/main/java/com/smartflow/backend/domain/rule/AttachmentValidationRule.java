package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.exception.InvalidRequestStateException;

import java.util.Locale;
import java.util.Set;

/**
 * RG-09 - "Les fichiers exécutables sont refusés. Les extensions, la taille et le type MIME
 * sont contrôlés côté serveur." §13 - "Liste blanche d'extensions, contrôle MIME,
 * renommage interne, stockage hors répertoire public".
 *
 * Pure: no I/O, no Spring. The caller (application/service) resolves the extension from
 * the original filename, sniffs the content type from the actual bytes (never trusts the
 * client-declared Content-Type header alone - infrastructure/storage does this), and reads
 * the admin-configured whitelist/size limit (§6.10 - "formats acceptés, taille maximale des
 * fichiers", SystemParameter) before calling validate().
 */
public class AttachmentValidationRule {

    // Refused unconditionally, regardless of any admin-configured whitelist (RG-09): a
    // misconfigured whitelist must never be able to let an executable through. Not
    // exhaustive by design - it is the second line of defense behind the whitelist itself,
    // which is deny-by-default for anything not explicitly listed as accepted.
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "bat", "cmd", "com", "msi", "scr", "pif", "gadget", "cpl", "hta",
            "jar", "js", "jse", "vb", "vbs", "vbe", "ws", "wsf", "wsh", "ps1", "psm1",
            "sh", "bash", "bin", "run", "app", "apk", "deb", "rpm", "dll", "so", "dylib",
            "reg", "action");

    // §13 - "contrôle MIME" : the extension can lie (a renamed .exe saved as report.pdf),
    // the sniffed content type - read from the file's actual bytes - cannot.
    private static final Set<String> BLOCKED_CONTENT_TYPES = Set.of(
            "application/x-msdownload", "application/x-msdos-program", "application/x-executable",
            "application/x-sh", "application/x-elf", "application/vnd.microsoft.portable-executable",
            "application/java-archive", "application/x-java-archive");

    public void validate(String extension, String sniffedContentType, long sizeBytes,
                          Set<String> allowedExtensions, long maxSizeBytes) {
        String normalizedExtension = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTENSIONS.contains(normalizedExtension)) {
            throw new InvalidRequestStateException("EXECUTABLE_REJECTED",
                    "Les fichiers exécutables sont refusés (RG-09).");
        }
        if (!allowedExtensions.contains(normalizedExtension)) {
            throw new InvalidRequestStateException("EXTENSION_NOT_ALLOWED",
                    "Extension de fichier non autorisée : ." + normalizedExtension);
        }
        if (sizeBytes <= 0 || sizeBytes > maxSizeBytes) {
            throw new InvalidRequestStateException("FILE_TOO_LARGE",
                    "La taille du fichier dépasse la limite autorisée.");
        }
        if (sniffedContentType != null && BLOCKED_CONTENT_TYPES.contains(sniffedContentType.toLowerCase(Locale.ROOT))) {
            throw new InvalidRequestStateException("CONTENT_TYPE_REJECTED",
                    "Le contenu du fichier a été détecté comme un exécutable (RG-09).");
        }
    }
}
