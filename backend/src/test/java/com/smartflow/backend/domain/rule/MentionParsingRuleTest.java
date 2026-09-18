package com.smartflow.backend.domain.rule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** §6.4/ADR-24 - extraction des adresses mentionnées. Règle pure : aucun contexte Spring,
 * aucune notion de droit (§3.4 - tests unitaires sur les règles métier). */
class MentionParsingRuleTest {

    private final MentionParsingRule rule = new MentionParsingRule();

    @Test
    @DisplayName("extrait une adresse mentionnée au milieu d'une phrase")
    void extractsASingleMention() {
        assertThat(rule.parse("Peux-tu regarder, @sara.bennis@smartflow.local ? Merci."))
                .containsExactly("sara.bennis@smartflow.local");
    }

    @Test
    @DisplayName("extrait plusieurs adresses, sans doublon, dans l'ordre d'apparition")
    void extractsSeveralMentionsWithoutDuplicates() {
        assertThat(rule.parse("@a.b@x.local et @c.d@x.local, puis encore @a.b@x.local"))
                .containsExactly("a.b@x.local", "c.d@x.local");
    }

    @Test
    @DisplayName("normalise la casse: une adresse est la même quelle que soit sa graphie")
    void lowercasesMentions() {
        assertThat(rule.parse("@Sara.Bennis@SmartFlow.Local")).containsExactly("sara.bennis@smartflow.local");
    }

    @Test
    @DisplayName("une adresse écrite sans '@' d'ouverture n'est pas une mention")
    void plainEmailIsNotAMention() {
        assertThat(rule.parse("Écris-lui à sara.bennis@smartflow.local plutôt.")).isEmpty();
    }

    @Test
    @DisplayName("la fin d'une mention n'est jamais relue comme une seconde mention")
    void doesNotRescanTheTailOfAMention() {
        assertThat(rule.parse("@sara.bennis@smartflow.local")).hasSize(1);
    }

    @Test
    @DisplayName("un texte vide ou nul ne mentionne personne")
    void emptyBodyMentionsNobody() {
        assertThat(rule.parse(null)).isEmpty();
        assertThat(rule.parse("   ")).isEmpty();
        assertThat(rule.parse("Aucune mention ici.")).isEmpty();
    }

    @Test
    @DisplayName("la ponctuation qui suit une mention ne fait pas partie de l'adresse")
    void trailingPunctuationIsNotPartOfTheAddress() {
        assertThat(rule.parse("merci @a.b@x.local.")).containsExactly("a.b@x.local");
        assertThat(rule.parse("merci @a.b@x.local, vraiment")).containsExactly("a.b@x.local");
    }

    @Test
    @DisplayName("le nombre de mentions retenues est borné: un commentaire n'est pas un envoi en masse")
    void stopsAtTheMaximum() {
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < MentionParsingRule.MAX_MENTIONS_PER_COMMENT + 5; i++) {
            body.append("@user").append(i).append("@x.local ");
        }
        assertThat(rule.parse(body.toString())).hasSize(MentionParsingRule.MAX_MENTIONS_PER_COMMENT);
    }
}
