# Évaluation de la classification IA (§12.2, §12.3)

Sortie réelle de `python evaluate.py` (jeu de données : `data/training_data.csv`, 468
lignes générées par gabarit — `data/generate_dataset.py` —, split 80/20 stratifié par
catégorie, graine aléatoire fixe `42` pour la reproductibilité).

## Résumé

| Approche | Exactitude catégorie | Exactitude priorité |
|---|---|---|
| Règles/mots-clés (`RuleBasedClassifier`) | 82 % | 81 % |
| TF-IDF + régression logistique (`MlClassifier`) | 100 % | 100 % |

**Décision : `MlClassifier` est l'approche active** (voir ADR-16, `docs/DECISIONS.md`).
`RuleBasedClassifier` reste dans le code, testé, comme référence de comparaison — pas
supprimée après la décision, exactement ce que §12.2 demande de produire pour la justifier.

## Où l'approche par règles échoue

La matrice de confusion de catégorie montre une confusion systématique entre `RESEAU` et
`ACCES_COMPTE` :

```
                 MATERIEL LOGICIEL ACCES_COMPTE RESEAU ACHAT AUTRE
MATERIEL             19       0         0          0     0     0
LOGICIEL              0      17         0          0     0     0
ACCES_COMPTE          0       0        15          0     2     0
RESEAU                0       0         8          6     0     0
ACHAT                 3       4         0          0     8     0
AUTRE                 0       0         0          0     0    12
```

8 des 14 exemples réellement `RESEAU` sont classés `ACCES_COMPTE` : les deux listes de
mots-clés partagent du vocabulaire de connexion ("connexion", "accès", "vpn"), qu'une
simple liste blanche ne peut pas départager sans contexte. Le même effet touche `ACHAT`,
confondu avec `MATERIEL`/`LOGICIEL` (un achat de matériel ou de licence mentionne
naturellement le mot-clé de la catégorie qu'il concerne, pas seulement "achat"/"devis").

Côté priorité, l'approche par règles confond `LOW` avec `HIGH` et `MEDIUM` avec `CRITICAL` :
une phrase d'urgence contenant à la fois un mot-clé de faible priorité et, incidemment, un
terme proche d'un mot-clé de priorité plus élevée fait basculer la règle sur le mauvais
mot-clé "le mieux représenté", sans notion de contexte ou de négation.

`MlClassifier` (TF-IDF, bigrammes) n'a aucune de ces confusions sur ce jeu de test : les
n-grammes captent des tournures entières ("production est interrompue", "aucune urgence")
plutôt que des mots isolés, ce qui suffit à lever l'ambiguïté que la liste de mots-clés ne
sait pas résoudre.

## Limite assumée de ce résultat

Le score de 100 % de `MlClassifier` reflète en bonne partie la régularité du jeu de données
généré par gabarit (un nombre fini de formulations, réutilisées à l'identique entre
l'entraînement et le test) : il ne doit pas être lu comme une garantie de performance sur
des formulations réelles totalement inédites. Ce que ce résultat établit avec certitude,
c'est la comparaison relative demandée par §12.2 — sur le même jeu de test, dans les mêmes
conditions, l'approche par apprentissage résout des confusions que l'approche par règles ne
résout jamais, quel que soit le jeu de mots-clés qu'on lui donne, tant que le contexte est
nécessaire pour trancher. §12.3 classe d'ailleurs "taux d'acceptation/correction des
suggestions" comme le critère d'**utilité** réel du module, mesurable seulement après mise
en service — cette évaluation hors-ligne reste le préalable que §12.2 impose avant cette
mesure-là, pas un substitut à elle.
