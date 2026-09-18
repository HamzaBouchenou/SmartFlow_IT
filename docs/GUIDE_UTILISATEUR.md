# Guide utilisateur — SmartFlow IT

**Ancrage : §17.1** (« Guide utilisateur court pour les principaux rôles »).

Un chapitre par rôle du §5. Lisez celui qui vous concerne : un demandeur n'a pas besoin des
quatre autres. Les comptes cités sont ceux du jeu de démonstration (mot de passe
`Password123!`) ; sur un environnement réel, votre compte vous est fourni par
l'administrateur fonctionnel — **il n'existe pas d'auto-inscription**.

---

## Pour tout le monde

### Se connecter

Ouvrez l'application, saisissez votre adresse professionnelle et votre mot de passe.

Après plusieurs échecs, le compte est **temporairement verrouillé**. Le message reste le même
que pour un mot de passe erroné : c'est volontaire, un attaquant ne doit pas pouvoir deviner
quels comptes existent. Attendez quelques minutes ou demandez un déverrouillage à
l'administrateur.

**Votre session expire après une période d'inactivité.** Laisser un onglet ouvert ne la
maintient pas : seules vos actions comptent. Vous êtes alors ramené à l'écran de connexion
avec le message « votre session a expiré ».

### L'écran d'accueil

Il s'adapte à ce que vous êtes :

- Vos **demandes en cours**, vos dernières décisions reçues et les délais annoncés.
- Si vous traitez des demandes : votre **charge actuelle**, vos retards, vos priorités
  hautes.

### Les notifications

La cloche de l'en-tête affiche le nombre de messages non lus. La page **Notifications**
liste tout, permet de filtrer les non lues et de tout marquer comme lu.

**Vos préférences** (même page) décident des **e-mails** que vous recevez, type par type. La
notification *dans l'application* est toujours écrite, quoi qu'il arrive. Deux alertes ne
peuvent pas être désactivées — échéance proche et retard : ce sont des engagements de
service, pas des courtoisies.

### Votre profil

Depuis votre nom en haut à droite : votre rattachement (direction, service, responsable), vos
habilitations et le temps restant avant expiration de session. Vous pouvez modifier **votre
prénom et votre nom**, et changer votre mot de passe (l'ancien est demandé). Adresse, service
et responsable relèvent de l'administrateur.

---

## Demandeur

*Compte de démonstration : `amina.idrissi@smartflow.local`*

### Créer et soumettre une demande

1. **Catalogue** : parcourez les services, ou cherchez par mot-clé. Chaque fiche annonce
   ce à quoi vous avez droit de vous attendre — description, délai cible, pièces nécessaires.
2. Choisissez un type de demande : le formulaire correspondant s'affiche. Il est configuré
   par service, donc il change d'un type à l'autre. Certains champs n'apparaissent qu'en
   fonction de ce que vous répondez ailleurs.
3. **Enregistrer** garde un brouillon : vos valeurs sont conservées, vous pouvez revenir plus
   tard. Un brouillon n'est visible que de vous.
4. **Soumettre** : les champs obligatoires sont contrôlés, une **référence** vous est
   attribuée (`DEM-2026-000123`) et le circuit démarre.

> Une demande soumise n'est jamais supprimée. Vous pouvez l'**annuler** tant que personne ne
> l'a prise en charge ; elle reste consultable.

### Suivre vos demandes

**Mes demandes** liste vos dossiers, brouillons compris, avec un filtre par statut. L'écran
de détail d'un dossier montre :

- La **frise d'avancement** : chaque étape franchie, par qui et quand, depuis la soumission.
- L'**indicateur de délai** : dans le délai, à risque, en retard.
- Le **fil de discussion** et les **pièces jointes**.

### Répondre à une demande de complément

Si l'agent a besoin d'une précision, vous recevez une notification. Répondez dans le fil de
commentaires et joignez au besoin un fichier. **Le compteur de délai est suspendu pendant
cette attente** : le temps que vous prenez à répondre n'est pas compté contre le service.

### Commenter, joindre un fichier, mentionner quelqu'un

- Les pièces jointes sont contrôlées : extensions autorisées, taille maximale, et le type
  réel du fichier est vérifié — renommer un exécutable en `.pdf` ne suffit pas.
- Pour **mentionner** quelqu'un : `@` suivi de son adresse e-mail. Seules les personnes qui
  ont **déjà accès à cette demande** sont notifiées ; les noms retenus s'affichent sous le
  commentaire.

### Après la clôture

Vous pouvez **rouvrir** un dossier pendant une période limitée après sa clôture, si le type
de demande l'autorise — utile quand le problème revient. Le bouton n'apparaît que quand c'est
réellement possible. À la clôture, vous pouvez laisser une **note de satisfaction** (1 à 5),
facultative.

---

## Agent de traitement

*Compte de démonstration : `sara.bennis@smartflow.local`*

### Votre file de travail

**Mes tâches** montre ce qui vous est affecté et ce qui attend votre équipe. Filtrez par
statut, priorité, date, demandeur, catégorie ou retard.

- **Prendre en charge** une demande de la file d'équipe se fait depuis le dossier.
- **Actions en masse** : sélectionnez plusieurs lignes pour les affecter d'un coup — à un
  agent précis, à une équipe, ou en **affectation automatique** (le membre le moins chargé).
  Seule l'affectation est proposée en masse : les actions à conséquence se font une par une.
- Si une demande échoue dans un lot, les autres aboutissent quand même.

### Traiter une demande

L'écran de détail ne propose **que les actions réellement possibles** à cette étape, pour
vous. Si un bouton n'est pas là, c'est que l'action n'est pas légale ici — pas que l'écran
l'a caché.

| Action | Quand |
|---|---|
| **Qualifier** | Fixer la catégorie et la priorité. C'est ce qui démarre les échéances : à faire tôt. |
| **Affecter** | Vous attribuer le dossier, ou le passer à un collègue habilité |
| **Demander un complément** | Le dossier attend le demandeur, **le compteur de délai est suspendu** |
| **Clôturer** | Avec un motif et la solution apportée |

### L'assistance par IA

Le panneau d'assistance propose une **catégorie**, une **priorité** et un **résumé**, chacun
avec son niveau de confiance — deux chiffres séparés, parce que les deux suggestions ne
viennent pas du même mécanisme et ne sont pas également fiables.

**Rien n'est appliqué sans vous.** Une suggestion validée est enregistrée comme telle, mais
c'est votre geste de qualification qui modifie la demande. Corrigez librement : la
proposition est une aide, pas une décision. Si le module est désactivé, le panneau le dit et
le reste fonctionne normalement.

### Les délais

Chaque dossier porte une pastille : **dans le délai**, **à risque**, **en retard**. Vous êtes
notifié à l'approche de l'échéance ; en cas de dépassement, votre responsable de service
l'est aussi. Ces deux alertes ne se désactivent pas.

---

## Manager / valideur

*Comptes de démonstration : `youssef.amrani@smartflow.local` (Informatique),
`fatima.squalli@smartflow.local` (Achats)*

Vous intervenez à l'étape de validation des dossiers de votre périmètre. Trois actions :

| Action | Effet |
|---|---|
| **Valider** | Le dossier poursuit vers l'étape suivante |
| **Rejeter** | **Un commentaire est obligatoire** — un refus sans motif n'est pas un refus |
| **Retourner** | Renvoie le dossier à l'étape précédente pour correction |

Chaque décision est enregistrée avec votre nom, la date et votre commentaire, et apparaît
dans la frise du dossier. Personne ne peut valider sa propre demande, même en ayant le rôle :
la séparation des tâches est vérifiée côté serveur.

---

## Responsable de service

*Comptes de démonstration : `karim.elfassi@smartflow.local` (Informatique),
`nawal.idrissi@smartflow.local` (Achats)*

Vous avez autorité sur toutes les demandes de votre service, et le **tableau de bord** en
plus.

### Le tableau de bord

Choisissez le service, filtrez sur la période. Vous y trouvez :

- Les **volumes** par statut, par catégorie et par agent.
- Le **délai moyen de prise en charge** et le **délai moyen de résolution**.
- Le **taux de respect des SLA** et le **taux de réouverture**.

**Export CSV** : le bouton exporte exactement ce que vos filtres affichent, pas la table
entière.

### Escalades

Quand une demande de votre service dépasse son délai, vous êtes notifié — une seule fois par
dépassement, pas à chaque balayage. Vous pouvez alors réaffecter, requalifier la priorité, ou
agir directement sur le dossier.

---

## Administrateur fonctionnel

*Compte de démonstration : `nadia.ziani@smartflow.local`*

Le menu **Administration** regroupe neuf écrans. Deux principes à garder en tête :

1. **Rien ne se supprime.** On désactive. Un référentiel utilisé qui disparaîtrait
   emporterait l'historique des dossiers qui le référencent.
2. **Formulaires et workflows sont versionnés.** On modifie un brouillon, on le publie, et
   les demandes déjà en cours continuent sur la version qu'elles ont figée.

| Écran | Ce qu'on y fait |
|---|---|
| **Organisation** | Directions, services, équipes |
| **Catalogue** | Fiches de service et types de demande, ordre d'affichage, réouverture autorisée |
| **Formulaires** | Champs d'un type de demande — un brouillon à la fois, puis publication |
| **Workflows** | Étapes et transitions — même cycle brouillon/publication |
| **SLA** | Délais de prise en charge et de résolution, par type de demande et priorité |
| **Utilisateurs** | Comptes, mots de passe initiaux, activation, déverrouillage, rôles |
| **Modèles d'e-mail** | Sujet et contenu de chaque notification |
| **Paramètres généraux** | Extensions et taille des fichiers, durée des sessions, seuil d'alerte |
| **Journal d'audit** | Qui a changé quoi, et quand |

### Publier un formulaire ou un workflow

1. Créer un brouillon (un seul à la fois par type de demande).
2. Le modifier librement : tant qu'il est brouillon, rien n'est figé.
3. **Publier** : la version publiée précédente est archivée — jamais supprimée — et la
   nouvelle prend effet **pour les demandes à venir seulement**.

Un brouillon vide est refusé à la publication. Les actions « Soumettre » et « Rouvrir » ne
sont pas câblables comme transitions : l'application les exécute elle-même.

### Créer un compte

**Utilisateurs → Nouveau compte** : identité, service, responsable, mot de passe initial.
Puis attribuez un ou plusieurs rôles, **chacun dans un périmètre** — une équipe, un service,
une direction, ou global. Un rôle sans périmètre n'a pas de sens : c'est le couple qui
décide de ce que la personne voit et peut faire.

---

## Administrateur technique

*Compte de démonstration : `omar.tazi@smartflow.local`*

**Administration → Diagnostic** affiche l'état des services techniques : base de données,
service IA, serveur d'e-mail. Chaque ligne est une **connexion réellement tentée** — la
question est « est-ce que ça répond maintenant », pas « est-ce configuré ». Aucun hôte, port
ni identifiant n'est affiché.

« IA désactivée » n'est pas une panne : c'est le mode prévu quand le module est coupé.

Pour les journaux, les sauvegardes et les mises à jour, voir
[EXPLOITATION.md](EXPLOITATION.md).

---

## Auditeur

*Compte de démonstration : `hicham.alaoui@smartflow.local`*

Vous êtes en **lecture seule**, sur tout votre périmètre. Vous consultez les dossiers, leur
historique et leurs pièces jointes, mais vous n'écrivez jamais — pas même un commentaire.

**Administration → Journal d'audit** filtre par utilisateur, action, objet et période. On y
retrouve les changements de rôle, de statut, d'affectation et de configuration (RG-11). Le
journal n'a qu'un seul écrivain — l'application elle-même — et rien n'y est modifiable.

À distinguer de la **frise** d'un dossier, qui raconte la vie de *cette* demande et reste
visible de tous ceux qui y ont accès.
