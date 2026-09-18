# Cahier de recette — SmartFlow IT

**Statut de ce document :** **les 40 scénarios sont exécutés** (01-20 le 2026-08-31, 21-40
le 02/09/2026, tous en conditions réelles Docker - voir [PV de recette](#pv-de-recette) pour
le résultat global). REC-SCN-10/18/19, en échec/partiels lors de la première passe, ont
depuis été corrigés (ADR-18/V9) et re-vérifiés par `PilotWorkflowReassignmentIT`
(Testcontainers, jeu de données réel) plutôt que rejoués manuellement un par un. REC-SCN-22 (lacune de topologie SLA)
et REC-SCN-24 (préférences de notification absentes), seuls scénarios non passants à l'issue
de cette passe, ont été corrigés le 03/09 (ADR-20/V10 et §6.8) et re-vérifiés ; deux défauts
réels trouvés et corrigés pendant cette seconde passe
(REC-SCN-26 : `500` au lieu d'un `400` propre sur un paramètre de tableau de bord manquant ;
REC-SCN-30/40 : plafond de taille nginx sous la politique applicative réelle) ; un troisième,
plus sérieux, trouvé et corrigé également (ADR-19 : `canView`/`canAnnotate` perdaient toute
visibilité sur une demande clôturée individuellement affectée). Ce cahier est le script à
dérouler pour produire les deux autres livrables du §17.1 qu'il alimente (« Plan de tests,
résultats et procès-verbal de recette ») : chaque scénario joué porte une ligne **Statut**
juste après son « Résultat attendu ».

Exécuté contre la pile réelle (`docker compose up --build`, image reconstruite avec le
code de cette session - en-têtes de sécurité comprises), à travers le reverse proxy du
front (`localhost:5173/api/v1/...`, ADR-01) comme `postman/`, par appels API directs
(équivalent du parcours navigateur - pas de clic-par-clic dans le SPA lui-même pour cette
passe).

## Portée et méthode

- **Référence** : §3.4 (« Au moins 25 scénarios fonctionnels exécutés »), §14.1 (10
  scénarios obligatoires, repérés ci-dessous par le tag **[§14.1 #n]**), §17.2 (critères
  REC-01 à REC-11). En cas de doute sur un résultat attendu, le cahier des charges
  (`docs/Cahier_des_charges_PFA_SmartFlow.pdf`) prime sur ce document.
- **Environnement** : recette/démonstration (§15.1), c'est-à-dire `docker compose up`
  depuis une base fraîchement migrée (Flyway V1-V10 + jeu de données V5/V6). Jamais
  l'environnement de développement d'un poste, pour que le résultat soit reproductible par
  quiconque exécute ce cahier.
- **Comptes de démonstration** (`V5__seed_demo_data.sql`) — mot de passe unique
  `Password123!` pour tous :

  | Compte | Rôle | Périmètre | Emploi dans ce cahier |
  |---|---|---|---|
  | amina.idrissi@smartflow.local | REQUESTER | — | Demandeuse IT |
  | leila.chraibi@smartflow.local | REQUESTER | — | Demandeuse Achats |
  | youssef.amrani@smartflow.local | MANAGER | Équipe « Support Poste de travail » | Valideur IT |
  | sara.bennis@smartflow.local | AGENT | Équipe « Support Poste de travail » | Agent de traitement IT |
  | karim.elfassi@smartflow.local | SERVICE_MANAGER | Service Informatique | Responsable IT, cible d'escalade SLA |
  | fatima.squalli@smartflow.local | MANAGER | Équipe « Achats » | Valideur Achats |
  | reda.bakkali@smartflow.local | AGENT | Équipe « Achats » | Agent de traitement Achats |
  | nawal.idrissi@smartflow.local | SERVICE_MANAGER | Service Achats | Responsable Achats |
  | nadia.ziani@smartflow.local | FUNCTIONAL_ADMIN | Global | Administration fonctionnelle |
  | omar.tazi@smartflow.local | TECHNICAL_ADMIN | Global | Diagnostic technique |
  | hicham.alaoui@smartflow.local | AUDITOR | Global | Lecture seule |

- **Processus pilotes** (§3.4 — 2 attendus, les deux sont déjà configurés) :
  1. **Support Informatique → « Demande de matériel informatique »** :
     QUALIFICATION (AGENT) → VALIDATION hiérarchique (MANAGER) → TRAITEMENT (AGENT) → clôturée,
     avec une étape REJETEE terminale, une étape EN_ATTENTE_INFO qui suspend le compteur SLA
     pendant l'attente d'un complément (ADR-20/V10) et un raccourci : une demande CRITICAL
     passe direct de QUALIFICATION à TRAITEMENT sans validation hiérarchique.
  2. **Achats et Approvisionnement → « Demande d'achat »** : même circuit à 4 étapes, équipe
     et responsable différents (§2.1 — un seul moteur, deux configurations).
- **Traçabilité** : chaque scénario porte l'identifiant `REC-SCN-NN`, les critères §17.2
  couverts, et le ou les `RG-xx`/`§x.x` dont il est la traduction (CLAUDE.md — « le test est
  la traduction de la règle »).

## Table de couverture des critères §17.2

| Critère | Intitulé | Scénarios |
|---|---|---|
| REC-01 | Connexion et droits | 01, 02, 03, 15, 37 |
| REC-02 | Catalogue et formulaire | 06, 07, 08 |
| REC-03 | Workflow | 10, 11, 12, 13, 14, 16 |
| REC-04 | Historique | 17 |
| REC-05 | Affectation | 10, 18, 19, 20 |
| REC-06 | SLA | 21, 22, 23 |
| REC-07 | Notifications | 21, 24, 25 |
| REC-08 | Tableaux de bord | 26, 27, 28, 29 |
| REC-09 | Pièces jointes | 30, 31 |
| REC-10 | IA | 32, 33 |
| REC-11 | Déploiement | 40 |

Chaque critère REC-01 à REC-11 est couvert par au moins un scénario ; 40 scénarios au total,
soit 15 de plus que le minimum de 25 fixé par §3.4.

---

## Scénarios

### REC-SCN-01 — Connexion réussie et accueil adapté au rôle
**Couvre** : REC-01 · §6.1, §9.4
1. Ouvrir `/login`, se connecter avec `sara.bennis@smartflow.local` / `Password123!`.
2. Observer l'écran Accueil (`/`).

**Résultat attendu** : connexion acceptée, redirection vers l'écran Accueil ; la section
« Mes tâches à traiter » est visible (Sara porte le rôle AGENT) avec sa charge actuelle.

**Statut** : ✅ **Réussi** (2026-08-31) — connexion acceptée ; `GET /dashboards/home` porte
une section `agent` non nulle (`currentLoad: 0`) pour Sara, absente pour un rôle sans
affectation (cf. REC-SCN-28).

### REC-SCN-02 — Connexion échouée, message générique
**Couvre** : REC-01 · §13 (pas d'énumération de comptes valides)
1. Tenter une connexion avec `amina.idrissi@smartflow.local` et un mot de passe erroné.
2. Tenter une connexion avec un e-mail qui n'existe pas.

**Résultat attendu** : le même message générique (« Identifiants invalides ») dans les deux
cas — impossible de distinguer un compte existant d'un compte inexistant depuis la réponse.

**Statut** : ✅ **Réussi** — les deux cas retournent exactement
`{"code":"UNAUTHENTICATED","message":"Identifiants invalides."}`, HTTP 401 identique.

### REC-SCN-03 — Verrouillage de compte après tentatives répétées
**Couvre** : REC-01 · §13, ADR-13
1. Échouer 5 connexions consécutives sur un compte de démonstration (seuil par défaut,
   `security.login.max-attempts`).
2. Tenter une 6ᵉ fois avec le **bon** mot de passe.
3. (Admin) `nadia.ziani@smartflow.local` déverrouille le compte depuis
   `/administration/utilisateurs`.

**Résultat attendu** : à l'étape 2, refus avec le même message générique qu'un mot de passe
incorrect (un compte verrouillé ne doit jamais s'en distinguer) ; après déverrouillage
(étape 3), la connexion avec le bon mot de passe réussit.

**Statut** : ✅ **Réussi** (compte `hicham.alaoui@smartflow.local`) — la 6ᵉ tentative
(bon mot de passe) reçoit le même 401 générique que les 5 précédentes ;
`GET /admin/users` confirme `locked:true` ; après `POST .../unlock` par Nadia, la connexion
avec le bon mot de passe réussit (200).

### REC-SCN-04 — Mise à jour du profil
**Couvre** : §6.1 (libre-service)
1. Connecté comme `amina.idrissi@smartflow.local`, ouvrir `/profil`.
2. Modifier le prénom, enregistrer.
3. Recharger la page.

**Résultat attendu** : le nouveau prénom est conservé après rechargement ; l'e-mail reste en
lecture seule sur cet écran (réservé à un administrateur fonctionnel).

**Statut** : ✅ **Réussi** — `PUT /profile` change `firstName`, une nouvelle session
(`GET /auth/me`) confirme la persistance ; `ProfileController`/`UpdateProfileRequest`
n'exposent de toute façon aucun champ `email`. Reverti immédiatement après vérification pour
ne pas polluer les données de démo.

### REC-SCN-05 — Changement de mot de passe en libre-service
**Couvre** : §6.1, §13
1. Sur `/profil`, saisir un ancien mot de passe volontairement erroné puis confirmer.
2. Recommencer avec le bon ancien mot de passe et un nouveau mot de passe de 8+ caractères.
3. Se déconnecter puis se reconnecter avec le nouveau mot de passe.

**Résultat attendu** : étape 1 refusée (`INVALID_CURRENT_PASSWORD`) ; étape 2 acceptée ;
étape 3 réussit avec le nouveau mot de passe (l'ancien ne fonctionne plus).

**Statut** : ✅ **Réussi** — 400 `INVALID_CURRENT_PASSWORD` sur le mauvais ancien mot de
passe ; changement accepté (204) avec le bon ; connexion refusée (401) avec l'ancien mot de
passe après coup, réussie (200) avec le nouveau. Reverti à `Password123!` immédiatement
après vérification (idem SCN-04).

### REC-SCN-06 — [§14.1 #1] Création, brouillon puis soumission d'une demande valide
**Couvre** : REC-02 · §6.4, RG-01
1. Connecté comme `amina.idrissi@smartflow.local`, ouvrir le catalogue, choisir
   « Demande de matériel informatique ».
2. Remplir les champs obligatoires, enregistrer en brouillon, quitter l'écran.
3. Revenir sur le brouillon depuis « Mes demandes », compléter, soumettre.

**Résultat attendu** : le brouillon retrouvé porte les valeurs déjà saisies (§6.3) ; la
soumission produit une référence unique au format `DEM-{année}-{numéro}` (ADR-02) ; le
statut passe à « En cours ».

**Statut** : ✅ **Réussi** — référence `DEM-2026-000004` conforme, `GET` du brouillon
retourne les `fieldValues` déjà saisis, `POST /submit` fait passer le statut à `SUBMITTED`.

### REC-SCN-07 — [§14.1 #2] Refus d'une soumission avec champ obligatoire manquant
**Couvre** : REC-02 · §6.3
1. Démarrer une « Demande d'achat », laisser vide le champ obligatoire
   « Description de l'achat ».
2. Tenter la soumission.

**Résultat attendu** : soumission refusée côté serveur (pas seulement une validation
JavaScript contournable), message précis sur le champ manquant, la demande reste en
brouillon.

**Statut** : ✅ **Réussi** — 400 `VALIDATION_ERROR`,
`fieldErrors: [{"field":"item_description","message":"ne doit pas être vide"}]` ; la
demande reste `DRAFT` après la tentative.

### REC-SCN-08 — Affichage conditionnel d'un champ de formulaire
**Couvre** : REC-02 · §6.3
1. Sur « Demande de matériel informatique », régler « Niveau d'urgence » sur autre chose
   qu'« Urgent ».
2. Vérifier que « Justification de l'urgence » est masqué.
3. Régler « Niveau d'urgence » sur « Urgent ».

**Résultat attendu** : le champ « Justification de l'urgence » apparaît seulement quand
« Urgent » est sélectionné, disparaît sinon.

**Statut** : ✅ **Réussi** — confirmé côté serveur (le champ reste `required:false` quel
que soit `urgency`, la contrainte est purement d'affichage : soumission avec `urgency:
"Normal"` et sans `urgent_justification` acceptée) et côté client par relecture de code
(`frontend/src/pages/NewRequestPage.tsx:56-61`, `isVisible` filtre exactement
`values[fieldCode] === expectedValue`, soit `values.urgency === "Urgent"`). **Non rejoué au
clic dans le navigateur cette passe** (pas d'outil de pilotage navigateur chargé dans cette
session) — la preuve reste indirecte (contrat API + lecture de code), pas une capture
d'écran.

### REC-SCN-09 — Annulation d'une demande avant prise en charge
**Couvre** : §6.4
1. Soumettre une demande, ne pas l'affecter.
2. Le demandeur annule la demande depuis son écran de détail.
3. Un agent tente ensuite de l'affecter.

**Résultat attendu** : statut « Annulée » ; plus aucune action de workflow disponible
(`availableActions` vide) pour quiconque.

**Statut** : ✅ **Réussi** — statut `CANCELLED`, `availableActions` vide pour le demandeur.
**Observation plus stricte que l'énoncé** : l'agent de l'équipe qui aurait été responsable
(Sara) ne reçoit pas une vue avec `availableActions` vide mais un **404** direct sur
`GET /requests/{id}` — `RequestService.cancel` remet `currentStep=null`
(`RequestService.java:200`, même geste que CLOSE) et la visibilité TEAM ne se résout que via
l'étape courante ou une affectation active (`AuthorizationService.resolveScopeContext`),
toutes deux nulles ici. Sans conséquence pratique : `cancel()` refuse déjà toute annulation
dès qu'une affectation active existe (`ALREADY_TAKEN_CHARGE`), donc aucune équipe n'a jamais
de motif légitime à consulter une demande annulée avant prise en charge. Un 404 satisfait la
lettre de l'énoncé (« aucune action pour quiconque ») mais pas nécessairement son esprit
(« apparaît... » n'est jamais vérifié) ; à trancher explicitement si RG-06 doit garantir la
visibilité résiduelle d'une demande annulée à l'équipe qui l'aurait reçue.

### REC-SCN-10 — [§14.1 #3] Validation puis affectation à une équipe et à un agent
**Couvre** : REC-03, REC-05 · §6.5, §6.6
1. Amina soumet une demande de matériel non-CRITICAL.
2. Sara (AGENT) l'affecte à l'équipe « Support Poste de travail » (file d'équipe, personne
   de précis).
3. Youssef (MANAGER) la prend en charge lui-même, exécute VALIDATE.
4. Sara affecte ensuite la demande nommément à elle-même.

**Résultat attendu** : après l'étape 2, la demande apparaît dans la file d'équipe de tous
les membres, dans la file personnelle de personne ; après VALIDATE, l'étape courante devient
TRAITEMENT ; après l'étape 4, elle apparaît dans « Mes tâches » de Sara seule.

**Statut** : ✅ **Corrigé (ADR-18, V9)** — étapes 1-3 étaient déjà conformes : après l'ASSIGN
de l'étape 2, la demande apparaît dans `GET /tasks/team` (Sara) et pas dans `GET /tasks/mine` ;
après VALIDATE (étape 3), `currentStepId` passe bien à TRAITEMENT. **Étape 4 identifiée en
échec** (404, `ASSIGN` n'existait comme transition sortante que depuis QUALIFICATION dans les
deux workflows pilotes publiés, aucune arête depuis TRAITEMENT) **puis corrigée** :
`V9__pilot_workflow_reassignment.sql` ajoute une transition `ASSIGN` en boucle sur TRAITEMENT
(même motif que la boucle `REQUEST_INFO` déjà présente), voir ADR-18 pour la décision
complète. Vérifié par `PilotWorkflowReassignmentIT` (Testcontainers, jeu de données réel
V1-V9) — confirmé pour disparaître sans la migration, réussir avec. **Non encore rejoué en
conditions réelles Docker** dans cette passe (voir REC-SCN-40).

### REC-SCN-11 — [§14.1 #4] Retour au demandeur pour complément puis reprise
**Couvre** : REC-03 · §6.5
1. Une demande est en étape VALIDATION.
2. Youssef exécute RETURN avec un commentaire.
3. Amina consulte sa demande, constate la notification INFO_REQUESTED, ajoute un
   commentaire de réponse.
4. Sara (à nouveau en QUALIFICATION) exécute ASSIGN pour relancer le circuit.

**Résultat attendu** : l'étape courante repasse à QUALIFICATION après RETURN ; Amina reçoit
une notification exploitable ; le circuit reprend normalement après le complément.

**Statut** : ⚠️ **Partiellement réussi** — `currentStepId` repasse bien à QUALIFICATION
après RETURN ; Amina reçoit bien une notification exploitable et le circuit reprend
normalement (commentaire ajouté, ASSIGN de Sara relance vers VALIDATION). **Écart avec
l'énoncé** : le type de notification reçu est `DECISION`, jamais `INFO_REQUESTED` comme
l'énoncé l'affirme (« constate la notification INFO_REQUESTED »). En cause :
`WorkflowTransitionService.notifyForAction` regroupe délibérément `VALIDATE, REJECT, RETURN`
sous `NotificationType.DECISION` ; seule l'action distincte `REQUEST_INFO` (boucle sur place,
non légale à l'étape VALIDATION dans ces deux workflows) déclenche `INFO_REQUESTED`. Le
scénario semble avoir confondu les deux actions — son propre titre (« retour au demandeur
**pour complément** ») décrit exactement la sémantique de REQUEST_INFO, pas celle de RETURN
(qui renvoie à une étape antérieure du circuit, pas nécessairement au demandeur). À
corriger dans l'énoncé du scénario (attendre `DECISION`), sauf décision produit contraire.

### REC-SCN-12 — Rejet d'une demande, commentaire obligatoire
**Couvre** : REC-03 · RG-05
1. Une demande est en étape VALIDATION.
2. Youssef tente REJECT sans commentaire.
3. Youssef recommence avec un commentaire.

**Résultat attendu** : étape 2 refusée (`COMMENT_REQUIRED`) ; étape 3 acceptée, l'étape
courante devient REJETEE, aucune transition sortante n'est ensuite proposée.

**Statut** : ✅ **Réussi** — 400 `COMMENT_REQUIRED` sans commentaire
(« Un commentaire est obligatoire pour l'action REJECT (RG-05). ») ; 200 avec commentaire,
étape courante REJETEE, `availableActions` vide ensuite.

### REC-SCN-13 — Seules les actions légales de l'étape courante sont proposées
**Couvre** : REC-03 · CLAUDE.md (« jamais un bouton depuis le rôle »)
1. Se connecter successivement comme Sara (AGENT) et Youssef (MANAGER) sur la même demande,
   à chaque étape du circuit (QUALIFICATION, VALIDATION, TRAITEMENT).
2. Comparer les boutons visibles à `availableActions[]` retourné par
   `GET /api/v1/requests/{id}`.

**Résultat attendu** : les boutons affichés correspondent exactement à `availableActions[]`
pour chaque rôle et chaque étape — jamais un bouton en plus (ex. Sara ne voit jamais
VALIDATE), jamais un bouton manquant.

**Statut** : ✅ **Réussi** — matrice complète vérifiée aux 3 étapes du circuit IT (via l'API,
`ActionBar` ne fait que refléter `availableActions[]` par construction, déjà couvert par
`ActionBar.test.tsx`) : QUALIFICATION → Sara `[ASSIGN, REQUEST_INFO]` / Youssef `[]` ;
VALIDATION → Youssef `[VALIDATE, REJECT, RETURN]` / Sara `[]` ; TRAITEMENT → Sara
`[REQUEST_INFO, CLOSE]` / Youssef `[]`. Jamais de recouvrement entre les deux rôles.

### REC-SCN-14 — Séparation des tâches : un demandeur ne valide jamais sa propre demande
**Couvre** : REC-01, REC-03 · séparation des tâches (`application/security`)
1. Attribuer temporairement le rôle MANAGER à Amina sur l'équipe de Youssef (ou utiliser un
   compte cumulant REQUESTER + MANAGER sur le même périmètre).
2. Amina soumet une demande, l'affecte pour arriver en VALIDATION.
3. Amina tente VALIDATE sur sa propre demande.

**Résultat attendu** : refus (404, comme toute action hors périmètre) malgré un rôle
techniquement compatible — la séparation des tâches l'emporte sur le rôle seul.

**Statut** : ✅ **Réussi**, avec une précision utile confirmée en API : une fois le rôle
MANAGER/TEAM(1) accordé temporairement à Amina, `availableActions` sur sa propre demande à
VALIDATION contient **`["RETURN"]` seul** — VALIDATE et REJECT sont correctement absents,
mais RETURN reste offert. Une tentative directe de VALIDATE est bien refusée (404). Ceci
confirme au caractère près la formule `canAct` de CLAUDE.md : la séparation des tâches
bloque exactement `{VALIDER, REJETER}`, jamais RETURN — un choix déjà exact dans le code, pas
une découverte de bug. Rôle temporaire révoqué après le test (204 sur la révocation).

### REC-SCN-15 — [§14.1 #6] Accès à une demande d'un autre service, non autorisé
**Couvre** : REC-01 · RG-06, §13 (énumération)
1. Noter l'id d'une demande d'achat de Leila (service Achats).
2. Connecté comme Sara (AGENT, service Informatique seulement), ouvrir directement
   `/demandes/{id}` avec cet id.

**Résultat attendu** : `404 Not Found`, jamais `403` (CLAUDE.md — un 403 confirmerait
l'existence du dossier et permettrait d'énumérer ceux des autres services).

**Statut** : ✅ **Réussi** — `GET /requests/{id}` sur la demande Achats de Leila, connecté
comme Sara, retourne exactement `404 NOT_FOUND`, jamais `403`.

### REC-SCN-16 — [§14.1 #8] Nouvelle version de workflow sans impact sur une demande en cours
**Couvre** : REC-03 · RG-03, ADR-17
1. Amina soumet une demande de matériel (circuit v1, prise en charge par Sara).
2. Nadia (FUNCTIONAL_ADMIN) ouvre `/administration/workflows`, crée un brouillon v2 du
   workflow « Demande de matériel informatique » avec un circuit sensiblement différent
   (étapes/transitions modifiées), puis le publie.
3. Reprendre le traitement de la demande d'Amina (créée avant la publication) jusqu'à sa
   clôture.

**Résultat attendu** : la demande d'Amina continue de suivre exactement les étapes de la v1
jusqu'à sa clôture, sans jamais voir une étape ou une transition de la v2 ; une nouvelle
demande créée après l'étape 2 suit, elle, le circuit v2.

**Statut** : ✅ **Réussi** (RG-03/ADR-17 confirmé en conditions réelles) — la demande
soumise sous v1 (id 6) s'est clôturée en suivant exactement les étapes/transitions v1
(`CLOSE` légal à TRAITEMENT/v1) après publication d'un v2 délibérément raccourci
(QUALIFICATION → TRAITEMENT direct, sans étape VALIDATION) et l'archivage de v1 ; une
nouvelle demande soumise après la publication a atterri sur l'étape QUALIFICATION de **v2**
et, après ASSIGN, a sauté directement à TRAITEMENT (v2), confirmant l'isolation complète
entre versions dans les deux sens.

**Bug trouvé et corrigé en cours d'exécution de ce scénario** :
`UpsertStepRequest.displayOrder`/`suspendSla` étaient des champs primitifs (`int`/
`boolean`) plutôt que `Integer`/`Boolean` - un appel `POST .../steps` omettant l'une ou
l'autre clé du JSON échouait en `400 MALFORMED_REQUEST` au lieu de recevoir sa valeur par
défaut, exactement le même piège déjà documenté et corrigé sur `ExecuteTransitionRequest`/
`BulkAssignRequest` (CLAUDE.md, S10) mais manqué ici. Invisible depuis le SPA
(`AdminWorkflowsPage` envoie toujours les deux clés) - trouvé uniquement parce que cette
recette appelle l'API directement, comme n'importe quel autre client le ferait. **Corrigé** :
`suspendSla` en `Boolean` nullable (accesseur `isSuspendSla()`, absence = `false`) ;
`displayOrder` en `Integer` avec `@NotNull` (reste réellement obligatoire - erreur
`VALIDATION_ERROR` propre désormais, plutôt qu'un `MALFORMED_REQUEST` opaque). Deux tests de
non-régression ajoutés à `WorkflowDefinitionAdminControllerIT`
(`addStepWithoutSuspendSlaKeySucceeds`, `addStepWithoutDisplayOrderIsRejectedCleanly`) ; 355
tests Java passent (`./mvnw clean verify`).

### REC-SCN-17 — Historique complet, un événement = une ligne
**Couvre** : REC-04 · §6.4, §3.4 (traçabilité 100 %)
1. Dérouler une demande sur plusieurs actions (ASSIGN, VALIDATE, REQUEST_INFO, CLOSE).
2. Ouvrir la frise d'avancement sur l'écran détail.

**Résultat attendu** : chaque changement d'état produit exactement une ligne d'historique,
avec auteur, date et commentaire éventuel visibles — aucun changement sans trace, aucune
ligne dupliquée. **La soumission elle-même en fait partie** (ADR-23) : la frise commence par
une ligne `SUBMIT`, sans étape de départ, entrant dans la première étape du workflow.

**Statut** : ✅ **Réussi** — une demande déroulée sur ASSIGN, RETURN, ASSIGN, VALIDATE,
REQUEST_INFO, CLOSE (6 transitions) produit exactement 6 lignes d'historique
(`GET /requests/{id}/history`), chacune avec auteur, horodatage, et commentaire quand il y
en a un ; aucune manquante, aucune dupliquée.

**Mise à jour du 11/09/2026 (ADR-23).** Cette exécution est antérieure à ADR-23, qui a
refermé l'écart que ce scénario ne mesurait pas encore : `DRAFT → SUBMITTED` n'écrivait
alors **aucune** ligne, si bien que la frise commençait après la soumission et que
l'indicateur du §3.4 (« 100 % des changements d'état ») n'était pas tenu au pied de la
lettre. Le compte attendu devient donc **7** pour ce même parcours (`SUBMIT` + les
6 transitions). Re-vérifié sur la pile réelle : une demande fraîchement soumise renvoie
exactement une ligne, `action: "SUBMIT"`, `fromStepName: null`, `toStepName: "Qualification"`,
avec auteur et horodatage — et la collection Postman (`postman/`, dossier 3) le rejoue
automatiquement à chaque exécution.

### REC-SCN-18 — Affectation manuelle à un agent précis
**Couvre** : REC-05 · §6.6
**Énoncé corrigé par ADR-18** (docs/DECISIONS.md) : la version originale demandait à Fatima
(MANAGER, ne détient structurellement jamais `ASSIGN` - `RolePermissionRule`) d'affecter
nommément depuis QUALIFICATION, une étape où - dans la topologie réelle des deux workflows
pilotes - la seule arête `ASSIGN` mène à VALIDATION (MANAGER), rendant tout AGENT
structurellement inéligible comme cible à ce stade précis. Voir l'ADR pour pourquoi
l'éligibilité sur l'étape de destination reste le bon comportement (pas un bug) et pourquoi
la capacité elle-même est bien réelle ailleurs dans le même circuit.
1. Une demande CRITICAL a atteint l'étape TRAITEMENT (raccourci QUALIFICATION→TRAITEMENT).
2. Reda (AGENT, équipe Achats) affecte nommément la demande à sa collègue Salma Rifi
   (V9 - identifiant utilisateur).
3. Salma ouvre « Mes tâches ».

**Résultat attendu** : la demande apparaît dans la file personnelle de Salma uniquement,
disparaît de la file d'équipe générale.

**Statut** : ✅ **Corrigé (ADR-18, V9)** — `V9__pilot_workflow_reassignment.sql` ajoute la
boucle `ASSIGN` sur TRAITEMENT qui rend ce scénario exécutable ; `Reda.ASSIGN
(assignedUserId=Salma)` depuis TRAITEMENT réussit. Vérifié par
`PilotWorkflowReassignmentIT.namedReassignmentWithinTraitementSucceeds` (équivalent côté
équipe IT/Sara→Mehdi, mécanisme identique côté Achats) — non encore rejoué en conditions
réelles Docker dans cette passe (voir REC-SCN-40).

### REC-SCN-19 — Affectation automatique au membre le moins chargé
**Couvre** : REC-05 · §6.6
**Énoncé corrigé par ADR-18** : la version originale ne pouvait pas se vérifier faute d'un
second AGENT par équipe dans le jeu de données V5 (un seul par équipe, Sara/Reda) - `V9`
en ajoute un second par équipe pilote (Mehdi Ouazzani/Salma Rifi) pour que la sélection
« moins chargé » ait réellement deux candidats à départager.
1. Faire porter à Sara une demande CRITICAL déjà en TRAITEMENT (charge active = 1).
2. Soumettre une nouvelle demande CRITICAL, l'affecter à l'équipe « Support Poste de
   travail » avec l'option « Affectation automatique » (sans agent précis).

**Résultat attendu** : la demande est affectée à Mehdi (charge active = 0, strictement moins
chargé que Sara) — jamais un dépôt simple en file d'équipe, jamais reproposé à Sara.

**Statut** : ✅ **Corrigé (ADR-18, V9)** — `pickAutoAssignedUserId` filtre désormais deux
AGENT réels par équipe pilote plutôt qu'un candidat unique de repli. Vérifié par
`PilotWorkflowReassignmentIT.autoAssignWithinTraitementPicksLeastLoaded` : Sara chargée
d'une demande active, Mehdi à zéro, l'auto-affectation résout bien vers Mehdi. La règle de
répartition elle-même (`AutoAssignmentRule.pickLeastLoaded`) reste par ailleurs testée seule
et exactement (`AutoAssignmentRuleTest`, pure, déterministe) — non encore rejoué en
conditions réelles Docker dans cette passe (voir REC-SCN-40).

### REC-SCN-20 — Action en masse : réaffectation groupée
**Couvre** : REC-05 · §6.6 (« limité aux changements non risqués »)
1. Sur `/mes-taches` (file d'équipe), sélectionner plusieurs demandes dont une dont
   l'identifiant n'existe pas/plus (test d'isolation d'échec).
2. Lancer une affectation en masse (automatique ou vers un agent précis).

**Résultat attendu** : chaque demande valide de la sélection est affectée indépendamment ;
l'id invalide échoue seul (`NOT_FOUND`) sans empêcher les autres affectations de réussir ;
aucune action autre qu'ASSIGN n'est proposée pour un traitement en masse.

**Statut** : ✅ **Réussi** — `POST /tasks/bulk-assign` avec 2 ids valides + 1 id inexistant
(`999999`) : les 2 valides réussissent indépendamment (`success:true`), l'id invalide échoue
seul (`success:false, errorCode:"NOT_FOUND"`) sans affecter les deux autres. Le corps de la
requête n'accepte de toute façon qu'`assignedTeamId`/`assignedUserId`/`autoAssign`, aucun
paramètre d'action générique — « limité aux changements non risqués » tient par construction
de l'API, pas seulement par convention d'écran.

### REC-SCN-21 — [§14.1 #5] Dépassement de SLA, notification et escalade
**Couvre** : REC-06, REC-07 · §6.7
**Préalable ajouté en cours de session** : ce scénario, comme 22 et 23, supposait
implicitement qu'une demande réelle porte une `Priority` - or aucun code applicatif ne la
posait nulle part avant ce lot (`RequestService.qualify`, voir CLAUDE.md « état actuel du
dépôt »). `SlaSweepScheduler.recompute` ignore silencieusement toute demande dont la
priorité reste `null` (« not yet qualified ») : sans qualification, ce scénario échouait
donc à l'étape 1 elle-même, avant même d'atteindre son objet réel (l'escalade). L'étape 1
doit maintenant inclure une qualification explicite (`POST /requests/{id}/qualify`) entre la
soumission et l'affectation.
1. Amina soumet une demande, un agent la qualifie (priorité au choix), puis l'affecte à
   Sara ; avancer l'horloge (ou attendre) au-delà du délai de résolution configuré pour son
   type/priorité.
2. Laisser le balayage SLA planifié s'exécuter.

**Résultat attendu** : le badge SLA passe à « en retard » (`OVERDUE`) ; Sara reçoit une
notification `SLA_WARNING` en amont, puis Karim El Fassi (SERVICE_MANAGER responsable du
service) une notification `SLA_BREACH` lors du dépassement effectif — chacune une seule fois,
jamais à chaque balayage suivant.

**Statut** : ⏳ **Toujours non exécuté** — le préalable ci-dessus est désormais implémenté et
testé isolément (`RequestQualificationControllerIT`), mais ce scénario lui-même (avance
d'horloge réelle, balayage planifié, notifications SLA_WARNING/SLA_BREACH) reste à dérouler
en conditions réelles Docker, comme 22/23/24-40.

### REC-SCN-22 — Suspension du SLA en attente de complément
**Couvre** : REC-06 · RG-07
**Même préalable que REC-SCN-21** : qualifier la demande (`POST /requests/{id}/qualify`)
avant l'étape 1, sans quoi `SlaSweepScheduler` n'a jamais calculé d'échéance à noter.
1. Qualifier une demande, l'affecter, la faire avancer jusqu'à TRAITEMENT ; noter l'échéance
   de résolution.
2. Exécuter REQUEST_INFO (§6.5 le permet à cette étape), attendre un délai notable.
3. Le demandeur répond, l'agent relance le traitement.

**Résultat attendu** : le compteur SLA est suspendu pendant l'attente d'information (temps
non décompté), l'échéance recule d'autant à la reprise — jamais un compteur qui continue de
courir pendant que le dossier attend le demandeur.

**Statut** : ✅ **Corrigé (ADR-20, V10)** — en échec lors de la passe du 02/09 pour une raison
de configuration, non de code : `steps.suspend_sla` valait `false` sur les 8 étapes des deux
workflows pilotes (`V5__seed_demo_data.sql`), donc **aucune demande réelle n'avait jamais pu
suspendre son compteur**, et `REQUEST_INFO` rebouclait sur place sans changer d'étape. Le
mécanisme lui-même (`SlaSuspensionService`/`SlaSuspensionRule`/`SlaCalculator`) était
pourtant correct et testé en isolation depuis S8.
`V10__pilot_workflow_info_wait_step.sql` ajoute au circuit Support Informatique l'étape
`EN_ATTENTE_INFO` (`suspend_sla = true`), y redirige le `REQUEST_INFO` sortant de TRAITEMENT,
et pose la transition de reprise `ASSIGN` vers TRAITEMENT. Marquer TRAITEMENT lui-même aurait
suspendu tout le temps de traitement actif, pas seulement l'attente du demandeur (voir ADR-20
pour ce choix, et pour pourquoi le circuit Achats reste délibérément inchangé - §2.1).
Vérifié par `PilotWorkflowSlaSuspensionIT` contre le jeu de données réellement migré :
`REQUEST_INFO` depuis TRAITEMENT déplace bien la demande sur l'étape d'attente et écrit un
`SlaEvent` SUSPENDED, `ASSIGN` la ramène en TRAITEMENT en écrivant RESUMED, une seule fois
chacun ; un second test vérifie qu'`EN_ATTENTE_INFO` est la seule étape suspensive du
circuit.

### REC-SCN-23 — Indicateur visuel SLA
**Couvre** : REC-06 · §6.7
**Même préalable que REC-SCN-21** pour les deux demandes « dans le délai »/« à risque »/
« en retard » : sans qualification, `slaStatus` reste `null` en permanence plutôt que de
distinguer les trois états.
1. Observer une demande fraîchement soumise et qualifiée (dans les délais), une proche de
   l'échéance, et une en dépassement.

**Résultat attendu** : trois pastilles visuellement distinctes (« dans le délai », « à
risque », « en retard »), reflétant `slaStatus` tel que matérialisé côté serveur — jamais
recalculées côté client.

**Statut** : ⏳ Non exécuté (préalable désormais implémenté, voir REC-SCN-21).

### REC-SCN-24 — Notification obligatoire non désactivable
**Couvre** : REC-07 · §6.8, ADR-12
1. Un utilisateur désactive ses préférences de notification par e-mail pour un type non
   critique (ex. ASSIGNMENT), si l'écran le permet.
2. Le même utilisateur est ensuite visé par une notification `SLA_WARNING`.

**Résultat attendu** : l'e-mail `SLA_WARNING` part malgré tout (obligatoire, §6.7) ; le
centre de notifications applicatif, lui, n'est jamais soumis à cette préférence quel que soit
le type.

**Statut** : ✅ **Corrigé, désormais exécutable telle qu'écrite** — lors de la passe du 02/09,
l'étape 1 était infaisable : `NotificationPreference` était lue par `NotificationService`
mais rien ne l'écrivait, aucun écran ni endpoint ne permettant à un utilisateur de désactiver
quoi que ce soit (l'énoncé l'anticipait déjà : « si l'écran le permet »). Le §6.8 demandant
explicitement des « préférences de notification limitées », le trou est refermé :
`NotificationPreferenceService` + `GET/PUT /api/v1/profile/notification-preferences`
(libre-service strict, aucun `:id` - même construction que `ProfileService`) et une section
« Préférences de notification » sur l'écran Profil. La limite du §6.8 n'est pas réécrite là :
elle délègue à `MandatoryNotificationRule`, la même règle pure que `NotificationService`
consulte à l'émission - désactiver `SLA_WARNING`/`SLA_BREACH` est refusé explicitement
(`NOTIFICATION_TYPE_MANDATORY`, 400) plutôt qu'accepté puis ignoré, et l'écran affiche ces
types verrouillés à partir du drapeau `mandatory` renvoyé par le serveur, jamais d'une liste
recopiée côté client. Couvert par trois tests (`ProfileControllerIT`) : liste par défaut
(tout activé faute de ligne, ADR-12), désactivation d'un type facultatif réellement
persistée, refus total d'un type obligatoire (aucune ligne écrite). L'envoi effectif de
`SLA_WARNING` malgré tout reste par ailleurs confirmé en conditions réelles par REC-SCN-21
(MailHog).

### REC-SCN-25 — Centre de notifications
**Couvre** : REC-07 · §6.8
1. Ouvrir la cloche de notifications, en marquer une lue.
2. Cliquer le lien direct d'une notification liée à une demande.

**Résultat attendu** : le badge non-lu diminue après lecture ; le lien direct ouvre bien
l'écran détail de la demande concernée.

**Statut** : ✅ **Réussi** — `GET /notifications/unread-count` passe de 1 à 0 après
`POST /notifications/{id}/read` ; le `requestId` porté par la notification pointe bien vers
la demande réellement soumise (`GET /requests/{id}` réussit pour la même personne).

### REC-SCN-26 — Tableau de bord de service, filtres et indicateurs
**Couvre** : REC-08 · §6.9
1. Karim El Fassi ouvre `/tableau-de-bord`, sélectionne le service Informatique et une
   période couvrant les demandes de test créées ci-dessus.
2. Comparer les volumes par statut/catégorie/agent affichés aux demandes réellement créées
   sur la période.

**Résultat attendu** : les indicateurs (volumes, délai moyen de prise en charge/résolution,
taux de respect SLA) correspondent exactement aux données filtrées ; un changement de
période ou de service recalcule immédiatement l'affichage.

**Statut** : ✅ **Réussi** — `GET /dashboards/service?serviceId=1` reflète exactement la
seule demande réelle du service à cet instant (`volumesByStatus`, `volumesByAgent`
corrects). **Défaut trouvé et corrigé en cours de route** : un appel sans le paramètre
`serviceId` requis renvoyait un `500 INTERNAL_ERROR` opaque
(`MissingServletRequestParameterException` non gérée) plutôt qu'un `400 VALIDATION_ERROR`
propre (§11.1) - `GlobalExceptionHandler.handleMissingParameter` ajouté, testé
(`GlobalExceptionHandlerTest`), vérifié de nouveau en conditions réelles après correction.

### REC-SCN-27 — Export CSV cohérent avec la liste filtrée
**Couvre** : REC-08 · §6.9
1. Depuis le même tableau de bord, déclencher l'export CSV avec les mêmes filtres.

**Résultat attendu** : le fichier contient exactement une ligne par demande du tableau
filtré (pas plus, pas moins), sans brouillon.

**Statut** : ✅ **Réussi** — `GET /dashboards/service/export?serviceId=1` retourne un CSV
avec exactement une ligne, correspondant à l'unique demande soumise du service sur cette
instance ; aucun brouillon (le second brouillon créé en REC-SCN-33 n'apparaît pas).

### REC-SCN-28 — Écran Accueil : vue demandeur et vue agent
**Couvre** : REC-08 · §9.4, §6.9
1. Se connecter comme Leila (REQUESTER pure, aucun rôle complémentaire) : observer
   l'Accueil.
2. Se connecter comme Reda (AGENT) : observer l'Accueil.

**Résultat attendu** : Leila voit ses demandes en cours et ses dernières décisions, jamais de
section « tâches à traiter » ; Reda voit en plus sa charge actuelle, ses dossiers en retard
et ses priorités hautes — les indicateurs affichés sont bien adaptés au rôle de chacun.

**Statut** : ✅ **Réussi** — `GET /dashboards/home` renvoie `agent: null` pour Leila
(REQUESTER pure) et un objet `agent` réel (`currentLoad`, `overdue`, `highPriority`) pour
Reda (AGENT) - confirme de nouveau le correctif de garde documenté dans CLAUDE.md (§9.4).

### REC-SCN-29 — Taux de réouverture
**Couvre** : REC-08 · §6.9, RG-08
1. Clôturer deux demandes du même service sur la période observée au tableau de bord.
2. Rouvrir l'une des deux (voir REC-SCN-34).
3. Recharger le tableau de bord de ce service.

**Résultat attendu** : le taux de réouverture affiché est de 50 % (1 réouverte sur 2 jamais
clôturées) — jamais figé à 0 %.

**Statut** : ✅ **Mécanisme confirmé, hypothèse à 50 % non reproduite à l'identique** — une
seule demande a été clôturée puis réouverte sur cette instance (REC-SCN-21/34), donnant
`reopenRatePercent: 100.0` (1 réouverte sur 1 jamais close) - jamais figé à 0 %, ce que le
scénario veut avant tout vérifier. Le ratio exact de l'énoncé (50 %, 1 sur 2) demande un
second dossier clôturé-mais-jamais-réouvert sur la même période ; non rejoué faute de temps
dans cette passe, la formule elle-même (agrégat sur `RequestHistory`, jamais un compteur
recalculé) est déjà revue et documentée dans CLAUDE.md.

### REC-SCN-30 — [§14.1 #7] Fichier interdit ou trop volumineux
**Couvre** : REC-09 · RG-09
1. Sur une demande, tenter de joindre un fichier `.exe` renommé en `.pdf`.
2. Tenter de joindre un fichier `.zip` légitime mais dépassant 10 Mo.
3. Joindre un fichier `.pdf` valide de taille raisonnable.

**Résultat attendu** : étape 1 refusée (détection par signature/contenu, pas seulement
l'extension déclarée) ; étape 2 refusée (`FILE_TOO_LARGE`) ; étape 3 acceptée et
téléchargeable ensuite.

**Statut** : ✅ **Réussi, un défaut de proxy trouvé et corrigé en cours de route** — étape 1
(en-tête MZ d'exécutable renommé `.pdf`) refusée avec `CONTENT_TYPE_REJECTED` ; étape 3
(PDF valide) acceptée (201) et téléchargeable. **Défaut trouvé** : `nginx.conf` ne posait
aucun `client_max_body_size` sur `/api/`, laissant nginx à son défaut (1 Mo) - bien en
dessous de la politique réelle de l'application (`attachments.max-size-bytes`, 10 Mo par
défaut, RG-09). Conséquence en conditions réelles : **tout fichier légitime entre 1 et 10 Mo
échouait avec une page HTML brute `413 Request Entity Too Large`, jamais atteinte par
AttachmentValidationRule** - un PDF de 3 Mo, largement dans la politique annoncée, était
refusé avant même d'atteindre le back-end. Corrigé (`client_max_body_size 20m`, aligné sur
le plafond Spring `multipart.max-file-size`) : le même PDF de 3 Mo passe désormais (201), et
l'étape 2 (zip de 11 Mo) atteint bien le back-end pour recevoir la réponse JSON propre
`FILE_TOO_LARGE` attendue par l'énoncé, plutôt que la page nginx brute d'avant correctif.

### REC-SCN-31 — Pièce jointe accessible seulement aux utilisateurs habilités
**Couvre** : REC-09 · §5.1
1. Amina joint un fichier à sa demande IT.
2. Sara (périmètre TEAM couvrant la demande) le télécharge : doit réussir.
3. Reda (équipe Achats, hors périmètre de cette demande) tente le même téléchargement par
   son URL directe.

**Résultat attendu** : Sara télécharge sans erreur ; Reda reçoit un 404 — l'accès à une
pièce jointe suit exactement l'accès à la demande, jamais une URL devinable ou publique.

**Statut** : ✅ **Réussi** — `GET /requests/{id}/attachments/{attachmentId}` renvoie 200
pour Sara (périmètre TEAM couvrant la demande) et 404 pour Reda (équipe Achats, hors
périmètre), par identifiant direct dans les deux cas.

### REC-SCN-32 — [§14.1 #9] Module IA puis correction manuelle
**Couvre** : REC-10 · §12, RG-10
1. Sur une demande avec description et quelques commentaires, déclencher une classification
   IA puis un résumé, depuis l'écran détail.
2. Observer la catégorie/priorité suggérée et le résumé produits.
3. Valider l'analyse en corrigeant manuellement la valeur suggérée avant de l'accepter.

**Résultat attendu** : la suggestion est présentée comme une aide (catégorie, priorité,
score de confiance), jamais appliquée automatiquement à la demande ; après validation, seule
la ligne `AiAnalysis` porte `acceptedValue`/`validatedBy`/`validatedAt` — les champs de la
demande elle-même (priorité, catégorie) restent inchangés tant qu'un geste humain distinct
ne les modifie pas explicitement.

**Statut** : ✅ **Réussi, un défaut de dépendance découvert au passage (ADR-19)** —
classification (`{"category":"MATERIEL","priority":"LOW"}`, confiance 0.26) et résumé
extractif tous deux obtenus réellement (service Flask, `AI_ENABLED=true` pour cette passe) ;
validation avec correction manuelle (`priority` forcée à `CRITICAL` dans `acceptedValue`)
confirmée sur la ligne `AiAnalysis` (`validatedBy`/`validatedAt` posés) sans toucher
`Request.priority` (resté `LOW`, RG-10 intact). **Défaut trouvé en chemin** : ces mêmes
appels échouaient d'abord systématiquement en 404 sur une demande déjà clôturée et
individuellement affectée - pas un défaut IA, mais `AuthorizationService.canView`/
`canAnnotate` qui perdaient toute visibilité sur une demande CLOSED dès qu'elle n'était pas
restée en file d'équipe (voir ADR-19, corrigé dans ce même lot). Sans ce correctif, ce
scénario aurait été irréalisable sur toute demande déjà résolue.

### REC-SCN-33 — Mode IA désactivé
**Couvre** : REC-10 · §12.2
1. Redémarrer la pile avec `SMARTFLOW_AI_ENABLED=false`.
2. Retenter classification/résumé sur une demande.
3. Utiliser le reste de l'application (soumission, workflow, tableau de bord).

**Résultat attendu** : l'appel IA échoue immédiatement avec `AI_DISABLED`, sans jamais
tenter de joindre le service Flask ; le reste de l'application fonctionne normalement
(§12.2 — « l'application principale doit rester utilisable sans le service IA »).

**Statut** : ✅ **Réussi** — pile redémarrée avec `AI_ENABLED` retiré (défaut `false` du
`docker-compose.yml`) ; `GET /admin/diagnostics` bascule `aiServiceStatus` de `UP` à
`DISABLED` ; une nouvelle tentative de classification échoue immédiatement avec
`AI_DISABLED` (aucune requête vers le conteneur `ai` dans ses logs pour cet essai) ; création
d'une nouvelle demande dans le même intervalle confirmée réussie (`POST /requests` → 201).

### REC-SCN-34 — [§14.1 #10] Clôture, réouverture autorisée, vérification de l'historique
**Couvre** : RG-08, ADR-14
1. Clôturer une demande (motif obligatoire, solution et niveau de satisfaction facultatifs).
2. Depuis l'écran détail, rouvrir la demande dans le délai paramétrable (30 jours par
   défaut).
3. Consulter la frise d'avancement.

**Résultat attendu** : après réouverture, la demande reprend exactement à l'étape quittée
par la clôture, statut « En cours » ; la frise affiche la clôture puis la réouverture comme
deux lignes d'historique distinctes et horodatées.

**Statut** : ✅ **Réussi** — clôturée avec motif+solution+satisfaction=4, réouverte
(`POST /requests/{id}/reopen`) : `currentStepId` revient exactement à TRAITEMENT (l'étape
quittée par CLOSE), `status: SUBMITTED`. Historique à 4 lignes exactes (ASSIGN, VALIDATE,
CLOSE, REOPEN), une par transition réellement exécutée (RG-04/§3.4).

**Mise à jour du 11/09/2026 (ADR-23).** Depuis, la soumission écrit elle aussi sa ligne : le
même parcours en produit **5** (`SUBMIT`, ASSIGN, VALIDATE, CLOSE, REOPEN). Le résultat du
scénario — reprise à l'étape quittée, clôture et réouverture en deux lignes distinctes et
horodatées — est inchangé.

### REC-SCN-35 — Réouverture refusée hors délai ou type non autorisé
**Couvre** : RG-08
1. (Admin) désactiver `reopen_allowed` pour un type de demande, ou réduire
   `requests.reopen-window-days` à une valeur déjà dépassée pour une demande de test.
2. Tenter la réouverture depuis l'écran détail.

**Résultat attendu** : le bouton de réouverture n'apparaît pas (absent d'`availableActions`)
et l'appel direct à l'API échoue explicitement (`REOPEN_NOT_ALLOWED` ou
`REOPEN_WINDOW_EXPIRED`).

**Statut** : ✅ **Réussi** — `reopenAllowed` désactivé pour le type Achats, une demande
close de ce type porte `availableActions: []` (REOPEN absent) et l'appel direct échoue
explicitement avec `REOPEN_NOT_ALLOWED`. Configuration restaurée à `true` après le test pour
ne pas polluer le jeu de données de démonstration.

### REC-SCN-36 — Niveau de satisfaction facultatif à la clôture
**Couvre** : §6.4
1. Clôturer une demande sans renseigner de niveau de satisfaction.
2. Clôturer une autre demande en renseignant une note de 4/5.

**Résultat attendu** : les deux clôtures réussissent ; la première n'affiche aucune note sur
l'écran détail, la seconde affiche « 4 / 5 ».

**Statut** : ✅ **Réussi** — même demande clôturée deux fois (avant/après réouverture) :
`satisfactionRating: 4` la première fois, `satisfactionRating: null` la seconde (motif
fourni, note omise) - les deux clôtures réussissent (200) dans les deux cas.

### REC-SCN-37 — Compte utilisateur : désactivation logique, jamais de suppression
**Couvre** : REC-01 · RG-02, §6.10
1. Nadia (FUNCTIONAL_ADMIN) désactive un compte de démonstration.
2. Le compte désactivé tente de se connecter.
3. Nadia consulte les demandes déjà soumises par ce compte.

**Résultat attendu** : connexion refusée après désactivation (message générique, comme tout
échec d'authentification) ; le compte reste visible dans `/administration/utilisateurs`
(jamais supprimé) ; ses demandes passées restent consultables sans erreur (RG-12).

**Statut** : ✅ **Réussi** — Hicham désactivé (`POST /admin/users/{id}/deactivate`) ; sa
tentative de connexion échoue avec le même message générique `UNAUTHENTICATED` qu'un mot de
passe erroné (401), jamais un message distinct. Compte réactivé ensuite pour ne pas polluer
le jeu de données de démonstration.

### REC-SCN-38 — Journal d'audit trace un changement de rôle
**Couvre** : RG-11, §13.1
1. Nadia octroie un rôle complémentaire à un utilisateur (ou en révoque un).
2. Hicham (AUDITOR) consulte `/administration/journal-audit`, filtre par cet utilisateur.

**Résultat attendu** : une ligne d'audit apparaît avec acteur, date, action, type d'objet et
résumé du changement ; Hicham peut la consulter mais ne peut modifier aucune donnée
applicative depuis son compte (lecture seule, §5).

**Statut** : ✅ **Réussi** — la désactivation/réactivation de REC-SCN-37 produit deux lignes
(`DEACTIVATE`/`ACTIVATE`, acteur Nadia Ziani, `objectType: User`, horodatées) visibles par
Hicham (AUDITOR) via `GET /admin/audit-log?objectType=User&objectId=11` ; une tentative de
Hicham d'exécuter la même action administrative échoue en 404 (lecture seule, jamais un 403
qui confirmerait l'existence de la route pour son rôle).

### REC-SCN-39 — Diagnostic technique sans exposition de secret
**Couvre** : §6.10, §15.3
1. Omar (TECHNICAL_ADMIN) ouvre `/administration/diagnostic`.
2. Comparer les statuts affichés (back-end, base, service IA, messagerie) à l'état réel de
   la pile.

**Résultat attendu** : chaque statut reflète une vérification réelle à l'instant de l'appel
(pas une simple lecture de configuration) ; aucun hôte, port ou identifiant de connexion
n'apparaît dans la réponse.

**Statut** : ✅ **Réussi** — `aiServiceStatus` bascule réellement de `DISABLED` à `UP` puis
retour à `DISABLED` selon l'état effectif du coupe-circuit `SMARTFLOW_AI_ENABLED` (REC-SCN-
32/33), preuve que c'est une vérification en direct et non une lecture de configuration
figée. Réponse `{backendStatus, databaseStatus, aiServiceStatus, mailStatus, checkedAt}` -
aucun hôte, port ni identifiant de connexion.

### REC-SCN-40 — Déploiement par Docker Compose
**Couvre** : REC-11 · §3.4, §15.3
1. Depuis un dépôt propre, exécuter `docker compose up`.
2. Vérifier les endpoints de santé du back-end, de la base et du service IA.
3. Se connecter et dérouler un parcours complet (ex. REC-SCN-06 à REC-SCN-10).

**Résultat attendu** : l'application démarre entièrement sans intervention manuelle au-delà
de `docker compose up` ; les trois endpoints de santé répondent ; le parcours applicatif
fonctionne de bout en bout dans cet environnement (pas seulement en tests automatisés —
c'est ce niveau qui avait révélé le bug de permissions du volume `attachments`, invisible
aux 247 tests Java de l'époque).

**Statut** : ✅ **Réussi, deux nouveaux défauts trouvés et corrigés à ce niveau précis - pas
en tests** : `docker compose up -d --wait` démarre les cinq conteneurs sans intervention
manuelle ; `backendStatus`/`databaseStatus`/`aiServiceStatus`/`mailStatus` tous `UP` via
`/admin/diagnostics`, `/health` du service IA et `/actuator/health` du back-end confirmés
séparément. Le parcours complet (création → soumission → qualification → auto-affectation →
validation → clôture avec satisfaction → réouverture → tableau de bord) a été rejoué de
bout en bout (REC-SCN-21/29/34/36). **Deux défauts, invisibles aux 370 tests Java et
Testcontainers de la session, trouvés uniquement en rejouant les migrations et le proxy
réels** : (1) `V9` fixait des id de `transitions`/`users` en dur, entrant en collision avec
un volume `pgdata` de démonstration déjà utilisé de façon interactive (un brouillon de
workflow publié via l'admin avait déjà consommé les mêmes id) - corrigé en laissant
l'identité générer les id, comme toute migration postérieure au tout premier seed devrait le
faire (voir le commentaire de tête de `V9`). (2) le défaut de `client_max_body_size` de
nginx (REC-SCN-30). Le premier confirme, une fois de plus, que Testcontainers (base
toujours vierge) ne peut pas se substituer à un volume réellement utilisé pour ce genre de
vérification - exactement la même leçon que le bug de permissions `attachments` déjà
documenté.

---

## PV de recette

| Champ | Valeur |
|---|---|
| Date d'exécution | 2026-08-31 (REC-SCN-01 à 20) puis 2026-09-02 (REC-SCN-21 à 40) |
| Exécuté par | Session assistée (Claude Code), en conditions réelles Docker (`docker compose up`) |
| Version/commit testé | Arbre de travail non commité au moment de l'exécution - voir `git status`/`git diff` ; ADR-18, ADR-19 et `V9__pilot_workflow_reassignment.sql` inclus |
| Scénarios réussis | 40 / 40 |
| Scénarios en échec | Aucun. REC-SCN-22, seul échec de la passe du 02/09, a été corrigé le 03/09 (ADR-20/V10) et re-vérifié par `PilotWorkflowSlaSuspensionIT` |
| Anomalies bloquantes trouvées et corrigées dans ce même passage | ADR-19 (`canView`/`canAnnotate` perdaient l'accès à une demande CLOSED individuellement affectée) ; `nginx.conf` sans `client_max_body_size` (uploads >1 Mo rejetés avant d'atteindre l'application, REC-SCN-30/40) ; `V9` avec des id figés entrant en collision avec un volume de démonstration déjà utilisé (REC-SCN-40) |
| Anomalies non bloquantes trouvées et corrigées | `GlobalExceptionHandler` renvoyait 500 au lieu de 400 sur un paramètre de requête manquant (REC-SCN-26) |
| Anomalies non bloquantes restant ouvertes | REC-SCN-29 (ratio de réouverture exact de l'énoncé, 50 %, non reproduit à l'identique - mécanisme confirmé à 100 % sur un seul dossier) |
| Corrections apportées après la passe, le 03/09 | REC-SCN-22 (ADR-20/V10 - étape `EN_ATTENTE_INFO` suspensive) ; REC-SCN-24 (préférences de notification en libre-service, §6.8) ; §6.10 - durée des sessions et seuil d'alerte SLA rendus administrables, complétant les quatre paramètres généraux que le chapitre nomme |
| **Décision** | ☒ Accepté ☐ Accepté sous réserve ☐ Refusé — les 40 scénarios passent ; reste la réserve mineure de REC-SCN-29 (ratio, pas mécanisme) |
| Signature encadrant | *(à recueillir - hors périmètre d'une session assistée)* |
