package com.smartflow.backend.domain.rule;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * §6.4/ADR-24 (docs/DECISIONS.md) - extrait d'un corps de commentaire les adresses
 * mentionnées, sous la forme {@code @prenom.nom@domaine}.
 *
 * Règle pure : aucun Spring, aucun accès base, aucune notion de droit. Elle dit seulement
 * « ce texte nomme ces adresses » ; qui existe, qui a le droit de lire la demande et qui
 * sera notifié reste une décision de CommentService et d'AuthorizationService.canView.
 * C'est ce qui la rend testable seule (§3.4).
 *
 * Le jeton est l'adresse complète parce que c'est la seule identité unique par contrainte
 * de base (users.email) - voir ADR-24 pour le choix et son coût.
 */
public class MentionParsingRule {

    /**
     * Un '@' d'ouverture qui ne soit pas collé à un mot (sans quoi la fin d'une adresse
     * déjà mentionnée serait relue comme une seconde mention), puis une adresse. Les
     * caractères acceptés sont ceux d'une adresse ordinaire ; la ponctuation qui suit une
     * phrase ("... @a.b@c.d.") n'est pas capturée, le point final n'appartenant pas au
     * domaine.
     */
    private static final Pattern MENTION = Pattern.compile(
            "(?<![\\w.@+-])@([\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+)");

    /** Limite de garde : un commentaire n'a aucune raison de nommer plus de monde que cela,
     * et une mention par personne présente de l'organisation serait un envoi en masse
     * déguisé plutôt qu'une sollicitation (§6.8 - "événements importants"). */
    public static final int MAX_MENTIONS_PER_COMMENT = 10;

    /**
     * Les adresses mentionnées, en minuscules, sans doublon, dans l'ordre d'apparition.
     * Un texte vide ou nul n'en contient aucune.
     */
    public Set<String> parse(String body) {
        Set<String> emails = new LinkedHashSet<>();
        if (body == null || body.isBlank()) {
            return emails;
        }
        Matcher matcher = MENTION.matcher(body);
        while (matcher.find() && emails.size() < MAX_MENTIONS_PER_COMMENT) {
            emails.add(matcher.group(1).toLowerCase());
        }
        return emails;
    }
}
