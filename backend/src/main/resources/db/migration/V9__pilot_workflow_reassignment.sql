-- V9 : deux corrections de topologie sur les workflows pilotes, relevées par
-- docs/CAHIER_DE_RECETTE.md (REC-SCN-10/18/19) en recette réelle plutôt qu'en test unitaire
-- - voir ADR-18 (docs/DECISIONS.md) pour la décision complète.
--
-- (1) Réaffectation nommée/automatique pendant le TRAITEMENT (§6.6) : ASSIGN n'existe
-- jusqu'ici que depuis QUALIFICATION (vers VALIDATION, ou vers TRAITEMENT en direct pour
-- une demande CRITICAL) - une fois à l'étape TRAITEMENT, aucune arête sortante ASSIGN
-- n'existe pour reboucler sur elle-même, alors que REQUEST_INFO le fait déjà (transitions
-- 8 et 13 de V5). Ajoute une transition ASSIGN en boucle sur TRAITEMENT (steps 3 et 7),
-- exactement le même motif que le REQUEST_INFO déjà présent - jamais une nouvelle branche
-- de moteur, une seule ligne de configuration par workflow pilote (§2.1).
--
-- (2) Un second AGENT par équipe pilote (§6.6, "affectation automatique au membre le moins
-- chargé") : le jeu de données V5 ne porte qu'un seul AGENT par équipe (Sara/Reda), ce qui
-- rend AutoAssignmentRule.pickLeastLoaded invérifiable en recette réelle faute d'un second
-- candidat à départager - la règle elle-même reste testée seule et exactement par
-- AutoAssignmentRuleTest, mais REC-SCN-19 ne peut pas la rejouer en bout en bout sans ce
-- second compte. Même mot de passe de démonstration que le reste de V5 (Password123!).
--
-- Contrairement à V5/V6, cette migration ne fixe AUCUN id explicite : trouvé en recette
-- réelle contre un volume `pgdata` de démonstration déjà utilisé de façon interactive (un
-- brouillon de workflow publié via l'écran d'administration avait déjà consommé les id de
-- `transitions` jusqu'à 22 - V5 ne va que jusqu'à 13). Un id figé du style
-- `insert into transitions (id, ...) values (14, ...)` n'est sûr que dans la toute première
-- migration de données (V5, avant que quiconque n'ait pu écrire une seule ligne) - toute
-- migration ultérieure doit laisser l'identité générer la valeur, jamais la deviner : c'est
-- invisible à Testcontainers (qui repart toujours d'une base vierge) et n'apparaît qu'en
-- rejouant les migrations sur un volume réellement utilisé, exactement comme le bug de
-- permissions du Dockerfile documenté plus haut dans CLAUDE.md.

insert into transitions (from_step_id, action, to_step_id, condition_priority) values
    (3, 'ASSIGN', 3, null),
    (7, 'ASSIGN', 7, null);

with new_agents as (
    insert into users (first_name, last_name, email, password_hash, department_id, manager_id)
    values
        ('Mehdi', 'Ouazzani', 'mehdi.ouazzani@smartflow.local', '$2a$10$lmJWexzyuuX2G0vrPfaR0u4GUaqnyj/LBsMV9ETJxGCJBh/WssoSq', 2, 3),
        ('Salma', 'Rifi', 'salma.rifi@smartflow.local', '$2a$10$lmJWexzyuuX2G0vrPfaR0u4GUaqnyj/LBsMV9ETJxGCJBh/WssoSq', 3, 6)
    returning id, department_id
)
insert into user_role_assignments (user_id, role, scope_type, scope_id)
select id, 'AGENT', 'TEAM', case department_id when 2 then 1 else 2 end
from new_agents;
