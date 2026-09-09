package com.smartflow.backend.crosscutting.security;

import com.smartflow.backend.domain.entity.SystemParameter;
import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * §6.10 - "durée des sessions" parmi les paramètres généraux administrables : prouve que
 * la durée appliquée à une session vient bien de la SystemParameter, pas d'une constante
 * compilée. C'est la moitié du mécanisme d'expiration que SessionActivityFilterIT ne peut
 * pas couvrir, MockMvc ne notifiant aucun HttpSessionListener (voir son login()).
 */
class SessionTimeoutListenerTest {

    @Test
    @DisplayName("§6.10 - la durée administrable est appliquée à chaque session créée")
    void appliesTheAdministrableTimeout() {
        MockHttpSession session = listenerWith("15").sessionCreatedOn(new MockHttpSession());

        assertThat(session.getMaxInactiveInterval()).isEqualTo(15 * 60);
    }

    @Test
    @DisplayName("§6.10 - sans paramètre posé, le défaut explicite s'applique plutôt qu'un délai nul (qui signifierait \"n'expire jamais\")")
    void fallsBackToTheExplicitDefault() {
        MockHttpSession session = listenerWith(null).sessionCreatedOn(new MockHttpSession());

        assertThat(session.getMaxInactiveInterval())
                .isEqualTo(SessionTimeoutListener.DEFAULT_SESSION_TIMEOUT_MINUTES * 60);
    }

    private static Fixture listenerWith(String configuredMinutes) {
        SystemParameterRepository repository = mock(SystemParameterRepository.class);
        when(repository.findByKey(anyString())).thenReturn(configuredMinutes == null
                ? Optional.empty()
                : Optional.of(new SystemParameter(SessionTimeoutListener.SESSION_TIMEOUT_MINUTES_KEY, configuredMinutes)));
        return new Fixture(new SessionTimeoutListener(repository));
    }

    private record Fixture(SessionTimeoutListener listener) {
        MockHttpSession sessionCreatedOn(MockHttpSession session) {
            listener.sessionCreated(new HttpSessionEvent(session));
            return session;
        }
    }
}
