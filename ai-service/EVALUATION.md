# Évaluation de la classification IA (§12.2, §12.3)

Sortie réelle de `python evaluate.py` (jeu de données : `data/training_data.csv`, 468
lignes générées par gabarit — `data/generate_dataset.py`).

**Ce que le service exécute aujourd'hui** (ADR-21, appliqué) : `HybridClassifier` —
**catégorie** par `MlClassifier`, **priorité** par `RuleBasedClassifier`. Les chiffres
ci-dessous sont ce qui a motivé cet assemblage, et la ligne HYBRIDE de chaque tableau
mesure l'assemblage lui-même, pas seulement ses deux moitiés.

Ce document a été **corrigé après coup** : sa première version ne rapportait que le
protocole 1 ci-dessous, dont elle tirait une conclusion que le protocole 2 renverse. Voir
ADR-21 (`docs/DECISIONS.md`) pour la décision qui en découle. La version initiale est
conservée dans l'historique Git plutôt que réécrite en silence — §12.3 demande une
évaluation, et une évaluation qu'on corrige quand elle s'est trompée vaut mieux qu'une
évaluation qu'on maquille.

**Chiffres re-mesurés après deux correctifs de `RuleBasedClassifier`.** Confier la
priorité aux règles a immédiatement fait apparaître qu'elles lisaient une urgence **niée**
comme une urgence : « ce n'est pas urgent » ressortait en `HIGH`, « ce n'est pas bloquant »
en `CRITICAL`. Deux causes, corrigées ensemble :

1. **Les mots-clés se contiennent** — « urgent » est un morceau de « pas urgent », les deux
   se déclenchaient donc ensemble et l'égalité se tranchait par l'ordre de déclaration du
   dictionnaire. À égalité, le mot-clé le plus long l'emporte désormais.
2. **La négation n'était pas vue du tout** — une occurrence précédée d'un marqueur de
   négation (« pas », « sans », « aucun »…) **dans la même proposition** ne compte plus.
   La borne de proposition n'est pas cosmétique : sans elle, « … tant que ce n'est pas
   résolu, urgence maximale » retombait sur `MEDIUM` (74 % → 50 % sur le pli 2b concerné).
   La négation ne s'applique qu'à l'urgence : nier un mot de sujet n'en change pas le sujet.

Aucune formulation du jeu de données n'a été recopiée dans la liste de mots-clés — ce
classifieur sert de référence de comparaison, y importer une phrase du jeu de test
fausserait la comparaison même qu'il sert à établir. Tous les chiffres ci-dessous sont
postérieurs à ces correctifs, et sont donc **meilleurs pour les règles** que ceux cités
dans ADR-21, rédigé avant qu'ils n'existent (le sens de la décision, lui, est inchangé —
l'écart se creuse plutôt qu'il ne se referme).

---

## Les deux protocoles, et pourquoi il en fallait deux

`data/generate_dataset.py` compose chaque ligne ainsi :

```
description = un sujet parmi 39  +  UNE des 12 phrases d'urgence figées
category    = déterminée par le sujet
priority    = déterminée UNIQUEMENT par la phrase d'urgence finale
```

Vérifié par `evaluate.py` lui-même (`_urgency_phrase_of` lève une `AssertionError`
autrement) : **les 468 lignes se terminent par l'une de ces 12 phrases, sans exception.**

| Protocole | Comment on découpe | Ce qu'il mesure réellement |
|---|---|---|
| **1. Split par ligne** (`--split`, historique) | `train_test_split`, 80/20 stratifié, graine 42 | La reconnaissance d'une formulation **déjà vue** : les mêmes sujets et les mêmes 12 phrases d'urgence sont des deux côtés du découpage |
| **2. Split par groupe** (`--grouped`) | Des **gabarits entiers** (un sujet complet, ou une formulation d'urgence complète) sont retirés de l'entraînement | La généralisation à une formulation **jamais vue** — la seule question qui se pose en service |

Le protocole 1 n'est pas faux, il répond simplement à une autre question que celle qu'on
croyait lui poser. Sur un jeu de données composé par gabarit, un découpage par ligne laisse
fuir le gabarit d'un côté à l'autre : le modèle est noté sur des phrases dont il a déjà vu
des exemplaires mot pour mot.

---

## Protocole 1 — split par ligne (résultat historique)

| Approche | Exactitude catégorie | Exactitude priorité |
|---|---|---|
| Règles/mots-clés (`RuleBasedClassifier`) | 85 % | 89 % |
| TF-IDF + régression logistique (`MlClassifier`) | **100 %** | **100 %** |
| Hybride servi (`HybridClassifier`) | **100 %** | 89 % |

C'est ce tableau — dans sa version d'alors, 82 % / 81 % pour les règles — et lui seul qui
avait motivé ADR-16 (« MlClassifier est l'approche active »). Sur ce protocole, l'hybride
paraît en retrait : c'est attendu, et c'est précisément ce que le protocole 2 corrige — ici
le ML est noté sur des phrases d'urgence qu'il a déjà vues mot pour mot.

### Où l'approche par règles échoue sur ce protocole

Confusion systématique entre `RESEAU` et `ACCES_COMPTE` :

```
                 MATERIEL LOGICIEL ACCES_COMPTE RESEAU ACHAT AUTRE
MATERIEL             19       0         0          0     0     0
LOGICIEL              0      17         0          0     0     0
ACCES_COMPTE          0       0        15          0     2     0
RESEAU                0       0         5          9     0     0
ACHAT                 3       4         0          0     8     0
AUTRE                 0       0         0          0     0    12
```

8 des 14 exemples réellement `RESEAU` sont classés `ACCES_COMPTE` : les deux listes de
mots-clés partagent du vocabulaire de connexion (« connexion », « accès », « vpn »), qu'une
simple liste blanche ne peut pas départager sans contexte. Le même effet touche `ACHAT`,
confondu avec `MATERIEL`/`LOGICIEL`.

Côté priorité, il ne reste plus d'inversion : les 8 erreurs sont des `LOW` rendus en
`MEDIUM`, et l'erreur restante est un `LOW` rendu `CRITICAL`.

```
              LOW MEDIUM HIGH CRITICAL
LOW            13     8    0      1
MEDIUM          0    25    0      1
HIGH            0     0   25      0
CRITICAL        0     0    0     21
```

C'est la trace directe des correctifs de négation : une urgence niée ne fait plus monter la
priorité, elle cesse simplement de compter, et la demande retombe sur le défaut `MEDIUM`.
Sous-estimer d'un cran une demande sans urgence coûte beaucoup moins qu'annoncer `HIGH`
sur une phrase qui dit le contraire — et RG-10 laisse de toute façon le dernier mot à
l'humain qui qualifie.

---

## Protocole 2 — split par groupe (le résultat qui compte)

### 2a. Sujets inédits (formulation d'urgence connue, sujet jamais vu)

```
  Pli 1 (10 sujets retirés, test=120) : catégorie ML  69.2% / Règles  90.0%   |   priorité ML 100.0% / Règles  91.7%   |   HYBRIDE  69.2% /  91.7%
  Pli 2 (10 sujets retirés, test=120) : catégorie ML  60.0% / Règles  90.0%   |   priorité ML 100.0% / Règles  89.2%   |   HYBRIDE  60.0% /  89.2%
  Pli 3 (10 sujets retirés, test=120) : catégorie ML  36.7% / Règles  60.0%   |   priorité ML 100.0% / Règles  91.7%   |   HYBRIDE  36.7% /  91.7%
  Pli 4 ( 9 sujets retirés, test=108) : catégorie ML  43.5% / Règles 100.0%   |   priorité ML 100.0% / Règles  91.7%   |   HYBRIDE  43.5% /  91.7%
```

**La catégorie ML s'effondre de 100 % à 36-69 %, et les règles la battent sur les quatre
plis.** La priorité ML reste à 100 % ici, mais c'est un artefact du protocole : les 12
phrases d'urgence sont toutes restées dans l'entraînement, donc le modèle les reconnaît
mot pour mot — ce que 2b démonte. La colonne HYBRIDE reprend, par construction, la
catégorie du ML et la priorité des règles : c'est le seul chiffre des trois qui décrit ce
qu'un utilisateur reçoit.

### 2b. Formulations d'urgence inédites (sujet connu, urgence jamais vue)

```
  Pli 1 (formulation #1 de chaque priorité retirée, test=156) : catégorie ML 100.0% / Règles  84.6%   |   priorité ML   0.6% / Règles  99.4%   |   HYBRIDE 100.0% /  99.4%
  Pli 2 (formulation #2 de chaque priorité retirée, test=156) : catégorie ML 100.0% / Règles  84.6%   |   priorité ML   3.2% / Règles  99.4%   |   HYBRIDE 100.0% /  99.4%
  Pli 3 (formulation #3 de chaque priorité retirée, test=156) : catégorie ML 100.0% / Règles  84.6%   |   priorité ML   0.0% / Règles  74.4%   |   HYBRIDE 100.0% /  74.4%
```

**La priorité ML tombe à 0-3 %.** Pas « dégradée » : effondrée sous le hasard (25 % pour
quatre classes équiprobables), ce qui est la signature d'une mémorisation pure — privé des
12 phrases apprises, le modèle prédit systématiquement la mauvaise classe plutôt qu'au
hasard. Les règles, elles, tiennent entre 74 % et 99 % : elles n'ont rien à « reconnaître »,
elles cherchent un vocabulaire d'urgence qui ne dépend d'aucun entraînement. Le pli 3 reste
le plus faible (74 %) parce que sa formulation LOW retirée, « simple remarque sans caractère
urgent », n'emploie aucun mot-clé de la liste : la négation y neutralise bien le « urgent »,
la demande retombe alors sur le défaut MEDIUM au lieu de LOW — une erreur d'un cran, pas
une inversion.

### Confirmation hors jeu de données

Six demandes rédigées à la main dans du vocabulaire métier réel, n'empruntant aucun
gabarit. Elles sont désormais **versionnées** (`data/manual_cases.csv`) et rejouables par
`python evaluate.py --manual` sur les trois stratégies, entraînées sur la totalité du jeu
de données — donc dans les conditions exactes du service. La première version de ce
document rapportait le même contrôle sur six demandes équivalentes qui, elles, n'avaient
été écrites nulle part : un chiffre invérifiable ne vaut pas mieux que pas de chiffre.

| Demande | Attendu | Règles | ML | **Hybride (servi)** |
|---|---|---|---|---|
| Station d'accueil hors service | MATERIEL / HIGH | ✓ / ✓ | ✓ / ✓ | **✓ / ✓** |
| Panne du switch du 3e étage | RESEAU / CRITICAL | ✓ / ✓ | ✓ / ✓ | **✓ / ✓** |
| Authentification à deux facteurs en échec | ACCES_COMPTE / HIGH | ✗ `LOGICIEL` / ✓ | ✓ / ✓ | **✓ / ✓** |
| Macro Excel en erreur depuis la mise à jour | LOGICIEL / MEDIUM | ✓ / ✓ | ✓ / ✗ `LOW` | **✓ / ✓** |
| Renouvellement d'abonnement de veille | ACHAT / LOW | ✓ / ✓ | ✓ / ✓ | **✓ / ✓** |
| Question sur la charte informatique | AUTRE / LOW | ✗ `ACCES_COMPTE` / ✓ | ✗ `ACCES_COMPTE` / ✓ | **✗ `ACCES_COMPTE` / ✓** |
| | | 4/6 · 6/6 | 5/6 · 5/6 | **5/6 · 6/6** |

L'hybride est le meilleur des trois sur ces six cas, et c'est bien la composition qui le
produit : il prend la catégorie là où le ML gagne (le MFA, que les mots-clés rangent en
`LOGICIEL`) et la priorité là où les règles gagnent (la macro Excel, que le ML rend `LOW`).
Six lignes ne sont pas un échantillon — elles illustrent, elles ne mesurent pas. Le seul
échec commun aux trois, la question sur la charte rangée en `ACCES_COMPTE`, est d'ailleurs
défendable : la demande parle bel et bien de mots de passe et d'accès.

---

## Conclusion

Le « 100 % contre 82 % » du protocole 1 ne mesurait pas ce que §12.2 demande de comparer.
Sur le seul protocole qui décrit le comportement en service :

- **Catégorie** : aucune des deux ne domine partout. Les règles sont devant sur sujet
  inédit (60-100 % contre 37-69 %), le ML devant dès que le sujet est connu (100 % contre
  85 %), et il tranche des confusions que la liste de mots-clés ne peut pas trancher
  (`RESEAU` / `ACCES_COMPTE` partagent « connexion », « accès », « vpn »).
- **Priorité** : les règles gagnent sans discussion (74-99 % contre 0-3 %). L'urgence
  s'exprime en français par un petit vocabulaire fermé et récurrent (« bloquant »,
  « urgence », « pas prioritaire ») — exactement ce qu'une liste de mots-clés capture bien
  et ce qu'un modèle entraîné sur 12 phrases ne peut pas apprendre.

**Le choix final est donc tranché par cible, et c'est ce que le service exécute** : catégorie
au `MlClassifier`, priorité au `RuleBasedClassifier`, assemblés par `HybridClassifier`. Le
catalogue de services d'une DSI est un vocabulaire fini et connu d'avance — un terrain où
l'apprentissage garde un avantage réel une fois le catalogue couvert par le jeu de données ;
l'urgence, elle, se dit avec les mêmes quelques mots quel que soit le sujet, et n'a rien à
apprendre. Le raisonnement complet, et ce qu'il faudrait pour faire mieux, sont consignés
dans **ADR-21** (`docs/DECISIONS.md`).

**La limite à ne pas perdre de vue** : sur un sujet absent du jeu d'entraînement, la
catégorie servie tombe à 37-69 %. Elle est l'exact inverse de la priorité — solide sur
formulation inédite, fragile sur sujet inédit. Élargir le jeu de données au catalogue réel
(et non aux 39 sujets générés) est le levier qui la ferait progresser ; c'est la première
chose à faire si le module devait être mis en service au-delà du périmètre pilote.

## Ce que cette évaluation ne peut pas établir

Aucun chiffre ci-dessus ne mesure la performance sur de **vraies** demandes : le jeu de
données est intégralement synthétique (§12.2 autorise ce point de départ — « une base
d'exemples anonymisés ou générés » — mais ne le confond pas avec une validation). Un jeu
généré par gabarit plafonne toute évaluation hors ligne : il ne contient ni fautes de
frappe, ni demandes à double sujet, ni formulations ambiguës, ni le vocabulaire propre à
un service donné. §12.3 classe d'ailleurs le « taux d'acceptation/correction des
suggestions » comme le critère d'**utilité** réel du module, mesurable seulement après mise
en service — c'est lui qui tranchera, et `AiAnalysis.validatedBy`/`acceptedValue`
(RG-10/ADR-16) enregistrent déjà exactement la donnée nécessaire pour le calculer.
