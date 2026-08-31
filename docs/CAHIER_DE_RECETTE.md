# Cahier de recette — SmartFlow IT

**Statut de ce document :** scénarios rédigés, **non encore exécutés**. Ce cahier est le
script à dérouler pour produire les deux autres livrables du §17.1 qu'il alimente
(« Plan de tests, résultats et procès-verbal de recette ») : une fois chaque scénario joué,
cocher la colonne Statut, noter toute anomalie dans la colonne dédiée, puis reporter le
résultat global dans la section [PV de recette](#pv-de-recette) en fin de document.

## Portée et méthode

- **Référence** : §3.4 (« Au moins 25 scénarios fonctionnels exécutés »), §14.1 (10
  scénarios obligatoires, repérés ci-dessous par le tag **[§14.1 #n]**), §17.2 (critères
  REC-01 à REC-11). En cas de doute sur un résultat attendu, le cahier des charges
  (`docs/Cahier_des_charges_PFA_SmartFlow.pdf`) prime sur ce document.
- **Environnement** : recette/démonstration (§15.1), c'est-à-dire `docker compose up`
  depuis une base fraîchement migrée (Flyway V1-V8 + jeu de données V5/V6). Jamais
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
     avec une étape REJETEE terminale et un raccourci : une demande CRITICAL passe direct de
     QUALIFICATION à TRAITEMENT sans validation hiérarchique.
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

### REC-SCN-02 — Connexion échouée, message générique
**Couvre** : REC-01 · §13 (pas d'énumération de comptes valides)
1. Tenter une connexion avec `amina.idrissi@smartflow.local` et un mot de passe erroné.
2. Tenter une connexion avec un e-mail qui n'existe pas.

**Résultat attendu** : le même message générique (« Identifiants invalides ») dans les deux
cas — impossible de distinguer un compte existant d'un compte inexistant depuis la réponse.

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

### REC-SCN-04 — Mise à jour du profil
**Couvre** : §6.1 (libre-service)
1. Connecté comme `amina.idrissi@smartflow.local`, ouvrir `/profil`.
2. Modifier le prénom, enregistrer.
3. Recharger la page.

**Résultat attendu** : le nouveau prénom est conservé après rechargement ; l'e-mail reste en
lecture seule sur cet écran (réservé à un administrateur fonctionnel).

### REC-SCN-05 — Changement de mot de passe en libre-service
**Couvre** : §6.1, §13
1. Sur `/profil`, saisir un ancien mot de passe volontairement erroné puis confirmer.
2. Recommencer avec le bon ancien mot de passe et un nouveau mot de passe de 8+ caractères.
3. Se déconnecter puis se reconnecter avec le nouveau mot de passe.

**Résultat attendu** : étape 1 refusée (`INVALID_CURRENT_PASSWORD`) ; étape 2 acceptée ;
étape 3 réussit avec le nouveau mot de passe (l'ancien ne fonctionne plus).

### REC-SCN-06 — [§14.1 #1] Création, brouillon puis soumission d'une demande valide
**Couvre** : REC-02 · §6.4, RG-01
1. Connecté comme `amina.idrissi@smartflow.local`, ouvrir le catalogue, choisir
   « Demande de matériel informatique ».
2. Remplir les champs obligatoires, enregistrer en brouillon, quitter l'écran.
3. Revenir sur le brouillon depuis « Mes demandes », compléter, soumettre.

**Résultat attendu** : le brouillon retrouvé porte les valeurs déjà saisies (§6.3) ; la
soumission produit une référence unique au format `DEM-{année}-{numéro}` (ADR-02) ; le
statut passe à « En cours ».

### REC-SCN-07 — [§14.1 #2] Refus d'une soumission avec champ obligatoire manquant
**Couvre** : REC-02 · §6.3
1. Démarrer une « Demande d'achat », laisser vide le champ obligatoire
   « Description de l'achat ».
2. Tenter la soumission.

**Résultat attendu** : soumission refusée côté serveur (pas seulement une validation
JavaScript contournable), message précis sur le champ manquant, la demande reste en
brouillon.

### REC-SCN-08 — Affichage conditionnel d'un champ de formulaire
**Couvre** : REC-02 · §6.3
1. Sur « Demande de matériel informatique », régler « Niveau d'urgence » sur autre chose
   qu'« Urgent ».
2. Vérifier que « Justification de l'urgence » est masqué.
3. Régler « Niveau d'urgence » sur « Urgent ».

**Résultat attendu** : le champ « Justification de l'urgence » apparaît seulement quand
« Urgent » est sélectionné, disparaît sinon.

### REC-SCN-09 — Annulation d'une demande avant prise en charge
**Couvre** : §6.4
1. Soumettre une demande, ne pas l'affecter.
2. Le demandeur annule la demande depuis son écran de détail.
3. Un agent tente ensuite de l'affecter.

**Résultat attendu** : statut « Annulée » ; plus aucune action de workflow disponible
(`availableActions` vide) pour quiconque.

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

### REC-SCN-11 — [§14.1 #4] Retour au demandeur pour complément puis reprise
**Couvre** : REC-03 · §6.5
1. Une demande est en étape VALIDATION.
2. Youssef exécute RETURN avec un commentaire.
3. Amina consulte sa demande, constate la notification INFO_REQUESTED, ajoute un
   commentaire de réponse.
4. Sara (à nouveau en QUALIFICATION) exécute ASSIGN pour relancer le circuit.

**Résultat attendu** : l'étape courante repasse à QUALIFICATION après RETURN ; Amina reçoit
une notification exploitable ; le circuit reprend normalement après le complément.

### REC-SCN-12 — Rejet d'une demande, commentaire obligatoire
**Couvre** : REC-03 · RG-05
1. Une demande est en étape VALIDATION.
2. Youssef tente REJECT sans commentaire.
3. Youssef recommence avec un commentaire.

**Résultat attendu** : étape 2 refusée (`COMMENT_REQUIRED`) ; étape 3 acceptée, l'étape
courante devient REJETEE, aucune transition sortante n'est ensuite proposée.

### REC-SCN-13 — Seules les actions légales de l'étape courante sont proposées
**Couvre** : REC-03 · CLAUDE.md (« jamais un bouton depuis le rôle »)
1. Se connecter successivement comme Sara (AGENT) et Youssef (MANAGER) sur la même demande,
   à chaque étape du circuit (QUALIFICATION, VALIDATION, TRAITEMENT).
2. Comparer les boutons visibles à `availableActions[]` retourné par
   `GET /api/v1/requests/{id}`.

**Résultat attendu** : les boutons affichés correspondent exactement à `availableActions[]`
pour chaque rôle et chaque étape — jamais un bouton en plus (ex. Sara ne voit jamais
VALIDATE), jamais un bouton manquant.

### REC-SCN-14 — Séparation des tâches : un demandeur ne valide jamais sa propre demande
**Couvre** : REC-01, REC-03 · séparation des tâches (`application/security`)
1. Attribuer temporairement le rôle MANAGER à Amina sur l'équipe de Youssef (ou utiliser un
   compte cumulant REQUESTER + MANAGER sur le même périmètre).
2. Amina soumet une demande, l'affecte pour arriver en VALIDATION.
3. Amina tente VALIDATE sur sa propre demande.

**Résultat attendu** : refus (404, comme toute action hors périmètre) malgré un rôle
techniquement compatible — la séparation des tâches l'emporte sur le rôle seul.

### REC-SCN-15 — [§14.1 #6] Accès à une demande d'un autre service, non autorisé
**Couvre** : REC-01 · RG-06, §13 (énumération)
1. Noter l'id d'une demande d'achat de Leila (service Achats).
2. Connecté comme Sara (AGENT, service Informatique seulement), ouvrir directement
   `/demandes/{id}` avec cet id.

**Résultat attendu** : `404 Not Found`, jamais `403` (CLAUDE.md — un 403 confirmerait
l'existence du dossier et permettrait d'énumérer ceux des autres services).

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

### REC-SCN-17 — Historique complet, un événement = une ligne
**Couvre** : REC-04 · §6.4, §3.4 (traçabilité 100 %)
1. Dérouler une demande sur plusieurs actions (ASSIGN, VALIDATE, REQUEST_INFO, CLOSE).
2. Ouvrir la frise d'avancement sur l'écran détail.

**Résultat attendu** : chaque action exécutée produit exactement une ligne d'historique,
avec auteur, date et commentaire éventuel visibles — aucune action sans trace, aucune ligne
dupliquée.

### REC-SCN-18 — Affectation manuelle à un agent précis
**Couvre** : REC-05 · §6.6
1. Une demande est en étape QUALIFICATION (file d'équipe « Achats »).
2. Fatima affecte nommément la demande à Reda (identifiant utilisateur).
3. Reda ouvre « Mes tâches ».

**Résultat attendu** : la demande apparaît dans la file personnelle de Reda uniquement,
disparaît de la file d'équipe générale.

### REC-SCN-19 — Affectation automatique au membre le moins chargé
**Couvre** : REC-05 · §6.6
1. Vérifier la charge actuelle de chaque agent de l'équipe « Support Poste de travail »
   (Sara) sur `/mes-taches`.
2. Soumettre une nouvelle demande, l'affecter avec l'option « Affectation automatique »
   cochée et l'équipe renseignée (sans agent précis).

**Résultat attendu** : la demande est affectée à l'agent qui portait le moins de demandes
actives avant l'opération (à égalité, au plus petit identifiant) — jamais un dépôt simple en
file d'équipe.

### REC-SCN-20 — Action en masse : réaffectation groupée
**Couvre** : REC-05 · §6.6 (« limité aux changements non risqués »)
1. Sur `/mes-taches` (file d'équipe), sélectionner plusieurs demandes dont une dont
   l'identifiant n'existe pas/plus (test d'isolation d'échec).
2. Lancer une affectation en masse (automatique ou vers un agent précis).

**Résultat attendu** : chaque demande valide de la sélection est affectée indépendamment ;
l'id invalide échoue seul (`NOT_FOUND`) sans empêcher les autres affectations de réussir ;
aucune action autre qu'ASSIGN n'est proposée pour un traitement en masse.

### REC-SCN-21 — [§14.1 #5] Dépassement de SLA, notification et escalade
**Couvre** : REC-06, REC-07 · §6.7
1. Affecter une demande à Sara, puis avancer l'horloge (ou attendre) au-delà du délai de
   résolution configuré pour son type/priorité.
2. Laisser le balayage SLA planifié s'exécuter.

**Résultat attendu** : le badge SLA passe à « en retard » (`OVERDUE`) ; Sara reçoit une
notification `SLA_WARNING` en amont, puis Karim El Fassi (SERVICE_MANAGER responsable du
service) une notification `SLA_BREACH` lors du dépassement effectif — chacune une seule fois,
jamais à chaque balayage suivant.

### REC-SCN-22 — Suspension du SLA en attente de complément
**Couvre** : REC-06 · RG-07
1. Noter l'échéance de résolution d'une demande en TRAITEMENT.
2. Exécuter REQUEST_INFO (§6.5 le permet à cette étape), attendre un délai notable.
3. Le demandeur répond, l'agent relance le traitement.

**Résultat attendu** : le compteur SLA est suspendu pendant l'attente d'information (temps
non décompté), l'échéance recule d'autant à la reprise — jamais un compteur qui continue de
courir pendant que le dossier attend le demandeur.

### REC-SCN-23 — Indicateur visuel SLA
**Couvre** : REC-06 · §6.7
1. Observer une demande fraîchement soumise (dans les délais), une proche de l'échéance, et
   une en dépassement.

**Résultat attendu** : trois pastilles visuellement distinctes (« dans le délai », « à
risque », « en retard »), reflétant `slaStatus` tel que matérialisé côté serveur — jamais
recalculées côté client.

### REC-SCN-24 — Notification obligatoire non désactivable
**Couvre** : REC-07 · §6.8, ADR-12
1. Un utilisateur désactive ses préférences de notification par e-mail pour un type non
   critique (ex. ASSIGNMENT), si l'écran le permet.
2. Le même utilisateur est ensuite visé par une notification `SLA_WARNING`.

**Résultat attendu** : l'e-mail `SLA_WARNING` part malgré tout (obligatoire, §6.7) ; le
centre de notifications applicatif, lui, n'est jamais soumis à cette préférence quel que soit
le type.

### REC-SCN-25 — Centre de notifications
**Couvre** : REC-07 · §6.8
1. Ouvrir la cloche de notifications, en marquer une lue.
2. Cliquer le lien direct d'une notification liée à une demande.

**Résultat attendu** : le badge non-lu diminue après lecture ; le lien direct ouvre bien
l'écran détail de la demande concernée.

### REC-SCN-26 — Tableau de bord de service, filtres et indicateurs
**Couvre** : REC-08 · §6.9
1. Karim El Fassi ouvre `/tableau-de-bord`, sélectionne le service Informatique et une
   période couvrant les demandes de test créées ci-dessus.
2. Comparer les volumes par statut/catégorie/agent affichés aux demandes réellement créées
   sur la période.

**Résultat attendu** : les indicateurs (volumes, délai moyen de prise en charge/résolution,
taux de respect SLA) correspondent exactement aux données filtrées ; un changement de
période ou de service recalcule immédiatement l'affichage.

### REC-SCN-27 — Export CSV cohérent avec la liste filtrée
**Couvre** : REC-08 · §6.9
1. Depuis le même tableau de bord, déclencher l'export CSV avec les mêmes filtres.

**Résultat attendu** : le fichier contient exactement une ligne par demande du tableau
filtré (pas plus, pas moins), sans brouillon.

### REC-SCN-28 — Écran Accueil : vue demandeur et vue agent
**Couvre** : REC-08 · §9.4, §6.9
1. Se connecter comme Leila (REQUESTER pure, aucun rôle complémentaire) : observer
   l'Accueil.
2. Se connecter comme Reda (AGENT) : observer l'Accueil.

**Résultat attendu** : Leila voit ses demandes en cours et ses dernières décisions, jamais de
section « tâches à traiter » ; Reda voit en plus sa charge actuelle, ses dossiers en retard
et ses priorités hautes — les indicateurs affichés sont bien adaptés au rôle de chacun.

### REC-SCN-29 — Taux de réouverture
**Couvre** : REC-08 · §6.9, RG-08
1. Clôturer deux demandes du même service sur la période observée au tableau de bord.
2. Rouvrir l'une des deux (voir REC-SCN-34).
3. Recharger le tableau de bord de ce service.

**Résultat attendu** : le taux de réouverture affiché est de 50 % (1 réouverte sur 2 jamais
clôturées) — jamais figé à 0 %.

### REC-SCN-30 — [§14.1 #7] Fichier interdit ou trop volumineux
**Couvre** : REC-09 · RG-09
1. Sur une demande, tenter de joindre un fichier `.exe` renommé en `.pdf`.
2. Tenter de joindre un fichier `.zip` légitime mais dépassant 10 Mo.
3. Joindre un fichier `.pdf` valide de taille raisonnable.

**Résultat attendu** : étape 1 refusée (détection par signature/contenu, pas seulement
l'extension déclarée) ; étape 2 refusée (`FILE_TOO_LARGE`) ; étape 3 acceptée et
téléchargeable ensuite.

### REC-SCN-31 — Pièce jointe accessible seulement aux utilisateurs habilités
**Couvre** : REC-09 · §5.1
1. Amina joint un fichier à sa demande IT.
2. Sara (périmètre TEAM couvrant la demande) le télécharge : doit réussir.
3. Reda (équipe Achats, hors périmètre de cette demande) tente le même téléchargement par
   son URL directe.

**Résultat attendu** : Sara télécharge sans erreur ; Reda reçoit un 404 — l'accès à une
pièce jointe suit exactement l'accès à la demande, jamais une URL devinable ou publique.

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

### REC-SCN-33 — Mode IA désactivé
**Couvre** : REC-10 · §12.2
1. Redémarrer la pile avec `SMARTFLOW_AI_ENABLED=false`.
2. Retenter classification/résumé sur une demande.
3. Utiliser le reste de l'application (soumission, workflow, tableau de bord).

**Résultat attendu** : l'appel IA échoue immédiatement avec `AI_DISABLED`, sans jamais
tenter de joindre le service Flask ; le reste de l'application fonctionne normalement
(§12.2 — « l'application principale doit rester utilisable sans le service IA »).

### REC-SCN-34 — [§14.1 #10] Clôture, réouverture autorisée, vérification de l'historique
**Couvre** : RG-08, ADR-14
1. Clôturer une demande (motif obligatoire, solution et niveau de satisfaction facultatifs).
2. Depuis l'écran détail, rouvrir la demande dans le délai paramétrable (30 jours par
   défaut).
3. Consulter la frise d'avancement.

**Résultat attendu** : après réouverture, la demande reprend exactement à l'étape quittée
par la clôture, statut « En cours » ; la frise affiche la clôture puis la réouverture comme
deux lignes d'historique distinctes et horodatées.

### REC-SCN-35 — Réouverture refusée hors délai ou type non autorisé
**Couvre** : RG-08
1. (Admin) désactiver `reopen_allowed` pour un type de demande, ou réduire
   `requests.reopen-window-days` à une valeur déjà dépassée pour une demande de test.
2. Tenter la réouverture depuis l'écran détail.

**Résultat attendu** : le bouton de réouverture n'apparaît pas (absent d'`availableActions`)
et l'appel direct à l'API échoue explicitement (`REOPEN_NOT_ALLOWED` ou
`REOPEN_WINDOW_EXPIRED`).

### REC-SCN-36 — Niveau de satisfaction facultatif à la clôture
**Couvre** : §6.4
1. Clôturer une demande sans renseigner de niveau de satisfaction.
2. Clôturer une autre demande en renseignant une note de 4/5.

**Résultat attendu** : les deux clôtures réussissent ; la première n'affiche aucune note sur
l'écran détail, la seconde affiche « 4 / 5 ».

### REC-SCN-37 — Compte utilisateur : désactivation logique, jamais de suppression
**Couvre** : REC-01 · RG-02, §6.10
1. Nadia (FUNCTIONAL_ADMIN) désactive un compte de démonstration.
2. Le compte désactivé tente de se connecter.
3. Nadia consulte les demandes déjà soumises par ce compte.

**Résultat attendu** : connexion refusée après désactivation (message générique, comme tout
échec d'authentification) ; le compte reste visible dans `/administration/utilisateurs`
(jamais supprimé) ; ses demandes passées restent consultables sans erreur (RG-12).

### REC-SCN-38 — Journal d'audit trace un changement de rôle
**Couvre** : RG-11, §13.1
1. Nadia octroie un rôle complémentaire à un utilisateur (ou en révoque un).
2. Hicham (AUDITOR) consulte `/administration/journal-audit`, filtre par cet utilisateur.

**Résultat attendu** : une ligne d'audit apparaît avec acteur, date, action, type d'objet et
résumé du changement ; Hicham peut la consulter mais ne peut modifier aucune donnée
applicative depuis son compte (lecture seule, §5).

### REC-SCN-39 — Diagnostic technique sans exposition de secret
**Couvre** : §6.10, §15.3
1. Omar (TECHNICAL_ADMIN) ouvre `/administration/diagnostic`.
2. Comparer les statuts affichés (back-end, base, service IA, messagerie) à l'état réel de
   la pile.

**Résultat attendu** : chaque statut reflète une vérification réelle à l'instant de l'appel
(pas une simple lecture de configuration) ; aucun hôte, port ou identifiant de connexion
n'apparaît dans la réponse.

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

---

## PV de recette

*(à remplir après exécution complète du cahier ci-dessus)*

| Champ | Valeur |
|---|---|
| Date d'exécution | |
| Exécuté par | |
| Version/commit testé | |
| Scénarios réussis | / 40 |
| Scénarios en échec | |
| Anomalies bloquantes | |
| Anomalies non bloquantes | |
| **Décision** | ☐ Accepté ☐ Accepté sous réserve ☐ Refusé |
| Signature encadrant | |
