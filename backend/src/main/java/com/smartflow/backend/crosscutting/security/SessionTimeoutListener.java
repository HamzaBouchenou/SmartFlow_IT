package com.smartflow.backend.crosscutting.security;

import com.smartflow.backend.infrastructure.repository.SystemParameterRepository;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * §6.1 ("Expiration de session") et §6.10 ("Paramètres généraux : ... durée des sessions")
 * - applique la durée d'inactivité administrable à chaque session créée.
 *
 * Pourquoi un HttpSessionListener plutôt que la seule propriété
 * `server.servlet.session.timeout` : cette dernière est lue une fois au démarrage, donc un
 * administrateur ne pourrait pas la modifier sans redéployer, alors que §6.10 la range
 * explicitement parmi les paramètres généraux ajustables au même titre que la taille des
 * pièces jointes. La propriété reste posée dans application.properties comme valeur de
 * repli explicite (jamais implicite) ; ce listener la remplace dès qu'une ligne
 * SystemParameter existe. Lu à la création de chaque session : une modification s'applique
 * aux connexions suivantes, jamais rétroactivement à une session déjà ouverte - un
 * comportement volontaire (raccourcir le paramètre ne doit pas déconnecter en masse).
 */
@Component
public class SessionTimeoutListener implements HttpSessionListener {

    public static final String SESSION_TIMEOUT_MINUTES_KEY = "security.session.timeout-minutes";
    static final int DEFAULT_SESSION_TIMEOUT_MINUTES = 30;

    private static final Logger log = LoggerFactory.getLogger(SessionTimeoutListener.class);

    private final SystemParameterRepository systemParameterRepository;

    public SessionTimeoutListener(SystemParameterRepository systemParameterRepository) {
        this.systemParameterRepository = systemParameterRepository;
    }

    @Override
    public void sessionCreated(HttpSessionEvent event) {
        int minutes = configuredTimeoutMinutes();
        event.getSession().setMaxInactiveInterval(minutes * 60);
        log.debug("Session {} created with a {}-minute inactivity timeout", event.getSession().getId(), minutes);
    }

    private int configuredTimeoutMinutes() {
        return systemParameterRepository.findByKey(SESSION_TIMEOUT_MINUTES_KEY)
                .map(parameter -> Integer.parseInt(parameter.getValue()))
                .orElse(DEFAULT_SESSION_TIMEOUT_MINUTES);
    }
}
