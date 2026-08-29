# Journal des décisions d'architecture — SmartFlow IT

Ce fichier est la source de vérité pour toute décision que le cahier des charges laisse
ouverte. Le cahier des charges (`docs/Cahier_des_charges_PFA_SmartFlow.pdf`) reste
prioritaire : un ADR ne peut trancher qu'une ambiguïté, jamais contredire un chapitre.

Une décision consignée ici devient un ancrage valide au sens de la « règle numéro un » de
`CLAUDE.md` : elle autorise l'implémentation correspondante. Tant qu'une décision n'est pas
écrite ici, le code qui en dépend ne doit pas être écrit.

La numérotation est stable et ne se réutilise pas. Les numéros absents correspondent à des
décisions identifiées pendant la conception mais pas encore rédigées.

---

## ADR-01 — Authentification par session serveur

**Statut** : accepté — 25/08/2026
**Chapitres concernés** : §11.2 (« ouvrir une session ou obtenir un jeton selon
l'architecture retenue »), §13 (ligne « XSS / CSRF » : *stratégie adaptée au mode
d'authentification*)

### Contexte

Le §11.2 laisse explicitement le choix ouvert entre session et jeton. Ce choix n'est pas un
détail d'implémentation : il détermine la configuration Spring Security, la stratégie CSRF,
la façon dont le front-end transporte l'identité, et l'existence ou non d'un stockage de
jetons de rafraîchissement.

### Décision

**Session serveur portée par un cookie.** Spring Security en configuration `SessionCreationPolicy.IF_REQUIRED`,
cookie de session `HttpOnly`, `Secure` et `SameSite=Lax`. Aucun jeton JWT n'est émis.

Conséquences directes :

- **CSRF activé.** Un cookie est envoyé automatiquement par le navigateur : la protection
  CSRF de Spring Security est obligatoire, elle n'est pas optionnelle comme elle l'aurait
  été avec un en-tête `Authorization` porté par du JavaScript. Jeton CSRF exposé au
  front-end via `CookieCsrfTokenRepository.withHttpOnlyFalse()`.
- **Pas de table de jetons.** `refresh_tokens`, créée en `V2` puis supprimée en `V3`,
  n'a pas à être recréée. Aucune migration n'est nécessaire pour appliquer cet ADR.
- **Révocation immédiate.** Invalider la session côté serveur suffit à couper l'accès ;
  il n'y a pas de fenêtre de validité résiduelle d'un jeton déjà émis.
- **Front-end et back-end servis sous la même origine** via le reverse proxy du
  `docker-compose.yml`, pour que le cookie soit envoyé sans configuration CORS permissive.

### Raisons

Sur un projet de quatre semaines, le JWT stateless ajoute du code de sécurité à écrire et à
tester — rotation du refresh, révocation, expiration, stockage côté client — pour un
bénéfice (scalabilité horizontale sans état partagé) dont le projet n'a aucun usage : une
seule instance back-end, déploiement par Docker Compose. Ce code supplémentaire est
précisément le genre de code où une erreur devient une faille, et le §3.4 demande des tests
d'autorisation, pas une infrastructure de jetons.

Le §13 n'impose ni l'un ni l'autre : il demande une stratégie *adaptée au mode
d'authentification retenu*. La session avec CSRF activé est cette stratégie.

### Ce que cet ADR n'autorise pas

Le stockage du mot de passe reste soumis au §13 (« hachage robuste, politique minimale,
limitation des tentatives ») : `users.password_hash` est alimenté par BCrypt, et la
limitation des tentatives fait l'objet d'une décision distincte non encore rédigée.

---

## ADR-02 — Format de la référence de demande

**Statut** : accepté — 27/08/2026
**Chapitres concernés** : §6.4 (« Génération d'une référence unique lisible, par exemple
DEM-2026-000123 »), RG-01 (« Référence unique et non réutilisable »)

### Contexte

Le §6.4 ne donne la forme `DEM-2026-000123` qu'à titre d'exemple (« par exemple ») : ni le
format exact, ni le comportement de la partie numérique d'une année sur l'autre (repart-elle
à zéro ou continue-t-elle ?) ne sont tranchés. RG-01 est en revanche explicite sur la
contrainte qui prime sur toute mise en forme : « Séquence PostgreSQL + contrainte UNIQUE.
Jamais `count(*) + 1` ».

### Décision

Format : `DEM-{année}-{numéro sur 6 chiffres}`, où `{numéro}` vient de
`nextval('request_reference_seq')` (séquence PostgreSQL créée en V2) et `{année}` est
l'année en cours au moment de l'appel, lue sur le `Clock` injecté
(`crosscutting/config/ClockConfig`), jamais `Instant.now()` en dur.

**La séquence ne se réinitialise jamais par année.** `{année}` est purement informatif dans
la référence affichée ; le numéro qui la suit continue de progresser sans interruption
d'une année sur l'autre. Une demande créée le 31/12/2026 et une autre le 01/01/2027 reçoivent
donc des numéros consécutifs malgré le changement de préfixe.

**La référence est attribuée à la création du brouillon**, pas à la soumission : le schéma
(`requests.reference` `not null unique` depuis V2) impose qu'une ligne `requests` porte déjà
une référence avant même d'être soumise, et une valeur différée (vide, placeholder) n'aurait
aucun sens le temps du brouillon.

### Raisons

Réinitialiser le compteur chaque année est la source d'ambiguïté que RG-01 cherche
justement à éviter : cela exige soit une séquence par année (recréée par une migration au
changement d'année, ce qui n'est pas automatisable proprement dans Flyway), soit une remise
à zéro manuelle risquant une collision transitoire. Une séquence globale et continue
garantit l'unicité et la non-réutilisation de RG-01 pour toujours, par construction, sans
aucune logique supplémentaire à tester - le préfixe `{année}` reste lisible pour un humain
(objectif du §6.4) sans conditionner la garantie d'unicité dessus.

### Ce que cet ADR n'autorise pas

Le format ne s'applique qu'à `Request.reference`. Il ne préjuge d'aucun format équivalent
pour d'autres identifiants lisibles que le CDC pourrait introduire ailleurs (numéro de
pièce jointe, de commentaire, etc.) - une décision distincte s'appliquerait à eux si le
besoin se présentait.

---

## ADR-03 — Seule CLOSE clôture une demande ; le statut ne suit pas chaque étape

**Statut** : accepté — 27/08/2026
**Chapitres concernés** : §6.5 (« Actions possibles selon l'étape : valider, rejeter,
retourner, affecter, demander un complément ou clôturer »), §2.1 (« le produit sera conçu de
manière suffisamment générique »)

### Contexte

`RequestStatus` (DRAFT, SUBMITTED, CLOSED, CANCELLED, ARCHIVED) est un petit ensemble fixe
de statuts globaux, alors que les étapes d'un workflow (`Step`) sont entièrement
configurables par type de demande. Le CDC ne dit pas explicitement si exécuter VALIDATE,
REJECT, RETURN ou REQUEST_INFO doit changer `Request.status`, ni si un statut dédié existe
pour une demande rejetée (il n'y en a pas dans le modèle de données du §10). `Transition`
elle-même documente déjà : « toStep is nullable: CLOSE is a terminal action with no target
step » - un indice explicite, mais seulement pour CLOSE.

### Décision

**Seule l'action CLOSE fait passer `Request.status` à `CLOSED`** (et fige `closedAt`,
`closureReason`, `closureSolution`, `currentStep = null`). CLOSE n'a jamais d'étape cible :
`to_step_id` doit être `null` sur toute `Transition` dont l'action est CLOSE - l'exécuteur
de transition le traite comme un invariant, pas comme une possibilité parmi d'autres.

**Toutes les autres actions (VALIDATE, REJECT, RETURN, ASSIGN, REQUEST_INFO) se contentent
de déplacer `currentStep` vers l'étape que désigne la Transition résolue ; `Request.status`
reste `SUBMITTED`.** Une demande rejetée n'obtient donc pas de statut dédié : elle reste
"en cours" au sens du statut global, simplement parquée sur l'étape que l'administrateur du
workflow a configurée comme cible de REJECT (typiquement une étape terminale sans
transition sortante, cf. le jeu de données de démonstration V5 - étape "REJETEE"). Rien
n'empêche un administrateur de configurer cette étape avec une transition sortante propre
(par exemple une nouvelle CLOSE) si un processus donné veut clôturer explicitement après
rejet.

### Raisons

Faire porter à l'ENGINE la connaissance que « REJECT signifie clos » serait exactement le
`if (action == REJECT)` que CLAUDE.md interdit dans le moteur de workflow (« la
configurabilité du §2.1 meurt là ») : deux types de demande pourraient vouloir des suites
différentes après un rejet (l'un clôture immédiatement, l'autre notifie et attend une
correction). Garder `RequestStatus` comme un résumé grossier du cycle de vie - jamais
recalculé depuis l'étape courante, toujours écrit explicitement par l'action qui le change
- respecte aussi l'esprit de RG-07 pour SLA : SlaSweepScheduler ne retouche que les demandes
`SUBMITTED`, donc une demande CLOSED sort proprement du balayage dès que ce statut est posé,
sans condition supplémentaire à écrire.

### Ce que cet ADR n'autorise pas

Il ne crée pas de statut REJECTED, ni aucune autre valeur `RequestStatus` dérivée d'une
étape ou d'une action : `RequestStatus` reste la liste fermée déjà actée par le modèle de
données du §10. Il ne dit rien non plus de la réouverture (RG-08) ni de l'archivage
(RG-12), hors périmètre de cet ADR.

---

## ADR-09 — Le calendrier ouvré est hors du socle

**Statut** : accepté — reconstruit a posteriori depuis la migration `V4`
**Chapitre concerné** : §6.7 (« calcul des échéances à partir de la date de soumission ;
calendrier ouvré **en extension** »)

### Contexte

`sla.use_business_calendar` existe en base depuis `V2`. Le §6.7 qualifie lui-même le
calendrier ouvré d'extension, par opposition au calcul d'échéance à partir de la date de
soumission qui, lui, fait partie du socle.

### Décision

Le calcul SLA du socle est en **temps calendaire continu**. `SlaCalculator` ne connaît ni
jours ouvrés, ni heures d'ouverture, ni jours fériés : il additionne des durées.

La colonne `sla.use_business_calendar` est conservée — le modèle de données du §10 la
prévoit et `RG-12` interdit de détruire des chemins de lecture — mais son défaut est passé
à `false` en `V4`, et **aucun code ne la lit**. Elle documente une extension future sans
créer de branche morte dans le moteur de calcul.

### Raisons

Un calendrier ouvré correct suppose un référentiel de jours fériés, des plages horaires par
service et une arithmétique d'intervalles qui n'est pas triviale à tester. Le §6.7 ne le
demande pas dans le socle. L'implémenter à moitié produirait un calcul faux, ce qui est pire
qu'une extension assumée : le §3.4 fait du calcul SLA une priorité de couverture de tests,
et on ne teste correctement que ce qu'on a décidé de faire entièrement.

### Conséquence sur le code existant

`SlaCalculator` et `SlaSuspensionRule` sont conformes à cet ADR : ils manipulent des
`Instant` et des `Duration` sans notion de calendrier. Le jour où l'extension est reprise,
elle passe par une nouvelle règle dans `domain/rule`, pas par un `if` dans `SlaCalculator`.

---

## ADR-10 — Visibilité d'une demande par un rôle complémentaire (RG-06)

**Statut** : accepté — 27/08/2026
**Chapitres concernés** : RG-06 (« Un demandeur ne peut consulter que ses dossiers, sauf
rôle complémentaire prévu par l'organisation »), §5.1, §6.6, §9.4, §13.1

### Contexte

`RequestService.getDetail` n'autorisait la lecture de `GET /api/v1/requests/{id}` qu'à la
requérante elle-même, sans distinction avec `updateDraft`/`submit`/`cancel`, qui doivent
eux rester strictement réservés au demandeur. Un manager, un agent ou un responsable de
service pouvait déjà exécuter une transition sur une demande hors de ses propres dossiers
(`WorkflowTransitionService.execute` vérifie `canAct`, jamais la propriété) mais ne pouvait
jamais consulter cette même demande au préalable pour décider quoi faire : l'écran détail
(§9.4) et le scénario REC-03 échouaient en 404 pour tout rôle autre que REQUESTER. RG-06
anticipe pourtant explicitement ce cas (« sauf rôle complémentaire prévu par
l'organisation ») sans que rien ne l'implémente - constaté en testant manuellement le
parcours manager après connexion, pas dans le cahier des charges lui-même.

### Décision

Une nouvelle décision, `AuthorizationService.canView(actingUser, request)`, distincte de
`canAct` : elle réutilise uniquement `perimetreCouvre` (`ScopeRule`), jamais
`roleAccordePermission`, `etapeAutoriseAction` ni `separationDesTaches` - ces trois clauses
gouvernent la légalité d'une **action**, pas la visibilité d'un **enregistrement**.
`GET /api/v1/requests/{id}` (`RequestService.getDetail`) autorise la lecture quand
l'appelant est la requérante (RG-06, inchangé) **ou** quand `canView` est vrai pour au
moins une de ses affectations de rôle.

**Une demande à l'état DRAFT reste visible uniquement par sa requérante**, quel que soit le
périmètre d'un rôle complémentaire : `canView` renvoie systématiquement `false` pour un
brouillon, par construction plutôt que par discipline d'appel. `updateDraft`, `submit` et
`cancel` (`RequestService.getOwned`/`getOwnedDraft`) ne changent pas : ils restent
strictement réservés au demandeur, `canView` ne s'applique qu'à la lecture.

### Raisons

`ScopeRule.covers` résout `DEPARTMENT`/`DIRECTION` depuis le service de la demande, une
donnée déjà présente dès la création du brouillon (`RequestType.ServiceCatalog.Department`)
- sans la garde DRAFT, un responsable de service verrait le brouillon d'un collaborateur
avant même sa soumission, ce qu'aucune ligne du CDC ne demande et que RG-06 (un brouillon
reste privé jusqu'à soumission, §6.4) exclut implicitement. Une fois soumise, une demande
est précisément ce que §6.6 (file d'équipe), §9.4 (écran détail pour tous les rôles) et
§13.1 (journal consultable) supposent déjà visible au-delà du seul demandeur - la garde ne
s'applique donc qu'à l'état DRAFT, pas à CLOSED/CANCELLED/ARCHIVED, pour ne pas casser la
consultation d'historique que RG-12 exige de préserver.

Une décision plus étroite (limiter `canView` aux seuls rôles qui apparaissent aussi dans
`RolePermissionRule`) exclurait AUDITOR - dont le §5 dit explicitement « lecture seule sur
périmètre autorisé », donc zéro action de workflow mais une visibilité réelle - et
imposerait une deuxième matrice de permissions rien que pour la lecture. Faire dépendre
`canView` du seul périmètre, indépendamment du rôle, couvre ce cas sans code supplémentaire
et reste cohérent avec la façon dont `UserRoleAssignment.scopeType/scopeId` représente déjà
« le rôle complémentaire prévu par l'organisation » que RG-06 appelle de ses vœux.

### Ce que cet ADR n'autorise pas

Il ne change rien à la légalité d'une action (`canAct` reste inchangé), ni aux garanties de
propriété de `updateDraft`/`submit`/`cancel`. Il ne dit rien non plus de la liste « Mes
tâches »/« file d'équipe » (`TaskQueueService`), dont le filtrage par affectation active
reste plus étroit que `canView` et n'est pas modifié ici - une demande visible via
`canView` peut très bien ne jamais apparaître dans une file de travail avant sa prise en
charge explicite.

---

## ADR-11 — Qui peut lire et écrire un commentaire ou une pièce jointe

**Statut** : accepté — 27/08/2026
**Chapitres concernés** : §6.4 (« Ajout de commentaires, mentions et pièces jointes
autorisées »), §9.4 (l'écran détail regroupe « commentaires... et pièces jointes » aux
côtés des données et du statut), §5 (tableau des acteurs — l'Auditeur consulte « sans
modifier les données »), §5.1 (« L'accès à une pièce jointe doit respecter les mêmes règles
que l'accès à la demande concernée »)

### Contexte

§6.4 introduit les commentaires et pièces jointes comme des fonctions de la gestion des
demandes, sans préciser qui peut en ajouter. `AuthorizationService` n'expose que deux
décisions : `canAct` (une `WorkflowAction`, bornée à une étape de workflow — commenter n'en
est pas une) et `canView` (lecture seule d'une demande, ADR-10). Aucune des deux ne
répond à « qui peut écrire un commentaire ou une pièce jointe ».

### Décision

**Lecture** : identique à `getDetail` (ADR-10) — la requérante, ou tout rôle dont `canView`
couvre la demande. §9.4 range explicitly les commentaires et pièces jointes dans le même
écran détail que le reste du dossier ; il n'y a pas de raison de leur appliquer une règle de
lecture plus étroite que celle de la demande elle-même, y compris pour l'AUDITOR (§5 —
lecture seule sur périmètre autorisé, donc lecture des commentaires comprise).

**Écriture** : une nouvelle décision, `AuthorizationService.canAnnotate(actingUser,
request)`, distincte de `canView` :

```
canAnnotate(u, r) = (r.requester == u)                          // toujours, y compris DRAFT
                  OU (canView(u, r) ET aucune affectation de u sur r n'a le rôle AUDITOR)
```

La requérante peut toujours commenter/joindre un fichier à son propre dossier, y compris en
brouillon (§6.3 modélise déjà les pièces jointes de formulaire séparément ; celles-ci sont
la conversation/pièces jointes libres de §6.4, qui n'a pas de raison d'attendre la
soumission). Tout autre rôle suit `canView` — donc au minimum le même périmètre que la
lecture de la demande — sauf l'AUDITOR, explicitement exclu.

### Raisons

Le tableau des acteurs (§5) est la seule ligne du CDC qui tranche explicitement un cas :
l'Auditeur/direction « consulte des indicateurs et traces **sans modifier les données** ».
Écrire un commentaire ou une pièce jointe est une modification de la demande au même titre
qu'une transition de workflow ; laisser `canView` seul gouverner l'écriture donnerait à
l'Auditeur un droit que le CDC lui refuse explicitement dans la même phrase qui définit son
rôle. Exclure uniquement AUDITOR, plutôt que de construire une nouvelle matrice
Role → autorisation dupliquant `RolePermissionRule` (qui ne connaît que les
`WorkflowAction`, pas « commenter »), garde la décision à une ligne, testable seule, sans
réintroduire le risque que CLAUDE.md interdit (« Ne disperse jamais ces règles »).

### Ce que cet ADR n'autorise pas

Il ne crée pas de permission par rôle pour les commentaires/pièces jointes au-delà de
l'exclusion d'AUDITOR : un MANAGER, un AGENT ou un SERVICE_MANAGER dont le périmètre couvre
la demande peut toujours annoter, que son rôle figure ou non dans `RolePermissionRule` pour
une `WorkflowAction` donnée. Il ne dit rien du contenu contrôlé côté serveur d'une pièce
jointe (RG-09, §13 — extension, taille, type MIME, stockage), qui est une règle séparée,
directement dictée par le CDC et ne nécessitant pas d'arbitrage.

---

## ADR-12 — Quels types de notification sont obligatoires (§6.8)

**Statut** : accepté — 27/08/2026
**Chapitres concernés** : §6.7 (« notification avant échéance et escalade au responsable »),
§6.8 (« Préférences de notification limitées pour éviter la désactivation des alertes
obligatoires »)

### Contexte

`NotificationPreference` existe depuis le socle avec un commentaire explicite renvoyant la
question à plus tard : « Which types are mandatory and therefore ignore this preference is
a domain/rule decision, not a column here. » §6.8 exige qu'au moins une catégorie
d'alertes reste obligatoire, sans dire laquelle.

### Décision

Une règle pure, `domain/rule/MandatoryNotificationRule`, ferme la liste :
**`SLA_WARNING` et `SLA_BREACH` sont obligatoires** — envoyées par e-mail quelle que soit
`NotificationPreference.emailEnabled` pour ce type et cet utilisateur. Tous les autres
types (`SUBMISSION`, `ASSIGNMENT`, `INFO_REQUESTED`, `DECISION`, `CLOSURE`) respectent la
préférence de l'utilisateur ; en l'absence de préférence enregistrée, le défaut est envoyé
(`NotificationPreference.emailEnabled` vaut `true` par défaut à la création — désactiver est
un choix explicite, pas l'absence de ligne).

La notification **en centre applicatif** (`Notification`, statut lu/non lu) n'est en
revanche jamais soumise à cette préférence : elle ne concerne que l'envoi d'un e-mail
(§6.8 — « Préférences de notification limitées » qualifie l'e-mail, pas le centre
lui-même, qui reste la source de vérité consultable dans l'application).

### Raisons

§6.7 est la seule ligne du CDC qui qualifie une notification de non-désactivable en
pratique : une « escalade au responsable en cas de dépassement » perd tout son sens si le
responsable a pu la couper. Les cinq autres types (soumission, affectation, complément,
décision, clôture) sont des convenances de suivi — utiles, mais leur désactivation ne casse
aucune garantie du cahier des charges. Limiter la liste obligatoire à SLA_WARNING/
SLA_BREACH est donc le plus petit ensemble qui satisfait §6.7 sans réduire à néant
l'objectif de personnalisation que §6.8 énonce dans la même phrase (« limitées », pas
« aucune »).

### Ce que cet ADR n'autorise pas

Il ne dit rien du contenu ou du déclenchement des événements `SLA_WARNING`/`SLA_BREACH`
eux-mêmes (lot S8, balayage SLA) — uniquement du fait que, une fois émis, leur e-mail
ignore la préférence utilisateur. Il ne change pas non plus le comportement des cinq autres
types déjà cité.

---

## ADR-13 — Limitation des tentatives de connexion (§13)

**Statut** : accepté — 28/08/2026
**Chapitres concernés** : §13 (« Vol de mot de passe : hachage robuste, politique minimale,
**limitation des tentatives** et réinitialisation sécurisée »), §6.1 (authentification)

### Contexte

`SmartFlowUserDetails.isAccountNonLocked()` renvoie toujours `true`, avec un commentaire
explicite renvoyant la décision à plus tard. §13 exige une limitation des tentatives sans
en préciser le mécanisme, le seuil, ni la durée de verrouillage.

### Décision

**Compteur et verrou portés par le compte, pas par l'adresse IP.** Deux colonnes
supplémentaires sur `users` : `failed_login_attempts` (entier, défaut 0) et `locked_until`
(horodatage, nullable). Deux `SystemParameter` administrables (§6.10 - « seuils
d'alerte ») : `security.login.max-attempts` (défaut `"5"`) et
`security.login.lockout-minutes` (défaut `"15"`), lues avec le même défaut-si-absent que
`AuthorizationService.SEPARATION_OF_DUTIES_KEY`.

Sur un échec d'authentification (mot de passe erroné, compte existant) :
`failed_login_attempts` s'incrémente ; à `max-attempts` atteint, `locked_until` se pose à
`maintenant + lockout-minutes` et le compteur repart à zéro. Sur une authentification
réussie, les deux colonnes se réinitialisent (`0`, `null`). Tant que `locked_until` est
dans le futur, `isAccountNonLocked()` renvoie `false` : Spring Security lève une
`LockedException`, une sous-classe d'`AuthenticationException` que
`GlobalExceptionHandler.handleAuthenticationException` traduit déjà en le même message
générique « Identifiants invalides » que n'importe quel autre échec — un compte verrouillé
ne se distingue jamais d'un mot de passe incorrect côté réponse HTTP.

**Mécanisme d'incrémentation : un `ApplicationListener` Spring Security standard**
(`AuthenticationSuccessEvent`, `AuthenticationFailureBadCredentialsEvent`), pas un
`try/catch` dans `AuthController` — ce dernier ne verrait plus les tentatives si §6.1's
option Azure AD/Entra ID ajoute un jour un second point d'entrée d'authentification, alors
que les événements Spring Security, eux, restent émis quel que soit le
`AuthenticationProvider` qui a échoué.

### Raisons

Un seuil et une durée codés en dur seraient un choix arbitraire non documenté et non
ajustable sans redéploiement ; `SystemParameter` reprend exactement le mécanisme déjà
éprouvé pour la séparation des tâches et les pièces jointes (RG-09), pas une nouvelle
infrastructure de configuration. Compter par compte plutôt que par IP : un attaquant
distribué sur de nombreuses adresses reste throttled à l'identique (c'est le compte visé
qui compte), et un utilisateur légitime derrière une IP partagée (NAT d'entreprise) n'est
jamais bloqué à cause des échecs d'un tiers. Deux colonnes mutables sur `users` plutôt
qu'une table séparée d'historique : c'est un compteur d'état courant propre à ce compte,
pas un journal à conserver — RG-11 n'exige la traçabilité que pour « rôle, statut,
affectation, configuration », pas pour chaque tentative de connexion individuellement, donc
rien n'impose une ligne `AuditLog` par échec.

### Ce que cet ADR n'autorise pas

Il ne tranche pas la « réinitialisation sécurisée » du mot de passe (§13 la cite dans la
même phrase) — décision distincte, non écrite ici. Il n'introduit ni CAPTCHA ni
ralentissement progressif (« exponential backoff ») : un verrouillage à durée fixe est le
mécanisme le plus simple qui satisfait « limitation des tentatives », hors périmètre
d'aller au-delà pour un PFA de quatre semaines. Il ne journalise pas non plus chaque
tentative individuelle dans `audit_log` — seul le compteur/verrou lui-même est un état
mutable sur `users`, pas un flux d'événements.

---

## ADR-14 — Fenêtre de réouverture RG-08

**Statut** : accepté — 28/08/2026
**Chapitres concernés** : RG-08 (« Une demande clôturée peut être rouverte pendant une
durée paramétrable si le service le permet »), §6.5, ADR-03

### Contexte

`requests.reopen_deadline` existe en base depuis `V2` mais n'est posé par aucun code
(constaté dans l'état actuel du dépôt, `CLAUDE.md`). ADR-03 a déjà tranché qu'une demande
`CLOSED` a `currentStep = null` et qu'aucune `WorkflowAction` n'existe pour la faire
revivre : `WorkflowActionAvailabilityRule` refuse tout sur une demande sans étape courante,
donc RG-08 est aujourd'hui structurellement impossible, pas seulement non implémentée.
RG-08 elle-même laisse deux choses ouvertes : ce que « le service le permet » désigne
précisément, et où une demande rouverte reprend son circuit.

### Décision

**« Le service le permet » = une nouvelle colonne `request_types.reopen_allowed boolean not
null default true`.** RequestType est déjà le grain de configuration de tout le reste du
circuit (SLA par type+priorité, `WorkflowDefinition`, `FormDefinition` sont tous portés par
`RequestType`, jamais par `ServiceCatalog` ni par un paramètre global) — RG-08 rejoint ce
même niveau plutôt que d'en introduire un nouveau.

**Une nouvelle valeur `WorkflowAction.REOPEN`.** `WorkflowTransitionService.closeRequest`
pose désormais `reopenDeadline = closedAt + durée paramétrable` (nouvelle clé
`SystemParameter` : `requests.reopen-window-days`, défaut `"30"`). REOPEN n'est légal que
si les trois conditions tiennent : `status = CLOSED`, `now <= reopenDeadline`,
`requestType.reopenAllowed = true` — une garde dédiée évaluée par `RequestService` (à la
manière de `getOwnedDraft`'s garde d'état), **avant** `canAct`, puisque
`WorkflowActionAvailabilityRule` ne sait raisonner que sur les `Transition` d'une étape
courante, qu'une demande `CLOSED` n'a plus.

**Rouvrir renvoie `currentStep` à l'étape précédant CLOSE** — le `fromStep` de la ligne
`RequestHistory` la plus récente dont `action = CLOSE` pour cette demande — et `status`
repasse à `SUBMITTED`. Aucune notion séparée d'« étape de réouverture » configurable : le
circuit reprend exactement où l'agent l'avait laissé, sans nouveau champ de configuration
workflow. Une nouvelle ligne `RequestHistory` (`action = REOPEN`, `fromStep = null`,
`toStep` = l'étape reprise) trace la réouverture, RG-04/§3.4 obligent déjà une ligne par
transition.

**Qui peut rouvrir** : la requérante elle-même (symétrique à ADR-11 — « ce n'est pas
vraiment résolu », la même légitimité que commenter/joindre un fichier à son propre
dossier) ou tout rôle qui pourrait déjà exécuter CLOSE sur cette demande (`AGENT`/
`SERVICE_MANAGER` dans son périmètre, `RolePermissionRule` inchangée par ailleurs) —
toujours borné par `reopenAllowed`/`reopenDeadline` en plus du rôle et du périmètre.

### Raisons

Ancrer « le service » sur `RequestType` plutôt que sur `ServiceCatalog` ou un
`SystemParameter` global évite une deuxième granularité de configuration workflow à
maintenir en parallèle de celle qui existe déjà pour SLA/formulaire/circuit. Réutiliser le
`fromStep` de l'historique plutôt qu'une étape de réouverture configurable séparément évite
d'ajouter une colonne à `Step` pour un besoin que RG-08 ne demande pas explicitement — le
CDC ne dit jamais qu'une réouverture doive recommencer ailleurs que là où le dossier a été
laissé. Autoriser la requérante à rouvrir, comme pour ADR-11, reflète le même
raisonnement : c'est son dossier, et rouvrir n'est pas plus une atteinte à la séparation
des tâches que commenter ne l'est.

### Ce que cet ADR n'autorise pas

Il ne dit rien d'une réouverture au-delà de `reopenDeadline`, même par un administrateur
fonctionnel — RG-08 dit « pendant une durée paramétrable », pas « sauf dérogation », et
aucune ligne du CDC ne demande un contournement. Il ne modifie pas `RolePermissionRule`
au-delà de faire de REOPEN un alias d'éligibilité de CLOSE : un rôle qui ne pouvait pas
clôturer une demande ne peut pas non plus la rouvrir. Il ne construit aucun code — seule la
décision est actée ici ; l'implémentation (migration, `WorkflowAction.REOPEN`,
`RequestService`/`WorkflowTransitionService`, tests) reste un lot à part.

---

## ADR-15 — Politique d'archivage RG-12

**Statut** : accepté — 28/08/2026
**Chapitres concernés** : RG-12 (« Les données archivées restent consultables selon la
politique de conservation retenue par l'entreprise »), §6.10 (« Archivage logique des
référentiels utilisés »), §10.1 (« Suppression logique des référentiels déjà utilisés »)

### Contexte

RG-12 couvre en réalité deux choses distinctes que le CDC ne sépare pas explicitement :
l'archivage des **référentiels** (catalogue, types de demande, équipes, services...) et
celui des **demandes** elles-mêmes. `RequestStatus.ARCHIVED` existe dans l'énumération
(citée dans ADR-03) mais aucun code ne le pose jamais. « La politique de conservation
retenue par l'entreprise » est explicitement laissée ouverte par le CDC lui-même — ce n'est
pas une ambiguïté à trancher entièrement, mais un paramètre à rendre configurable.

### Décision

**Volet référentiels : déjà satisfait, rien à construire.** `active` existe déjà sur
`departments`, `teams`, `service_catalog`, `request_types` (V2) et CLAUDE.md interdit déjà
tout `DELETE` exposé. Un référentiel désactivé reste en base, donc consultable par toute
demande qui le référence déjà (clé étrangère intacte) — c'est exactement « suppression
logique... afin de préserver l'historique » (§10.1, §6.10). Cet ADR ne fait que le
constater : aucune migration, aucun code nouveau pour ce volet.

**Volet demandes : une politique de rétention configurable, appliquée par un nouveau
balayage planifié.** Nouvelle clé `SystemParameter` : `requests.archive-after-months`
(défaut `"24"`). Un nouveau composant `infrastructure/scheduler` (même forme que
`SlaSweepScheduler`) fait passer `status` de `CLOSED`/`CANCELLED` à `ARCHIVED` quand
`closedAt` (ou, à défaut de clôture, `updatedAt` pour une demande annulée) dépasse ce
délai.

**« Restent consultables » : aucun changement d'accès requis.**
`AuthorizationService.canView` (ADR-10) n'exclut déjà que `DRAFT` — une demande `ARCHIVED`
reste visible par quiconque son périmètre couvre déjà, exactement comme une `CLOSED`.
`DashboardService` filtre déjà seulement `status != DRAFT` : une demande archivée continue
d'apparaître dans les volumes et l'export CSV (§10.1 — « chemins de lecture conservés »).
Aucun de ces deux composants n'a besoin d'un cas `ARCHIVED` spécifique : ADR-10 et la
lecture de S8 couvrent déjà ce statut par construction.

**Une demande `ARCHIVED` n'offre plus aucune action de workflow** (comme `CLOSED`,
`currentStep` reste `null`) **et n'est plus réouvrable** — ADR-14/RG-08 exige explicitement
`status = CLOSED`, jamais `ARCHIVED`. `requests.archive-after-months` doit donc toujours
rester strictement supérieur à `requests.reopen-window-days` (ADR-14) pour qu'une demande
n'atteigne jamais l'archivage avant la fin de sa fenêtre de réouverture — contrainte
documentée ici, pas vérifiée en base (deux `SystemParameter` indépendants, une valeur
d'admin incohérente resterait possible mais n'omettrait la réouverture qu'en la retardant
artificiellement tard, jamais en la faisant disparaître avant l'heure).

### Raisons

Séparer les deux volets évite de complexifier le volet référentiel, déjà correct, avec une
politique de rétention qui ne le concerne pas. Un balayage planifié plutôt qu'un archivage
déclenché à la demande reproduit exactement le patron déjà validé par `SlaSweepScheduler`
pour RG-07 — une recomputation périodique d'un statut dérivé, jamais un calcul à la volée
dans une requête de tableau de bord (CLAUDE.md, « Ce qu'il ne faut jamais faire »). Ne rien
changer à `canView`/`DashboardService` est un signe que ADR-10 et le design de S8 étaient
déjà corrects par construction, pas un oubli à combler.

### Ce que cet ADR n'autorise pas

Il ne décide pas d'un archivage manuel déclenché par un administrateur fonctionnel (§6.10
pourrait vouloir cette option en plus) — seul l'archivage automatique par ancienneté est
tranché ici. Il n'introduit aucune purge physique après une durée encore plus longue que
l'archivage : RG-02/RG-12 interdisent justement toute suppression physique d'une demande
soumise, archivée ou non. Il ne construit aucun code — seule la décision est actée ici.

---

## ADR-16 — Intégration du module IA : autorisation, mode désactivé, stockage validé (§12)

**Statut** : accepté — 28/08/2026
**Chapitres concernés** : §12 (« aide à la qualification et à la recherche, ne modifie
aucune donnée sensible sans action explicite »), §12.1/§12.2/§12.3, RG-10, §11.2 (« POST
/api/v1/ai/requests/{id}/analyze — selon les droits »), §13 (« Usage IA non maîtrisé »)

### Contexte

`AiAnalysis`/`AiAnalysisType`/`KnowledgeDocument` existent déjà en base et en entité JPA
(S3-S6) mais rien ne les alimente : ni `infrastructure/ai`, ni le service Flask au-delà de
`/health`. §11.2 qualifie sa seule route documentée de « selon les droits » sans préciser
lesquels ; §12.2 exige un « mode désactivé » réel ; RG-10 exige qu'aucune écriture directe
de l'IA n'atteigne une `Request`, sans dire comment l'API doit matérialiser la validation
humaine qu'elle appelle de ses vœux.

### Décision

**Deux fonctions P1 seulement** (§12.1 les marque prioritaires ; les fonctions P2/Option -
recherche documentaire, suggestion de réponse, détection de tendance - restent hors
périmètre de ce lot) : classification (catégorie + priorité) et résumé.

**Classification : `MlClassifier` (TF-IDF + régression logistique) retenu après comparaison
réelle avec `RuleBasedClassifier`** (§12.2 l'exige explicitement) — 100 % d'exactitude
contre 82 %/81 % sur le même jeu de test séparé, détail dans `ai-service/EVALUATION.md`.
`RuleBasedClassifier` reste dans le code et testé, comme trace de la comparaison exigée, pas
supprimée une fois la décision prise. Entraîné une fois au démarrage du process Flask sur
`ai-service/data/training_data.csv` (généré par gabarit, §12.2 - « exemples anonymisés ou
générés »), pas d'artefact binaire séparé à maintenir.

**Résumé : extractif, local, en pur Python** (`ai-service/summarization.py`) — sélectionne
les phrases sources les plus représentatives (fréquence de mots hors mots vides) plutôt que
d'en générer de nouvelles, ce qui rend « absence d'information inventée » (§12.3) vraie par
construction, sans modèle à télécharger ni service externe à appeler (§12.2 - « point de
vigilance », donnée interne jamais envoyée hors du périmètre applicatif). Entrée : la
description de la demande plus ses 5 derniers commentaires (§12.1 - « et de ses derniers
échanges »).

**Mode désactivé : un vrai coupe-circuit côté Spring, jamais côté Flask.**
`infrastructure/ai/AiClient` lit `SMARTFLOW_AI_ENABLED` (déjà anticipée par
`docker-compose.yml`, propriété `smartflow.ai.enabled`) et refuse **avant tout appel
réseau** (`InvalidRequestStateException("AI_DISABLED", ...)`) plutôt que de laisser un appel
échouer contre un service arrêté — « l'application principale doit rester utilisable sans le
service IA » (§12.2) se lit ici comme une garde explicite, pas comme une tolérance de
panne. Un appel réussi mais en échec réseau/HTTP distinct (`AI_SERVICE_UNAVAILABLE`) reste
possible et distinct du refus délibéré.

**Autorisation : lecture par `canView` (ADR-10), écriture par `canAnnotate` (ADR-11).**
« Selon les droits » (§11.2) se lit comme une nouvelle occurrence des deux mêmes gardes déjà
posées pour les commentaires/pièces jointes, pas une troisième matrice de permissions :
consulter les analyses déjà produites suit le même accès que le reste de l'écran détail
(§9.4 - un résumé ou une suggestion de catégorie fait partie du dossier, comme un
commentaire) ; **demander** une nouvelle analyse ou **valider** une analyse existante sont
toutes deux des écritures (une nouvelle ligne `AiAnalysis`, ou `acceptedValue`/
`validatedBy`/`validatedAt` posés sur une ligne existante) et suivent donc `canAnnotate` -
jamais l'AUDITOR, en lecture seule par construction (§5).

**Stockage : une ligne `AiAnalysis` par appel, jamais d'écriture sur `Request`.** Pour
CLASSIFICATION, `suggestedValue` porte un petit JSON `{"category":...,"priority":...}` (une
seule fonction §12.1 produit les deux valeurs ensemble) ; pour SUMMARY, le texte du résumé
lui-même. `validate()` (nouvelle route `POST /api/v1/ai/analyses/{id}/validate`) ne pose que
`acceptedValue`/`validatedBy`/`validatedAt` sur cette même ligne — RG-10 tient par
construction : aucun chemin de code n'écrit `Request.priority`, `RequestType` ni
`RequestFieldValue` depuis `AiAnalysisService`. Appliquer une suggestion validée à la
demande elle-même reste un geste humain explicite via les cas d'usage déjà existants
(`RequestService`/qualification), pas une conséquence automatique de la validation.

### Raisons

Comparer réellement les deux approches de classification, plutôt que d'affirmer un choix,
est ce que §12.2 demande mot pour mot (« Le choix final devra être justifié par les
résultats ») - `RuleBasedClassifier` n'est donc pas du code mort à supprimer après coup : il
est la preuve écrite de la comparaison. Un résumé extractif plutôt que génératif évite
d'un même geste le risque d'invention (§12.3) et la dépendance à un modèle téléchargé ou à
un appel externe que §12.2 met en garde. Refuser côté Spring avant l'appel réseau, plutôt
que de laisser Flask échouer, rend le mode désactivé testable sans dépendre de l'état réel
du service IA - une IT peut le prouver avec `SMARTFLOW_AI_ENABLED=false` seul, sans jamais
démarrer Flask. Réutiliser `canView`/`canAnnotate` plutôt que d'inventer un troisième
niveau de décision garde `AuthorizationService` comme unique point de vérité (CLAUDE.md -
« une seule fonction de décision ») : l'IA n'a pas de règle d'accès qui lui soit propre, elle
hérite de celle du dossier qu'elle assiste.

### Ce que cet ADR n'autorise pas

Il ne construit pas les fonctions P2/Option de §12.1 (recherche documentaire, suggestion de
réponse, détection de tendance) : `KnowledgeDocument` reste non consommé. Il n'autorise
aucun chemin, présent ou futur, qui appliquerait `acceptedValue` à `Request` sans un second
geste humain explicite passant par un cas d'usage déjà soumis à `canAct`/RG-06 - la
validation d'une `AiAnalysis` et la modification de la demande restent deux actions
distinctes, jamais fusionnées pour « gagner un clic ». Il ne dit rien d'un éventuel seuil de
confiance bloquant (§13 l'évoque) : la confiance est affichée, jamais utilisée pour refuser
d'enregistrer une suggestion.

---

## ADR-17 — Administration versionnée des formulaires et des workflows (§6.5/§10.1, RG-03)

**Statut** : accepté — 29/08/2026
**Chapitres concernés** : §6.5 (« Possibilité pour l'administrateur de publier une nouvelle
version du workflow sans modifier les demandes déjà en cours »), §6.3, §10.1
(« Versionnement des définitions de formulaire et de workflow »), RG-03, RG-12, §14.1
(scénario 8 - « Modification d'un workflow publié sans impact sur une demande déjà en
cours »)

### Contexte

`FormDefinition`/`WorkflowDefinition` portent déjà `PublicationStatus` (DRAFT/PUBLISHED/
ARCHIVED) et leur propre javadoc l'annonce depuis S3-S6 : « Once PUBLISHED, its FormField
rows are immutable ; a change publishes a new FormDefinition. » Jusqu'à ce lot, rien ne
l'appliquait - les deux tables n'étaient peuplées que par les migrations Flyway (V5). Le
reste du §6.10 construit jusqu'ici (directions/services, équipes, SLA, gabarits d'e-mail,
utilisateurs) est un CRUD plat avec activation/désactivation logique ; formulaires et
workflows ne peuvent pas suivre ce même patron tel quel, parce que RG-03 impose une
contrainte qu'aucun des écrans précédents n'avait : une demande déjà soumise doit continuer
à lire exactement la version qu'elle a gelée à sa soumission, même après qu'une nouvelle
version a été publiée.

### Décision

**Un brouillon à la fois, publication = archivage de l'ancien plutôt que remplacement.**
Un administrateur fonctionnel peut ouvrir au plus un `FormDefinition`/`WorkflowDefinition`
`DRAFT` par `RequestType` à la fois (créer un second brouillon tant que le premier existe
est refusé - `DRAFT_ALREADY_EXISTS` - pour qu'il n'y ait jamais d'ambiguïté sur lequel un
administrateur modifie). Tant que le statut reste `DRAFT`, ses `FormField`/`FieldOption` ou
`Step`/`Transition` sont librement modifiables (create/update/delete) par ce même service.
**Publier** un brouillon (`POST .../publish`) : (1) refuse un brouillon structurellement
invalide - un workflow sans aucune `Step` ne peut pas être publié, exactement le contrôle
que `RequestService.submit` fait déjà à la soumission, ici déplacé plus tôt pour un retour
immédiat à l'administrateur plutôt qu'à la première tentative de dépôt ; (2) fait passer
l'actuelle version `PUBLISHED` de ce `RequestType`, s'il y en a une, à `ARCHIVED` - jamais
`DRAFT`, jamais supprimée (RG-12, « les données archivées restent consultables ») ; (3) fait
passer le brouillon à `PUBLISHED` et pose `publishedAt`. Une version `PUBLISHED` ou
`ARCHIVED` redevient immuable : ni ses champs propres, ni ses `FormField`/`Step` ne sont
plus modifiables par ce service, quel que soit l'appelant. Un brouillon jamais publié peut
être supprimé physiquement (il n'a jamais été référencé par une demande) ; une version
publiée ou archivée ne l'est jamais.

**Pourquoi RG-03 tient sans code dédié sur `Request`.** `Request.workflowDefinitionId` est
déjà gelé à la soumission (`RequestService.submit`, RG-03 tel qu'implémenté depuis S3-S6) et
chaque étape suivante (`WorkflowTransitionService`) résout toujours le prochain `Step`/
`Transition` à partir du graphe de *cette* `WorkflowDefinition`-là, jamais de « la version
actuellement publiée ». Publier une v2 archive la v1 sans toucher une seule ligne `Step`/
`Transition` de la v1 : une demande déjà en cours sur la v1 continue de fonctionner à
l'identique après la publication de la v2, parce que rien de ce qu'elle lit n'a changé - la
garantie est une conséquence de l'immutabilité post-publication ci-dessus, pas un mécanisme
séparé. Pour les formulaires, la garantie est plus directe encore : `RequestFieldValue`
référence un `FormField` précis (jamais un `FormDefinition` par son statut courant), et ce
`FormField` n'est jamais modifié ni supprimé une fois publié - aucun `Request.formDefinitionId`
n'a donc jamais été nécessaire.

**Catalogue et types de demande restent hors de ce mécanisme.** `ServiceCatalog`/
`RequestType` n'ont pas de `PublicationStatus` : ce sont des référentiels plats comme
`Department`/`Team` (RG-02/RG-12 - activation/désactivation logique, jamais de suppression
physique), administrés par le même patron CRUD que le reste du §6.10 déjà construit. Rien
dans le §6.2/§10 ne demande de versionner un type de demande lui-même - seuls son
formulaire et son workflow le sont.

**Éditeur structuré, jamais un canevas graphique.** L'écran d'administration d'un workflow
est un tableau d'étapes et un tableau de transitions (mêmes formulaires que le reste du
§6.10), pas un outil de glisser-déposer : §4.2 exclut explicitement un « moteur BPMN complet
comparable à une suite BPM du marché », et un tableau structuré reste testable et cohérent
avec le reste de cette admin sans faire courir ce risque.

### Raisons

Un « brouillon unique à la fois » évite la question, sinon ouverte, de savoir laquelle de
plusieurs ébauches concurrentes un administrateur publierait - une contrainte simple plutôt
qu'un mécanisme de verrouillage ou de fusion, proportionnée à un usage mono-administrateur
typique d'un PFA. Archiver plutôt que supprimer l'ancienne version publiée est la seule
option compatible à la fois avec RG-12 et avec RG-03 : une demande déjà en cours doit
pouvoir continuer à afficher le nom de ses étapes passées (§6.4 - frise d'avancement) bien
après qu'une nouvelle version a pris sa place. Documenter explicitement *pourquoi* RG-03
tient sans mécanisme dédié - plutôt que de coder une garde redondante - garde le principe du
projet : chaque garantie a un seul point de vérité, jamais deux chemins qui pourraient un
jour diverger.

### Ce que cet ADR n'autorise pas

Il n'autorise aucune modification en place d'une `FormDefinition`/`WorkflowDefinition`
`PUBLISHED` ou `ARCHIVED`, ni de ses `FormField`/`FieldOption`/`Step`/`Transition` - une
correction, même mineure, passe toujours par un nouveau brouillon puis une nouvelle
publication. Il n'introduit pas de retour en arrière (« republier une version archivée ») :
publier consiste toujours à créer un nouveau numéro de version, jamais à réactiver un ancien.
Il ne verse aucun contrôle de cohérence sémantique du graphe (accessibilité de toutes les
étapes depuis la première, absence d'impasse) au-delà du seul contrôle explicite ci-dessus
(au moins une `Step`) - une omission plus subtile reste possible et n'est détectée qu'à
l'usage, comme c'était déjà le cas avant ce lot.
