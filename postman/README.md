# Tests API — Postman / Newman

**Ancrage : §14** (« Tests API : scénarios nominaux, erreurs, droits et pagination »,
outillage « Postman/Newman ou tests automatisés »).

Cette collection est la partie **rejouable automatiquement** de la stratégie de tests. Elle
complète — sans le remplacer — le [cahier de recette](../docs/CAHIER_DE_RECETTE.md), qui est
une exécution manuelle par rôle sur les processus pilotes.

| | |
|---|---|
| Requêtes | 43, en 7 dossiers |
| Assertions | 93 |
| Cible | La pile réelle (`docker compose`), à travers le reverse proxy du front |

---

## Exécuter

La pile doit tourner :

```bash
docker compose up -d --build --wait
npx newman run postman/SmartFlow.postman_collection.json \
    -e postman/SmartFlow.postman_environment.json
```

Dans l'application Postman : importer les deux fichiers, sélectionner l'environnement
*SmartFlow - recette locale*, puis lancer la collection entière (l'ordre des requêtes
compte).

Le job `api-tests` de `.github/workflows/ci.yml` fait exactement cela à chaque push, et
publie un rapport JUnit.

---

## Ce qui est couvert

| Dossier | Ce qu'il vérifie |
|---|---|
| **1. Authentification et CSRF** | Jeton CSRF délivré, appel mutant sans jeton refusé (403), mot de passe erroné (401 au format §11.1, sans jamais révéler qu'un compte est verrouillé), route protégée anonyme (401), session décrite par `/auth/me` sans jamais exposer de secret |
| **2. Catalogue et formulaires** | Liste des services, **recherche par mot-clé** (§6.2), types de demande, formulaire publié. Les valeurs de test sont **déduites du formulaire renvoyé**, jamais codées en dur : la collection suit la configuration, et un formulaire reconfiguré ne la casse pas |
| **3. Parcours nominal** | Brouillon → soumission → référence `DEM-AAAA-NNNNNN` (RG-01) → **frise commençant par `SUBMIT`** (§3.4, ADR-23) → détail portant le statut SLA matérialisé et `availableActions[]` |
| **4. Erreurs et validation** | Champ obligatoire manquant (§14.1 #2 — `VALIDATION_ERROR` + `fieldErrors`), ressource inexistante (404), corps JSON malformé (400 en JSON, jamais une page HTML ni une 500), paramètre de requête obligatoire absent |
| **5. Droits et périmètres** | §14.1 #6 — dossier d'un autre service invisible **en 404, jamais 403** (lecture, historique, commentaire) ; écran d'administration inatteignable pour une demandeuse ; auditeur qui **lit** le journal d'audit mais ne peut **pas** commenter (ADR-11) |
| **6. Pagination et tri** | Enveloppe normalisée (§11.1), taille respectée, seconde page distincte de la première, tri décroissant appliqué, filtre par statut, tableau de bord et export CSV (§6.9) |
| **7. Commentaires et mentions** | ADR-24 — une mention habilitée est retenue, une mention hors périmètre est **silencieusement ignorée** (le texte reste intact, aucune adresse n'est renvoyée), commentaire vide refusé, et la personne mentionnée retrouve bien sa notification (§6.8) |

---

## Deux propriétés à préserver

**Elle passe par `localhost:5173`, jamais par `localhost:8080`.** C'est l'origine que voit un
navigateur : le SPA et l'API y partagent le même domaine, donc le cookie de session et le
jeton CSRF s'y comportent comme en conditions réelles (ADR-01). Tester directement le port
back-end contournerait précisément ce que la configuration de sécurité protège.

**Elle est idempotente.** Chaque exécution crée ses propres demandes et n'en supprime aucune
(RG-02 l'interdit de toute façon). Elle peut donc être rejouée indéfiniment sur la même base
— c'est vérifié : deux exécutions consécutives donnent 93 assertions vertes.

---

## Le piège CSRF, à ne pas réintroduire

**Sous Newman, `pm.cookies` n'est pas fiable dans un script de PRÉ-REQUÊTE** — contrairement
à l'application Postman, où le même code fonctionne. L'en-tête `X-XSRF-TOKEN` n'était alors
jamais posé, et **chaque requête mutante échouait en 403, silencieusement, dès la première**.

La collection capture donc le jeton depuis l'en-tête `Set-Cookie` de la **réponse**, dans un
script de **test** (fiable), vers une variable de collection relue ensuite comme valeur
d'en-tête :

```javascript
const setCookies = pm.response.headers.all().filter(h => h.key.toLowerCase() === 'set-cookie');
setCookies.forEach(h => {
    const match = /XSRF-TOKEN=([^;]+)/.exec(h.value);
    if (match) { pm.collectionVariables.set('csrfToken', match[1]); }
});
```

Ce bloc est répété sur `/auth/csrf` et sur chaque connexion — Spring Security renouvelle la
session, donc le jeton, à chaque authentification réussie. **Ne revenez jamais à
`pm.cookies.get(...)` en pré-requête pour cette collection.**

---

## Faire évoluer la collection

Le fichier JSON est volumineux et pénible à modifier à la main. Quelques règles qui évitent
les régressions déjà rencontrées :

- **Un seul jeu de cookies pour toute l'exécution** : Newman n'a qu'un bocal. Changer de rôle
  veut dire se reconnecter, et les requêtes d'un rôle doivent suivre immédiatement sa
  connexion.
- **Ne codez pas en dur un identifiant de service, de type de demande ou de champ.** Capturez-
  le depuis une réponse antérieure : le jeu de démonstration évolue par migration, et une
  collection qui en fige les identifiants casse au premier `V…` suivant.
- **Une apostrophe dans un nom de requête** est injectée dans une chaîne JavaScript : elle
  doit être échappée, sinon le script du test ne compile pas (constaté).
- `GET /requests` est borné à la **propriété** du dossier (RG-06) : c'est une demandeuse qui a
  de quoi paginer, pas un responsable de service — sa vue à lui est le tableau de bord.
- `serviceId` d'un tableau de bord est l'identifiant d'une **fiche de catalogue**, pas celui
  d'un département.
