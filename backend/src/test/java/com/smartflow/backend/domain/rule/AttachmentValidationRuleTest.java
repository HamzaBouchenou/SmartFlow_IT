package com.smartflow.backend.domain.rule;

import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RG-09 - "Les fichiers exécutables sont refusés. Les extensions, la taille et le type MIME
 * sont contrôlés côté serveur." §13 - "Liste blanche d'extensions, contrôle MIME".
 */
class AttachmentValidationRuleTest {

    private final AttachmentValidationRule rule = new AttachmentValidationRule();
    private final Set<String> allowed = Set.of("pdf", "png", "jpg", "docx");

    @ParameterizedTest
    @ValueSource(strings = {"exe", "bat", "sh", "jar", "js", "msi", "dll", "EXE"})
    @DisplayName("RG-09 - an executable extension is refused even if an admin whitelisted it")
    void executableExtensionAlwaysRejected(String extension) {
        assertThatThrownBy(() -> rule.validate(extension, "application/octet-stream", 100,
                Set.of(extension.toLowerCase(java.util.Locale.ROOT), "pdf"), 10_000_000))
                .isInstanceOf(InvalidRequestStateException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((InvalidRequestStateException) ex).getCode()).isEqualTo("EXECUTABLE_REJECTED"));
    }

    @Test
    @DisplayName("RG-09 - an extension outside the configured whitelist is refused")
    void extensionNotInWhitelistRejected() {
        assertThatThrownBy(() -> rule.validate("txt", "text/plain", 100, allowed, 10_000_000))
                .isInstanceOf(InvalidRequestStateException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((InvalidRequestStateException) ex).getCode()).isEqualTo("EXTENSION_NOT_ALLOWED"));
    }

    @Test
    @DisplayName("RG-09 - a file larger than the configured limit is refused")
    void oversizedFileRejected() {
        assertThatThrownBy(() -> rule.validate("pdf", "application/pdf", 1000, allowed, 500))
                .isInstanceOf(InvalidRequestStateException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((InvalidRequestStateException) ex).getCode()).isEqualTo("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("RG-09 - a zero or negative size is refused")
    void nonPositiveSizeRejected() {
        assertThatThrownBy(() -> rule.validate("pdf", "application/pdf", 0, allowed, 500))
                .isInstanceOf(InvalidRequestStateException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((InvalidRequestStateException) ex).getCode()).isEqualTo("FILE_TOO_LARGE"));
    }

    @Test
    @DisplayName("§13 - a whitelisted extension whose sniffed content is actually an executable is refused")
    void sniffedExecutableContentTypeRejectedDespiteAllowedExtension() {
        assertThatThrownBy(() -> rule.validate("pdf", "application/x-msdownload", 100, allowed, 10_000_000))
                .isInstanceOf(InvalidRequestStateException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((InvalidRequestStateException) ex).getCode()).isEqualTo("CONTENT_TYPE_REJECTED"));
    }

    @Test
    @DisplayName("a whitelisted, correctly-sized, non-executable file passes")
    void validAttachmentPasses() {
        assertThatCode(() -> rule.validate("pdf", "application/pdf", 100, allowed, 10_000_000))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a null sniffed content type (unrecognized by the JDK sniffer) does not itself fail validation")
    void unrecognizedContentTypeDoesNotFailAlone() {
        assertThatCode(() -> rule.validate("docx", null, 100, allowed, 10_000_000))
                .doesNotThrowAnyException();
    }
}
