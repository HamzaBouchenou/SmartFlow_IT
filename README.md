# SmartFlow IT

Plateforme de gestion des demandes internes et de pilotage des SLA.
Projet de fin d'études — ENSA Khouribga / Neonovia.

Les demandes internes entrent par un catalogue de services, circulent dans des circuits de
validation **configurables** (aucun code métier par type de demande), et leur traitement est
mesuré par des engagements de service. Deux fonctions d'assistance par IA — classification et
résumé — proposent, mais n'écrivent jamais sur une demande (RG-10).

> **La source de vérité fonctionnelle est le cahier des charges**
> (`docs/Cahier_des_charges_PFA_SmartFlow.pdf`). Les décisions qu'il laisse ouvertes sont
> tranchées dans [`docs/DECISIONS.md`](docs/DECISIONS.md).

---

## Sommaire

- [Architecture](#architecture)
- [Prérequis](#prérequis)
- [Démarrage rapide](#démarrage-rapide)
- [Comptes de démonstration](#comptes-de-démonstration)
- [Points d'entrée](#points-dentrée)
- [Configuration](#configuration)
- [Développement sans Docker](#développement-sans-docker)
- [Tests](#tests)
- [Exploitation](#exploitation)
- [Documentation](#documentation)
- [Dépannage](#dépannage)

---

## Architecture

Quatre composants, assemblés par `docker-compose.yml` :

| Composant | Technologie | Rôle |
|---|---|---|
| `web` | React 19 + TypeScript, servi par nginx | Interface **et** reverse proxy vers l'API — le navigateur ne voit qu'une seule origine (ADR-01) |
| `api` | Java 21, Spring Boot, Spring Security, JPA | Métier, autorisation, workflow, SLA, audit |
| `ai` | Python 3.12, Flask, scikit-learn | Classification et résumé (§12), local, sans appel externe |
| `postgres` | PostgreSQL 16 | Persistance ; schéma géré **exclusivement** par Flyway |

`mailhog` complète la pile en développement et en recette : il capture les e-mails sortants
au lieu de les livrer.

Le back-end suit les cinq couches du §9.3 du cahier des charges — `api`, `application`,
`domain`, `infrastructure`, `crosscutting` — avec une règle de dépendance stricte
(`api → application → domain`, `domain` ne dépend de personne). Voir
[`docs/CONCEPTION.md`](docs/CONCEPTION.md).

---

## Prérequis

| Outil | Version | Nécessaire pour |
|---|---|---|
| Docker Engine + Compose v2 | 24+ | **Tout ce qui suit dans « Démarrage rapide »** |
| Java (JDK) | 21 | Développement back-end hors Docker uniquement |
| Node.js | 22+ | Développement front-end hors Docker uniquement |
| Python | 3.12 | Développement du service IA hors Docker uniquement |

Rien d'autre n'est à installer : Maven arrive par le wrapper `./mvnw` du dépôt, et la base de
données n'existe que dans un conteneur.

Environ **3 Go d'espace disque** sont nécessaires pour les images.

---

## Démarrage rapide

```bash
git clone <url-du-dépôt> smartflow && cd smartflow

# 1. La configuration. DB_PASSWORD n'a aucune valeur par défaut : la pile refuse de
#    démarrer sans lui, volontairement (§8 - aucun secret dans le code).
cp .env.example .env
$EDITOR .env          # au minimum, remplacer DB_PASSWORD

# 2. La pile entière, images comprises.
docker compose up -d --build --wait
```

`--wait` rend la main quand les *healthchecks* passent au vert, pas dès que les conteneurs
démarrent : si la commande se termine sans erreur, la base est migrée et l'API répond.

Ouvrir **<http://localhost:5173>** et se connecter avec un compte du tableau ci-dessous.

Pour activer les fonctions d'IA (désactivées par défaut, §12.2) :

```bash
AI_ENABLED=true docker compose up -d       # ou AI_ENABLED=true dans .env
```

Pour tout arrêter :

```bash
docker compose down        # conserve les données
docker compose down -v     # supprime aussi la base et les pièces jointes
```

---

## Comptes de démonstration

Créés par la migration `V5__seed_demo_data.sql` (et complétés par `V9`). **Mot de passe
unique : `Password123!`** — données de démonstration uniquement, jamais un environnement
réel.

| Compte | Rôle (§5) | Périmètre | Ce qu'il montre |
|---|---|---|---|
| `amina.idrissi@smartflow.local` | Demandeur | Ses dossiers | Catalogue, brouillon, soumission, suivi |
| `leila.chraibi@smartflow.local` | Demandeur | Ses dossiers | Le même, sur l'autre service |
| `youssef.amrani@smartflow.local` | Manager | Équipe Support Poste de travail | Valider, rejeter, retourner |
| `sara.bennis@smartflow.local` | Agent | Équipe Support Poste de travail | « Mes tâches », affectation, traitement, clôture |
| `mehdi.ouazzani@smartflow.local` | Agent | Équipe Support Poste de travail | Second agent : réaffectation et affectation automatique |
| `karim.elfassi@smartflow.local` | Responsable de service | Service Informatique | Tableau de bord, export CSV, escalades |
| `fatima.squalli@smartflow.local` | Manager | Équipe Achats | Le circuit Achats |
| `reda.bakkali@smartflow.local` | Agent | Équipe Achats | Le circuit Achats |
| `nawal.idrissi@smartflow.local` | Responsable de service | Service Achats | Pilotage du second service |
| `nadia.ziani@smartflow.local` | Administrateur fonctionnel | Global | **Administration** : référentiels, formulaires, workflows, utilisateurs |
| `omar.tazi@smartflow.local` | Administrateur technique | Global | Page de diagnostic (§15.3) |
| `hicham.alaoui@smartflow.local` | Auditeur | Global | Journal d'audit, lecture seule |

Deux processus pilotes sont livrés configurés de bout en bout (§2.1) : **Support
Informatique** et **Achats**.

---

## Points d'entrée

| Adresse | Quoi |
|---|---|
| <http://localhost:5173> | L'application (et le reverse proxy vers l'API) |
| <http://localhost:5173/api/v1/...> | L'API telle que le navigateur la voit — c'est l'URL à utiliser pour tester |
| <http://localhost:8080/swagger-ui.html> | Documentation OpenAPI interactive (§17.1) |
| <http://localhost:8080/actuator/health> | Santé du back-end (§15.3) |
| <http://localhost:5000/health> | Santé du service IA (§15.3) |
| <http://localhost:8025> | MailHog — tous les e-mails envoyés par l'application |

Le port `8080` est exposé pour le diagnostic et Swagger. **Les appels applicatifs passent par
`5173`** : c'est la même origine que le SPA, donc le cookie de session et le jeton CSRF s'y
comportent comme en production (ADR-01).

---

## Configuration

Toute la configuration passe par des variables d'environnement, jamais par le code (§8). Le
fichier `.env` est lu par `docker compose`.

| Variable | Défaut | Effet |
|---|---|---|
| `DB_NAME` / `DB_USER` | `smartflow` | Base et rôle PostgreSQL |
| `DB_PASSWORD` | **aucun** | Obligatoire : la pile refuse de démarrer sans |
| `AI_ENABLED` | `false` | Active les appels au service IA. À `false`, l'application reste pleinement utilisable, `AiClient` refuse l'appel sans le tenter (§12.2) |
| `LOG_LEVEL` | `INFO` | Niveau de log du code SmartFlow (§15.3) |
| `LOG_LEVEL_ROOT` | `INFO` | Niveau de log de Spring, Hibernate, etc. |
| `SHOW_SQL` | `false` | Rejoue chaque requête SQL dans les logs |

D'autres réglages sont **administrables dans l'application** plutôt que par variable
d'environnement, parce que le §6.10 en fait un écran : extensions de fichier acceptées,
taille maximale, durée des sessions, seuil d'alerte SLA, fenêtre de réouverture, délai
d'archivage. Ils vivent dans la table `system_parameters` et se modifient dans
**Administration → Paramètres généraux** (compte `nadia.ziani`), sans redémarrage.

---

## Développement sans Docker

Les trois composants se lancent séparément ; seule la base reste dans un conteneur.

```bash
# Base de données seule
docker compose up -d postgres

# Back-end (port 8080) - Flyway applique les migrations au démarrage
cd backend
DB_NAME=smartflow DB_USER=smartflow DB_PASSWORD=<votre-mot-de-passe> ./mvnw spring-boot:run

# Front-end (port 5173, proxy vers 8080 via vite.config.ts)
cd frontend && npm install && npm run dev

# Service IA (port 5000)
cd ai-service && pip install -r requirements.txt && python app.py
```

**Le schéma de base ne se modifie jamais à la main** : `ddl-auto=validate`, Flyway seul fait
foi, et une migration publiée ne se réécrit pas — toute correction passe par une migration
suivante.

---

## Tests

```bash
# Back-end : unitaires + intégration (Testcontainers démarre un vrai PostgreSQL)
cd backend && ./mvnw clean verify

# Front-end
cd frontend && npm run lint && npm test && npm run build

# Service IA
cd ai-service && pip install -r requirements-dev.txt && pytest

# API de bout en bout, contre la pile réelle (docker compose doit tourner)
npx newman run postman/SmartFlow.postman_collection.json \
    -e postman/SmartFlow.postman_environment.json
```

Les tests d'intégration appliquent les **vraies migrations Flyway**, jamais un schéma généré
depuis les entités : une migration cassée fait échouer la suite.

Le pipeline GitHub Actions (`.github/workflows/ci.yml`) rejoue tout cela, plus l'analyse de
dépendances, SpotBugs, ruff, un contrôle OWASP ZAP de base et la construction des images
Docker versionnées depuis le tag Git.

---

## Exploitation

Sauvegarde, restauration, mise à jour, retour arrière, diagnostic et journaux :
**[`docs/EXPLOITATION.md`](docs/EXPLOITATION.md)**.

```bash
./scripts/backup.sh                      # -> backups/smartflow-<horodatage>.dump
./scripts/restore.sh backups/<fichier>   # restauration (destructive, demande confirmation)
```

---

## Documentation

| Document | Contenu |
|---|---|
| [`docs/Cahier_des_charges_PFA_SmartFlow.pdf`](docs/Cahier_des_charges_PFA_SmartFlow.pdf) | Le cahier des charges — source de vérité |
| [`docs/CONCEPTION.md`](docs/CONCEPTION.md) | Dossier de conception : architecture, modèle de données, règles de sécurité (§17.1) |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Journal des décisions d'architecture (ADR-01 à ADR-24) |
| [`docs/CAHIER_DE_RECETTE.md`](docs/CAHIER_DE_RECETTE.md) | 40 scénarios de recette et le PV d'exécution (§17.1) |
| [`docs/GUIDE_UTILISATEUR.md`](docs/GUIDE_UTILISATEUR.md) | Guide court par rôle (§17.1) |
| [`docs/EXPLOITATION.md`](docs/EXPLOITATION.md) | Procédures d'exploitation (§15.3) |
| [`docs/DEMONSTRATION.md`](docs/DEMONSTRATION.md) | Scénario de démonstration finale (§17.3) |
| [`docs/maquettes/`](docs/maquettes/) | Maquettes des dix écrans principaux |
| [`ai-service/EVALUATION.md`](ai-service/EVALUATION.md) | Comparaison des approches IA et choix motivé (§12.2) |
| [`postman/README.md`](postman/README.md) | Collection de tests API |

---

## Dépannage

**`docker compose up` échoue sur `DB_PASSWORD` requis.**
`.env` est absent ou n'en contient pas. `cp .env.example .env`, puis renseigner la valeur.

**L'API redémarre en boucle.**
`docker compose logs api`. Presque toujours Flyway : soit une migration échoue, soit le
schéma d'un volume existant ne correspond pas aux migrations du code
(`ddl-auto=validate` refuse alors de démarrer, volontairement). Sur un environnement de
démonstration, `docker compose down -v && docker compose up -d --build` repart d'une base
vierge — **cela supprime les données**.

**Le dépôt d'une pièce jointe échoue.**
Vérifier l'extension et la taille dans **Administration → Paramètres généraux** : la liste
blanche et le plafond sont administrables (RG-09). Au-delà de 20 Mo, c'est nginx qui refuse
en amont (`client_max_body_size`).

**Les fonctions d'IA répondent « service désactivé ».**
Comportement attendu tant que `AI_ENABLED` vaut `false` (§12.2). Le reste de l'application
n'en dépend pas.

**Aucun e-mail ne part.**
C'est voulu : MailHog les capture tous. Ils sont consultables sur <http://localhost:8025>.
Une notification *dans l'application* est écrite même quand l'e-mail est désactivé par les
préférences du destinataire (ADR-12).

**Un compte ne peut plus se connecter après plusieurs essais.**
Verrouillage temporaire après N échecs (ADR-13). Le message reste volontairement identique à
celui d'un mot de passe erroné — un compte verrouillé ne doit pas être distinguable. Un
administrateur fonctionnel peut déverrouiller depuis **Administration → Utilisateurs**.
