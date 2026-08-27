-- V4 : point de suspension SLA piloté par étape, cumul de suspension, calendrier ouvré
-- désactivé par défaut.
--
-- RG-07 : le compteur SLA "peut être suspendu uniquement par un statut prévu dans la
-- configuration". suspend_sla marque quelle(s) étape(s) du workflow constituent un tel
-- point de suspension configuré (§6.5, §6.7) ; ni Step ni Request n'exposaient jusqu'ici
-- de quoi le déclencher légitimement.
--
-- sla_suspended_minutes corrige un calcul faux dès la deuxième suspension : un seul
-- timestamp (sla_suspended_since) ne peut porter qu'une suspension ouverte à la fois et
-- perd la durée déjà écoulée d'une suspension précédente. Ce cumul porte les intervalles
-- déjà clos.
--
-- V1-V3 sont déjà appliquées sur une base existante : ce correctif passe par une nouvelle
-- migration plutôt qu'une réécriture de V2.

alter table steps
    add column suspend_sla boolean not null default false;

alter table requests
    add column sla_suspended_minutes integer not null default 0;

-- ADR-09 : le calendrier ouvré est une extension hors socle (§6.7). Aucune ligne n'existe
-- encore dans sla (aucun moteur de calcul SLA n'est implémenté à ce stade), donc seul le
-- défaut des futures insertions change ici.
alter table sla
    alter column use_business_calendar set default false;
