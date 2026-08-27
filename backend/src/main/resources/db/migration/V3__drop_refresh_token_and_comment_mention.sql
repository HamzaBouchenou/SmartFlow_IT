-- V3 : retrait de refresh_tokens et comment_mentions.
--
-- refresh_tokens présupposait une authentification par jeton JWT (ADR-01, §11.2), mais
-- CLAUDE.md liste ADR-01 comme point bloquant explicite et non tranché dans
-- docs/DECISIONS.md : la table anticipait une décision qui n'a pas été actée.
--
-- comment_mentions relève du §6.4 mais ne fait pas partie du lot en cours.
--
-- V2 est déjà appliquée sur une base existante (flyway_schema_history) : elle reste
-- inchangée, cette correction passe par une migration suivante plutôt qu'une réécriture.

drop table comment_mentions;
drop table refresh_tokens;
