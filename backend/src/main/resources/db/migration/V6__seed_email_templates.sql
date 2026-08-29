-- V6 : modèles d'e-mail par défaut, un par NotificationType (domain/enums/NotificationType).
--
-- Ancrage : §6.8 ("Envoi d'e-mails pour les événements importants : soumission,
-- affectation, demande de complément, décision, retard et clôture") et §6.10 ("modèles
-- d'e-mail" administrables - email_templates existe depuis V2, mais rien n'y était encore
-- inséré). infrastructure/mail/MailService résout un gabarit par son code, qui est
-- exactement NotificationType.name() (application/service/NotificationService) : sans
-- cette migration, tout envoi se contente de journaliser un avertissement et de ne rien
-- envoyer (comportement dégradé volontaire de MailService, pas un bug), ce qui rendrait
-- §6.8 invérifiable de bout en bout.
--
-- SLA_WARNING/SLA_BREACH sont seedés ici aussi, avant que le lot S8 (balayage SLA) ne les
-- émette réellement (ADR-12, docs/DECISIONS.md) : un gabarit administrable n'a pas de
-- raison d'attendre son premier déclenchement pour exister.
--
-- {{reference}}/{{title}} sont les seules variables interpolées par les déclencheurs actuels
-- (RequestService.submit, WorkflowTransitionService.execute) - un futur déclencheur peut en
-- ajouter d'autres sans migration, MailService.interpolate ignore silencieusement une clé de
-- gabarit qu'aucune variable ne fournit.

insert into email_templates (code, subject, body_html) values
    ('SUBMISSION', 'Votre demande {{reference}} a bien été soumise',
     '<p>Bonjour,</p><p>Votre demande <strong>{{reference}}</strong> ({{title}}) a bien été soumise et va être traitée.</p>'),
    ('ASSIGNMENT', 'Une demande vous a été affectée : {{reference}}',
     '<p>Bonjour,</p><p>La demande <strong>{{reference}}</strong> ({{title}}) vous a été affectée.</p>'),
    ('INFO_REQUESTED', 'Complément demandé pour votre demande {{reference}}',
     '<p>Bonjour,</p><p>Un complément d''information est nécessaire pour votre demande <strong>{{reference}}</strong> ({{title}}).</p>'),
    ('DECISION', 'Décision sur votre demande {{reference}}',
     '<p>Bonjour,</p><p>Une décision a été prise sur votre demande <strong>{{reference}}</strong> ({{title}}).</p>'),
    ('SLA_WARNING', 'Échéance proche pour la demande {{reference}}',
     '<p>Bonjour,</p><p>La demande <strong>{{reference}}</strong> ({{title}}) approche de son échéance SLA.</p>'),
    ('SLA_BREACH', 'Échéance dépassée pour la demande {{reference}}',
     '<p>Bonjour,</p><p>La demande <strong>{{reference}}</strong> ({{title}}) a dépassé son échéance SLA.</p>'),
    ('CLOSURE', 'Votre demande {{reference}} a été clôturée',
     '<p>Bonjour,</p><p>Votre demande <strong>{{reference}}</strong> ({{title}}) a été clôturée.</p>');
