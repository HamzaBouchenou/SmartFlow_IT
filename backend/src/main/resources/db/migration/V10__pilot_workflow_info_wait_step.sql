-- V10 : une étape « en attente de complément » qui suspend réellement le SLA sur le
-- workflow pilote Support Informatique - voir ADR-20 (docs/DECISIONS.md) pour la décision
-- complète.
--
-- Ancrage : RG-07 ("Le compteur SLA démarre à la soumission et peut être suspendu par un
-- statut configuré") et REC-SCN-22 du cahier de recette, resté en échec faute de pouvoir
-- l'exécuter : `steps.suspend_sla` valait `false` sur les 8 étapes des deux workflows
-- pilotes, si bien qu'aucune demande réelle n'a jamais pu suspendre son compteur. Le
-- mécanisme lui-même (SlaSuspensionService/SlaSuspensionRule/SlaCalculator) était pourtant
-- déjà écrit et testé en isolation : c'est la configuration seule qui ne l'exerçait jamais.
--
-- Ce que fait cette migration, sur le seul workflow publié du type de demande 1 :
--   1. ajoute l'étape EN_ATTENTE_INFO (suspend_sla = true), portée par la même équipe AGENT
--      que TRAITEMENT afin que l'agent qui a demandé le complément puisse reprendre la main ;
--   2. redirige la transition REQUEST_INFO sortant de TRAITEMENT vers cette nouvelle étape,
--      au lieu de la boucle sur place qui ne changeait pas d'étape et ne pouvait donc rien
--      suspendre (une seconde transition REQUEST_INFO au lieu d'un redirect créerait deux
--      candidats sans condition pour la même action - TransitionResolutionRule ne saurait
--      pas laquelle choisir) ;
--   3. ajoute la transition de reprise EN_ATTENTE_INFO --ASSIGN--> TRAITEMENT ("prendre en
--      charge" à nouveau, RolePermissionRule accorde déjà ASSIGN à l'AGENT).
--
-- Le workflow Achats (type de demande 2) est délibérément laissé tel quel : il n'a jamais
-- porté de REQUEST_INFO, et §2.1 attend justement deux configurations différentes du même
-- moteur (ADR-20).
--
-- Aucun id fixé en dur (leçon de V9) : les étapes sont retrouvées par leur code au sein du
-- workflow publié, et les nouveaux id sont générés par l'identité.

do $$
declare
    v_workflow_id bigint;
    v_traitement_id bigint;
    v_team_id bigint;
    v_next_display_order int;
    v_attente_id bigint;
begin
    select wd.id into v_workflow_id
      from workflow_definitions wd
     where wd.request_type_id = 1 and wd.status = 'PUBLISHED';

    select s.id, s.responsible_team_id into v_traitement_id, v_team_id
      from steps s
     where s.workflow_definition_id = v_workflow_id and s.code = 'TRAITEMENT';

    select coalesce(max(s.display_order), 0) + 1 into v_next_display_order
      from steps s
     where s.workflow_definition_id = v_workflow_id;

    insert into steps (workflow_definition_id, code, name, display_order, responsible_role, responsible_team_id, suspend_sla)
    values (v_workflow_id, 'EN_ATTENTE_INFO', 'En attente de complément', v_next_display_order, 'AGENT', v_team_id, true)
    returning id into v_attente_id;

    update transitions
       set to_step_id = v_attente_id
     where from_step_id = v_traitement_id and action = 'REQUEST_INFO';

    insert into transitions (from_step_id, action, to_step_id, condition_priority)
    values (v_attente_id, 'ASSIGN', v_traitement_id, null);
end $$;
