-- V8 : réouverture RG-08 (ADR-14, docs/DECISIONS.md).
--
-- Ancrage : RG-08 ("Une demande clôturée peut être rouverte pendant une durée paramétrable
-- si le service le permet"). ADR-14 ancre "le service le permet" au même grain que le
-- reste du circuit (SLA, workflow, formulaire) : RequestType, pas ServiceCatalog.
-- requests.reopen_deadline existe déjà depuis V2 et reste inchangé - seule cette colonne
-- manquait pour que RG-08 soit implémentable.

alter table request_types
    add column reopen_allowed boolean not null default true;
