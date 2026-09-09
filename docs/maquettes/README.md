# Maquettes SmartFlow IT — specification visuelle

Huit maquettes haute fidelite, 1440 × 960. Elles remplacent l'habillage actuel du
front-end **sans changer les donnees, les routes ni les appels API existants**.

| Fichier | Page React correspondante |
|---|---|
| `01-connexion.png` | `pages/LoginPage.tsx` |
| `02-accueil.png` | `pages/HomePage.tsx` |
| `03-catalogue.png` | `pages/CataloguePage.tsx` |
| `04-nouvelle-demande.png` | `pages/NewRequestPage.tsx` |
| `05-detail-demande.png` | `pages/RequestDetailPage.tsx` |
| `06-mes-taches.png` | `pages/TasksPage.tsx` |
| `07-tableau-de-bord.png` | `pages/DashboardPage.tsx` |
| `08-administration.png` | `pages/AdminWorkflowsPage.tsx` (gabarit pour toutes les pages Admin*) |
| `09-notifications.png` | `pages/NotificationsPage.tsx` |
| `10-profil.png` | `pages/ProfilePage.tsx` |

---

## Jetons de couleur — a mettre dans `src/index.css`

```css
:root {
  --color-bg:          #F4F6FA;
  --color-surface:     #FFFFFF;
  --color-border:      #E3E8EF;
  --color-border-strong:#CDD5E0;

  --color-text:        #101828;
  --color-text-muted:  #667085;
  --color-text-faint:  #98A2B3;

  --color-primary:      #2454FF;
  --color-primary-hover:#1A3FD6;
  --color-primary-soft: #EBF0FF;

  --color-navy:        #111A34;   /* barre laterale */
  --color-navy-hover:  #1D2A4D;
  --color-navy-text:   #9AA6C4;

  --color-success:     #12B76A;  --color-success-bg: #E8F8F0;
  --color-warning:     #F79009;  --color-warning-bg: #FEF3E2;
  --color-danger:      #D92D20;  --color-danger-bg:  #FEECEA;
  --color-violet:      #7A5AF8;  --color-violet-bg:  #F1EDFE;

  --radius:      8px;
  --radius-card: 12px;
}
```

`--color-primary` est inchange par rapport a l'existant : le reste de la palette est
enrichi autour.

## Typographie et espacement

- Titre de page 19 px gras · titre de carte 15 px gras · corps 13 px · secondaire 12 px ·
  legende 11 px · references (`DEM-2026-000123`) en **police monospace** 11 px.
- Cartes : fond blanc, bordure 1 px `--color-border`, rayon 12 px, padding interne 22–26 px.
- Gouttiere entre cartes : 20 px. Marge du contenu : 32 px.
- Pas d'ombre portee. La hierarchie vient de la bordure et du fond, pas de l'elevation.

## Structure applicative

- **Barre laterale fixe, 248 px, fond `--color-navy`.** Element actif : fond
  `--color-navy-hover`, barre `--color-primary` de 3 px a gauche, texte blanc gras.
  Pastille rouge de compteur sur « Notifications ». Bloc utilisateur en bas.
- **Barre superieure, 68 px, fond blanc**, bordure basse. Titre + sous-titre a gauche,
  recherche, langue et avatar a droite.
- Contenu sur fond `--color-bg`.

## Composants a extraire

| Composant | Ou il apparait |
|---|---|
| `StatCard` | accueil, tableau de bord |
| `SlaBadge` (`Dans le delai` / `A risque` / `En retard`) | accueil, taches, detail, tableau de bord |
| `StatusBadge` (etape, priorite, publication) | partout |
| `DataTable` (en-tete `#FAFBFD`, lignes 58 px, separateurs) | taches, admin, audit |
| `Timeline` (pastille + trait vertical) | detail, formulaire |
| `FilterBar` | taches, tableau de bord, audit |
| `EmptyState` | toute liste vide |

---

## Regles a ne pas casser

Ces details ne sont pas decoratifs, ils portent des exigences du cahier des charges.

1. **Zone d'actions du detail (05)** — les boutons se rendent depuis `availableActions[]`
   renvoye par le serveur. Ne jamais afficher un bouton selon le role cote React (§11.1).
   La legende monospace « calculees par le serveur » est une note de maquette, pas du texte
   a afficher.
2. **Deux horloges SLA distinctes** — prise en charge et resolution, avec les trois etats
   `dans le delai / a risque / en retard` (§6.7). Jamais une seule barre.
3. **Bloc Aide IA (05)** — toujours a cote des donnees, jamais dedans. Affiche le niveau de
   confiance et reste corrigeable (RG-10).
4. **Barre d'actions en masse (06)** — uniquement priorite et affectation. La mention
   « validation et cloture en masse indisponibles » doit rester visible (§6.6).
5. **Champ conditionnel (04)** — encadre a gauche par un liseré `--color-primary`. Un seul
   motif supporte : afficher X si Y vaut Z.
6. **Banniere de version de workflow (08)** — le message « publier la v4 n'affectera pas les
   demandes en cours » materialise RG-03 dans l'interface.
7. **Erreur de connexion (01)** — message unique quel que soit le motif d'echec. Ne jamais
   distinguer « compte inconnu » de « mot de passe incorrect ».
8. **Accessibilite (§8)** — conserver l'anneau `:focus-visible` existant, les libelles
   explicites, les messages d'erreur rattaches au champ, et le contraste.
9. **Preferences de notification (09)** — les cinq alertes obligatoires (affectation,
   complement, decision, retard et escalade, cloture) s'affichent avec un interrupteur
   **desactive et non cliquable**, jamais masque. Le §6.8 exige des preferences limitees
   « pour eviter la desactivation des alertes obligatoires » : l'utilisateur doit voir
   qu'elles existent et qu'il ne peut pas les couper. Seul « resume hebdomadaire » est
   reellement basculable.
10. **Non lu (09)** — fond `#F7F9FF`, liseré `--color-primary` a gauche, titre en gras et
    pastille a droite. Trois marqueurs, pas un seul : le §8 demande de ne pas faire reposer
    une information sur la seule couleur.
11. **Champs en lecture seule (10)** — identifiant, rattachement et roles sont geres par
    l'administrateur (§6.1, §5.1). Fond `#F7F8FA`, texte `--color-text-faint`, mention
    « gere par l'administrateur ». Ne jamais les rendre editables cote client.
12. **Expiration de session (10)** — le compte a rebours est affiche en
    `--color-warning`, et la deconnexion invalide la session cote serveur (§6.1, ADR-01).

## Etats non dessines, a implementer quand meme

Liste vide, chargement, erreur reseau, 403, session expiree, televersement refuse.
Utiliser `EmptyState` et `ErrorBanner`, avec le meme vocabulaire francais.

## Responsive (§8)

Sous 1024 px : barre laterale repliee en menu, grilles KPI en 2 colonnes, colonne de droite
du detail passee sous la colonne principale, tableaux en defilement horizontal.