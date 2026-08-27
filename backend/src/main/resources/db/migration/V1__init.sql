-- V1 : migration d'amorçage.
--
-- Volontairement vide de tout schéma métier : le cahier des charges (docs/) ne définit
-- pas encore le modèle de données (aucune entité JPA n'existe dans domain/entity à ce
-- stade). Cette migration établit uniquement la présence du répertoire Flyway et de
-- l'historique de version, condition requise par `spring.jpa.hibernate.ddl-auto=validate`.
--
-- Toute création de table doit être ajoutée dans une migration V2+ ultérieure, ancrée sur
-- un chapitre du cahier des charges ou une décision de docs/DECISIONS.md, jamais ici.
select 1;
