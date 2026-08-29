-- V7 : limitation des tentatives de connexion (ADR-13, docs/DECISIONS.md).
--
-- Ancrage : §13 ("Vol de mot de passe... limitation des tentatives"). ADR-13 pose le
-- mécanisme : un compteur et un verrou portés par le compte lui-même (pas par IP), lus et
-- écrits par crosscutting/security/LoginAttemptListener. Les seuils (nombre de tentatives,
-- durée de verrouillage) restent administrables via system_parameters
-- (security.login.max-attempts, security.login.lockout-minutes), pas des colonnes ici.

alter table users
    add column failed_login_attempts integer not null default 0,
    add column locked_until timestamptz;
