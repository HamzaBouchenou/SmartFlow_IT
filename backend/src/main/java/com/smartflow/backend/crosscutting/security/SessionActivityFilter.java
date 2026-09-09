package com.smartflow.backend.crosscutting.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;

/**
 * §6.1 ("Expiration de session") et §6.10 ("durée des sessions") - ADR-22
 * (docs/DECISIONS.md) : mesure l'inactivité sur les seules requêtes déclenchées par
 * l'utilisateur, et invalide la session au-delà de la durée administrable.
 *
 * Pourquoi ce filtre existe. Le conteneur mesure l'inactivité d'une session en
 * `lastAccessedTime`, que *toute* requête portant le cookie de session repousse. Or le SPA
 * interroge le compteur de notifications non lues toutes les 30 secondes depuis l'ossature
 * (NotificationBell, §6.8), donc sur tous les écrans : un onglet simplement laissé ouvert
 * repoussait indéfiniment `lastAccessedTime`, et `SessionTimeoutListener` ne pouvait plus
 * jamais expirer une session. La durée administrable existait, elle n'était plus jamais
 * atteinte - un poste laissé déverrouillé restait connecté indéfiniment (§13).
 *
 * Ce que le filtre fait à la place. Il tient sa propre horloge d'activité,
 * `lastInteractionAt`, qu'un appel de fond ne repousse pas : le client marque ses requêtes
 * d'arrière-plan de l'en-tête {@value #BACKGROUND_REQUEST_HEADER} (api/client.ts). Un
 * client qui ment ne peut que raccourcir sa propre session, jamais la prolonger - poser
 * l'en-tête ne fait qu'empêcher de repousser l'échéance, et l'omettre revient au geste
 * utilisateur ordinaire, indiscernable d'un vrai. Le seuil reste celui de la session
 * (`getMaxInactiveInterval`), posé par SessionTimeoutListener depuis la SystemParameter :
 * cette classe ne relit aucun paramètre et n'en redéfinit aucun défaut.
 *
 * Il s'exécute avant la chaîne Spring Security (l'ordre ci-dessous) : la session est donc
 * déjà invalidée quand le contexte de sécurité tente de s'y charger, la requête devient
 * anonyme et RestAuthenticationEntryPoint rend le 401 au format d'erreur commun - avec le
 * code SESSION_EXPIRED plutôt qu'UNAUTHENTICATED, pour que le SPA distingue "votre session
 * a expiré" d'un "vous n'êtes pas connecté" (§11.1). Cette distinction ne révèle rien : son
 * destinataire est justement celui qui possédait la session.
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER - 10)
public class SessionActivityFilter extends OncePerRequestFilter {

    /** L'instant du dernier geste réel de l'utilisateur, en millisecondes. */
    public static final String LAST_INTERACTION_ATTRIBUTE = "smartflow.session.lastInteractionAt";

    /** Posé par le client sur ses requêtes périodiques d'arrière-plan (api/client.ts). */
    public static final String BACKGROUND_REQUEST_HEADER = "X-SmartFlow-Background";

    /** Lu par RestAuthenticationEntryPoint pour distinguer une session expirée d'une absence de session. */
    static final String EXPIRED_REQUEST_ATTRIBUTE = "smartflow.session.expired";

    private static final Logger log = LoggerFactory.getLogger(SessionActivityFilter.class);

    private final Clock clock;

    public SessionActivityFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            applyIdlePolicy(request, session);
        }
        filterChain.doFilter(request, response);
    }

    private void applyIdlePolicy(HttpServletRequest request, HttpSession session) {
        long now = clock.millis();
        Object lastInteraction = session.getAttribute(LAST_INTERACTION_ATTRIBUTE);
        int timeoutSeconds = session.getMaxInactiveInterval();

        // Première requête d'une session tout juste créée (la connexion elle-même la crée
        // après ce filtre) : l'horloge d'activité démarre ici, elle ne peut pas déjà être
        // dépassée.
        if (!(lastInteraction instanceof Long lastInteractionMillis)) {
            session.setAttribute(LAST_INTERACTION_ATTRIBUTE, now);
            return;
        }

        // Un délai nul ou négatif signifie "n'expire pas" au sens de l'API servlet : rien à
        // faire respecter, y compris pour un appel de fond.
        if (timeoutSeconds > 0 && now - lastInteractionMillis > timeoutSeconds * 1000L) {
            log.debug("Session {} invalidated after {} ms without a user-initiated request",
                    session.getId(), now - lastInteractionMillis);
            session.invalidate();
            request.setAttribute(EXPIRED_REQUEST_ATTRIBUTE, Boolean.TRUE);
            return;
        }

        if (!isBackgroundRequest(request)) {
            session.setAttribute(LAST_INTERACTION_ATTRIBUTE, now);
        }
    }

    private static boolean isBackgroundRequest(HttpServletRequest request) {
        return Boolean.parseBoolean(request.getHeader(BACKGROUND_REQUEST_HEADER));
    }
}
