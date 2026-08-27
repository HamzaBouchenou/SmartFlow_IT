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
