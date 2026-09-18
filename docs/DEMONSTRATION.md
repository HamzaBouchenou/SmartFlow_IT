# Scénario de démonstration finale — SmartFlow IT

**Ancrage : §17.3** (« Démonstration finale recommandée ») et §17.1 (« scénario de
démonstration » parmi les livrables). Les six étapes du §17.3 sont reprises dans l'ordre et
repérées **[§17.3 #n]**.

**Durée : 15 à 20 minutes.** Ce n'est pas le cahier de recette — celui-ci prouve les 40
scénarios un par un ([CAHIER_DE_RECETTE.md](CAHIER_DE_RECETTE.md)). Ici on raconte **une
demande, du besoin à la clôture**, en montrant au passage ce qui distingue l'application
d'un formulaire branché sur une base.

---

## Avant de commencer

```bash
# Pile reconstruite depuis une base vierge, IA activée pour la démonstration
docker compose down -v
AI_ENABLED=true docker compose up -d --build --wait
```

`--wait` ne rend la main qu'une fois les *healthchecks* au vert : si la commande se termine
sans erreur, tout est prêt. Comptez deux à trois minutes la première fois.

**Ouvrez trois fenêtres de navigateur** (ou trois profils / fenêtres privées) : la
démonstration change de rôle cinq fois, et se reconnecter à chaque fois casse le rythme.

| Fenêtre | Compte | Rôle |
|---|---|---|
| A | `amina.idrissi@smartflow.local` | Demandeuse |
| B | `youssef.amrani@smartflow.local` | Manager, puis `sara.bennis@smartflow.local` (agent) |
| C | `karim.elfassi@smartflow.local` | Responsable de service, puis `nadia.ziani@…` (admin) |

Mot de passe unique : `Password123!`.
Gardez un onglet sur **<http://localhost:8025>** (MailHog) : les e-mails y arrivent en direct,
et c'est la preuve visible que les notifications partent vraiment.

> **Repli si la démonstration doit se faire sans le module IA** : sautez l'étape 3, elle est
> la seule à en dépendre. Le panneau affichera « service désactivé » et le reste du parcours
> est identique — c'est d'ailleurs un point à souligner (§12.2 : le socle ne dépend pas de
> l'IA).

---

## 1. Connexion et création d'une demande depuis le catalogue — [§17.3 #1]

**Fenêtre A, Amina.**

1. **Accueil.** Faites remarquer que l'écran est déjà personnalisé : ses demandes en cours,
   ses dernières décisions, les délais annoncés. Un agent verrait sa charge à la place.
2. **Catalogue.** Parcourez les fiches, puis cherchez « matériel ». Chaque fiche annonce le
   délai cible et les pièces nécessaires **avant** la saisie.
3. Choisissez *Support Informatique → Demande de matériel*.
4. **Le formulaire n'est pas dans le code** : il est configuré pour ce type de demande. On y
   reviendra à l'étape 6.
5. Remplissez partiellement et **Enregistrer**. Montrez que le brouillon est retrouvé dans
   **Mes demandes**, valeurs conservées.
6. Tentez de **soumettre** en laissant un champ obligatoire vide → refus, avec l'erreur
   portée par le champ concerné. *La même règle est appliquée côté serveur : le contrôle du
   navigateur est un confort, pas la sécurité.*
7. Complétez, **Soumettre**.

**À montrer :** la **référence** attribuée (`DEM-2026-00000x`) — une séquence en base, jamais
un comptage de lignes — et, dans MailHog, l'**accusé de soumission** déjà arrivé.

---

## 2. Validation par un manager, puis affectation à une équipe — [§17.3 #2]

**Fenêtre B, Youssef (manager).**

1. **Mes tâches** : la demande est là, sans que personne n'ait eu à la router à la main.
2. Ouvrez le dossier. **Ne montrez que les boutons présents** et dites pourquoi : ils
   viennent du serveur, qui a évalué le rôle, le périmètre, l'étape et la séparation des
   tâches. L'interface ne décide rien.
3. **Valider**. La demande avance à l'étape de traitement.

**Le moment fort — l'autorisation.** Restez sur le dossier et, dans **la fenêtre A**, tentez
d'ouvrir la même URL en tant qu'Amina une fois le dossier sorti de son périmètre de
modification, ou mieux : connectez-vous en `leila.chraibi@smartflow.local` (demandeuse de
l'autre service) et ouvrez l'URL du dossier.

→ **404, pas 403.** Insistez : un `403` confirmerait que le dossier existe et permettrait
d'énumérer ceux des autres services. L'absence d'information *est* la mesure de sécurité.

4. Reconnectez la fenêtre B en **Sara (agent)**. Depuis **Mes tâches**, prenez le dossier en
   charge — ou montrez l'**affectation automatique**, qui choisit le membre le moins chargé
   de l'équipe (Sara ou Mehdi, selon leur charge du moment).

**À montrer :** l'e-mail d'affectation dans MailHog, et la **frise** du dossier qui compte
déjà trois lignes — soumission, validation, affectation — chacune avec son auteur et sa date.

---

## 3. Qualification assistée par IA, avec correction de la proposition — [§17.3 #3]

**Fenêtre B, Sara.**

1. Dans le panneau d'assistance, lancez la **classification** puis le **résumé**.
2. Deux confiances distinctes s'affichent — une pour la catégorie, une pour la priorité.
   Expliquez pourquoi : elles ne viennent pas du même mécanisme (la catégorie d'un modèle
   d'apprentissage, la priorité de règles), donc une moyenne masquerait laquelle vérifier.
3. **Corrigez** la catégorie proposée, validez la suggestion.
4. **Qualifiez** ensuite la demande — catégorie et priorité — par le bouton dédié.

**Le point à ne pas manquer (RG-10).** Ce sont **deux gestes différents**. Valider une
suggestion n'écrit rien sur la demande : cela enregistre seulement que l'IA a proposé ceci et
qu'un humain a retenu cela. C'est la qualification, un geste humain explicite, qui modifie le
dossier. *Aucun chemin de code ne permet à l'IA d'écrire une donnée métier.*

Précisez aussi que tout est **local** : modèle entraîné au démarrage sur un jeu généré,
résumé extractif en Python pur. Aucune donnée ne sort — ce qui répond au risque
« confidentialité » du §18.

---

## 4. Traitement, commentaire et pièce jointe — [§17.3 #4]

**Fenêtre B, Sara.**

1. Ajoutez un **commentaire**, en **mentionnant** le manager : `@youssef.amrani@smartflow.local`.
   → Son nom apparaît sous le commentaire, et il reçoit une notification. Essayez ensuite de
   mentionner quelqu'un hors périmètre : le commentaire est accepté, mais **personne n'est
   notifié et rien ne le signale** — répondre « cette personne n'a pas accès » révélerait qui
   a accès à quoi.
2. Joignez un **PDF** → accepté, téléchargeable.
3. Tentez de joindre un **exécutable** (ou un `.exe` renommé en `.pdf`) → **refusé**. Le
   contrôle porte sur l'extension, la taille **et la signature réelle du fichier** :
   renommer ne trompe rien (RG-09).
4. Montrez que l'URL de téléchargement n'est pas devinable et passe par un contrôle de droits
   — les fichiers ne sont jamais servis comme ressources statiques.

**Optionnel, si le temps le permet — la suspension du délai.** Faites **Demander un
complément** : le dossier passe en attente, et **le compteur de délai s'arrête**. Répondez
depuis la fenêtre A, reprenez le traitement : le compteur repart, et l'échéance a reculé du
temps d'attente. Le temps que prend le demandeur n'est pas compté contre le service.

---

## 5. Délai, alerte et tableau de bord — [§17.3 #5]

**Fenêtre C, Karim (responsable de service).**

1. Sur le dossier : la **pastille de délai** — dans le délai / à risque / en retard.
2. **Simuler une alerte.** Passez en `nadia.ziani` (administratrice), ouvrez
   **Administration → Paramètres généraux** et abaissez `sla.warning-threshold-percent` à
   `1`. Au balayage suivant (moins d'une minute), la demande bascule **à risque**, sans
   redémarrage.
   → L'agent reçoit l'alerte, et en cas de dépassement le responsable est notifié, **une
   seule fois par franchissement**, pas à chaque balayage.
   *Pensez à remettre la valeur d'origine après la démonstration.*
3. **Tableau de bord** (Karim) : volumes par statut, catégorie et agent ; délai moyen de
   prise en charge et de résolution ; taux de respect des SLA ; taux de réouverture.
4. **Export CSV** : il exporte exactement ce que les filtres affichent.

**À souligner :** aucun statut SLA n'est recalculé par cette page. Les échéances sont
matérialisées à la soumission et mises à jour par un balayage — deux écrans ne peuvent donc
pas afficher deux vérités.

---

## 6. Clôture, historique et journal d'audit — [§17.3 #6]

**Fenêtre B, Sara.**

1. **Clôturer** avec un motif et la solution apportée.
2. **Fenêtre A, Amina** : la demande est clôturée, elle laisse une **note de satisfaction**.
3. **Rouvrir** : le bouton est là parce que la fenêtre de réouverture est ouverte et que le
   type de demande l'autorise — pas parce qu'Amina a un rôle. Rouvrez : le dossier **reprend
   exactement à l'étape quittée**.
4. **La frise complète** : chaque changement d'état depuis la soumission, avec auteur, date
   et commentaire. Exactement une ligne par transition — jamais zéro, jamais deux.

**Fenêtre C, en `hicham.alaoui` (auditeur) ou `nadia.ziani`.**

5. **Administration → Journal d'audit**, filtré sur la période : les changements de rôle,
   de statut, d'affectation et de configuration — y compris le paramètre SLA modifié à
   l'étape 5.

**La distinction à expliquer :** la frise raconte la vie d'**une demande** et se lit par tous
ceux qui y ont accès ; le journal d'audit trace les gestes d'**administration** et n'est
lisible que des administrateurs et de l'auditeur. L'un ne remplace pas l'autre.

---

## Bonus — la configurabilité, s'il reste du temps

C'est la promesse du §2.1 : « ajouter de nouveaux services sans modifier le code métier ».

**Fenêtre C, `nadia.ziani`.**

1. **Administration → Workflows**, type *Demande de matériel*. Créez un **brouillon** de
   nouvelle version, ajoutez une étape, publiez.
2. Rouvrez la demande de la démonstration : **elle n'a pas bougé**. Elle a figé sa version de
   workflow à la soumission (RG-03), et le moteur résout toujours *ce* graphe-là.
3. Créez une **nouvelle** demande du même type : elle suit le nouveau circuit.

Une seule phrase suffit à conclure : *le circuit est une donnée, pas du code — et changer la
donnée ne réécrit jamais le passé.*

---

## Aide-mémoire

| | |
|---|---|
| Application | <http://localhost:5173> |
| E-mails (MailHog) | <http://localhost:8025> |
| OpenAPI / Swagger | <http://localhost:8080/swagger-ui.html> |
| Mot de passe des comptes de démonstration | `Password123!` |
| Tout remettre à zéro | `docker compose down -v && AI_ENABLED=true docker compose up -d --build --wait` |

**Après la démonstration**, remettez à leur valeur d'origine les paramètres modifiés à
l'étape 5, ou repartez d'une base vierge.
