package com.smartflow.backend.infrastructure.ai;

import com.smartflow.backend.domain.exception.InvalidRequestStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ADR-16 (docs/DECISIONS.md) - "mode désactivé" : classify/summarize must refuse
 * synchronously, before any network attempt, whenever SMARTFLOW_AI_ENABLED is false - a
 * plain unit test suffices precisely because a disabled AiClient never reaches the network
 * (base-url points nowhere real, on purpose, to prove no connection is even attempted).
 */
class AiClientTest {

    private final AiClient disabledClient = new AiClient("http://localhost:1", false);

    @Test
    @DisplayName("§12.2 - classify refuses before any network call when the AI module is disabled")
    void classifyRefusesWhenDisabled() {
        assertThatThrownBy(() -> disabledClient.classify("Titre", "Description"))
                .isInstanceOf(InvalidRequestStateException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((InvalidRequestStateException) ex).getCode()).isEqualTo("AI_DISABLED"));
    }

    @Test
    @DisplayName("§12.2 - summarize refuses before any network call when the AI module is disabled")
    void summarizeRefusesWhenDisabled() {
        assertThatThrownBy(() -> disabledClient.summarize("Un texte à résumer."))
                .isInstanceOf(InvalidRequestStateException.class)
                .satisfies(ex -> org.assertj.core.api.Assertions.assertThat(
                        ((InvalidRequestStateException) ex).getCode()).isEqualTo("AI_DISABLED"));
    }

    @Test
    @DisplayName("isEnabled reflects the configured flag")
    void isEnabledReflectsConfiguration() {
        org.assertj.core.api.Assertions.assertThat(disabledClient.isEnabled()).isFalse();
        AiClient enabledClient = new AiClient("http://localhost:1", true);
        org.assertj.core.api.Assertions.assertThat(enabledClient.isEnabled()).isTrue();
    }
}
