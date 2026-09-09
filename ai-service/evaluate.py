"""§12.3 - Critères d'évaluation du module IA : "Précision, rappel, F1-score et matrice de
confusion sur un jeu de test séparé" pour la classification. §12.2 demande de comparer
l'approche par règles/mots-clés à un modèle de classification simple et de justifier le
choix final par les résultats - ce script produit ces résultats (voir EVALUATION.md pour la
sortie effectivement obtenue et ADR-16/ADR-21, docs/DECISIONS.md, pour les décisions
qu'elle a motivées).

Deux protocoles, et la différence entre les deux est le résultat principal de ce script :

1. **Split par ligne** (`train_test_split`, historique) - chaque ligne du jeu de données
   part indépendamment en entraînement ou en test. Comme `data/generate_dataset.py`
   compose chaque description à partir d'un nombre fini de gabarits, les mêmes
   formulations se retrouvent des deux côtés du split : ce protocole mesure la capacité à
   reconnaître une formulation **déjà vue**, jamais à en généraliser une nouvelle.

2. **Split par groupe** (`--grouped`, ajouté après coup) - on retire du jeu
   d'entraînement des *gabarits entiers* (un sujet complet, ou une formulation d'urgence
   complète) et on teste dessus. C'est le seul des deux qui répond à la question posée en
   service : que fait le modèle sur une demande rédigée avec des mots qu'il n'a jamais
   vus ? Voir ADR-21 pour ce que cette mesure a changé.

Les deux protocoles évaluent trois stratégies, dont l'assemblage effectivement servi
(`HybridClassifier` : catégorie=ML, priorité=règles) - la colonne HYBRIDE des plis, ou son
propre rapport dans le protocole 1. Comparer les deux approches sans mesurer celle qu'on
livre laisserait le §12.2 à moitié satisfait.

Un troisième contrôle, hors jeu de données, complète les deux protocoles : `--manual`
rejoue `data/manual_cases.csv`, six demandes rédigées à la main en vocabulaire métier réel,
qui n'empruntent aucun gabarit. Aucune mesure sur un jeu généré ne peut dire ce que fait le
module sur une phrase que personne n'a fabriquée pour lui ; ces six cas ne le disent pas non
plus statistiquement (six lignes ne sont pas un échantillon), mais ils l'illustrent
concrètement et ils sont désormais versionnés, donc rejouables à l'identique.

Usage: python evaluate.py            (les deux protocoles)
       python evaluate.py --grouped  (le protocole par groupe seul)
       python evaluate.py --split    (le protocole par ligne seul)
       python evaluate.py --manual   (les six demandes rédigées à la main)
"""
import csv
import importlib.util
import sys
from pathlib import Path

from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import train_test_split

from classification import (CATEGORIES, PRIORITIES, HybridClassifier, MlClassifier,
                             RuleBasedClassifier)


def load_dataset():
    path = Path(__file__).parent / "data" / "training_data.csv"
    with path.open(encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    return rows


def evaluate(name, classifier, test_rows):
    category_true, category_pred = [], []
    priority_true, priority_pred = [], []
    for row in test_rows:
        prediction = classifier.classify(row["title"], row["description"])
        category_true.append(row["category"])
        category_pred.append(prediction.category)
        priority_true.append(row["priority"])
        priority_pred.append(prediction.priority)

    print(f"\n{'=' * 20} {name} {'=' * 20}")
    print("\n--- Catégorie ---")
    print(classification_report(category_true, category_pred, labels=CATEGORIES, zero_division=0))
    print("Matrice de confusion (lignes=réel, colonnes=prédit), ordre:", CATEGORIES)
    print(confusion_matrix(category_true, category_pred, labels=CATEGORIES))

    print("\n--- Priorité ---")
    print(classification_report(priority_true, priority_pred, labels=PRIORITIES, zero_division=0))
    print("Matrice de confusion (lignes=réel, colonnes=prédit), ordre:", PRIORITIES)
    print(confusion_matrix(priority_true, priority_pred, labels=PRIORITIES))


def train_ml(train_rows):
    return MlClassifier.train(
        [r["title"] for r in train_rows], [r["description"] for r in train_rows],
        [r["category"] for r in train_rows], [r["priority"] for r in train_rows])


def run_split_protocol(rows):
    """Protocole 1 - split par ligne (historique, celui d'ADR-16)."""
    print("\n" + "#" * 78)
    print("# PROTOCOLE 1 - split par ligne (test_size=0.2, stratifié, graine 42)")
    print("# Mesure la reconnaissance d'une formulation DÉJÀ VUE à l'entraînement.")
    print("#" * 78)

    train_rows, test_rows = train_test_split(rows, test_size=0.2, random_state=42,
                                              stratify=[r["category"] for r in rows])
    print(f"Jeu d'entraînement: {len(train_rows)} lignes - jeu de test: {len(test_rows)} lignes")

    ml = train_ml(train_rows)
    evaluate("Règles/mots-clés (RuleBasedClassifier)", RuleBasedClassifier(), test_rows)
    evaluate("TF-IDF + régression logistique (MlClassifier)", ml, test_rows)
    evaluate("Hybride catégorie=ML / priorité=règles (HybridClassifier, ADR-21)",
              HybridClassifier(ml, RuleBasedClassifier()), test_rows)


def _load_generator():
    """`data/generate_dataset.py` n'est pas un module importable (data/ n'est pas un
    paquet) : on le charge par chemin pour lire ses gabarits, seule source fiable des
    groupes. Déduire les groupes du CSV par découpage de chaîne marcherait presque, et
    "presque" n'est pas une base d'évaluation."""
    path = Path(__file__).parent / "data" / "generate_dataset.py"
    spec = importlib.util.spec_from_file_location("generate_dataset", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _urgency_phrase_of(row, all_phrases):
    for phrase in all_phrases:
        if row["description"].endswith(phrase):
            return phrase
    raise AssertionError(f"Ligne rattachable à aucun gabarit d'urgence: {row['description']!r}")


def accuracy(predicted, actual):
    return sum(p == a for p, a in zip(predicted, actual)) / len(actual)


def _accuracies(classifier, test_rows):
    predictions = [classifier.classify(r["title"], r["description"]) for r in test_rows]
    return (accuracy([p.category for p in predictions], [r["category"] for r in test_rows]),
            accuracy([p.priority for p in predictions], [r["priority"] for r in test_rows]))


def run_grouped_protocol(rows):
    """Protocole 2 - split par groupe : des gabarits ENTIERS sont retirés de
    l'entraînement, donc le jeu de test ne contient que des formulations inédites."""
    generator = _load_generator()
    all_phrases = [p for phrases in generator.URGENCY_PHRASES.values() for p in phrases]
    titles = sorted({r["title"] for r in rows})

    print("\n" + "#" * 78)
    print("# PROTOCOLE 2 - split par groupe (gabarits entiers retenus hors entraînement)")
    print("# Mesure la généralisation à une formulation JAMAIS VUE. C'est celui-ci qui")
    print("# décrit ce que le module fait en service (ADR-21).")
    print("#" * 78)
    print(f"{len(rows)} lignes, {len(titles)} sujets distincts, "
          f"{len(all_phrases)} formulations d'urgence distinctes.")
    print("Chaque description = un sujet + une formulation d'urgence : la priorité n'est")
    print("portée QUE par cette dernière phrase (data/generate_dataset.py).\n")

    print("--- 2a. Sujets inédits (le titre/la description du test n'ont jamais été vus) ---")
    print("     La formulation d'urgence, elle, reste connue.")
    for fold in range(4):
        held = set(titles[fold::4])
        train_rows = [r for r in rows if r["title"] not in held]
        test_rows = [r for r in rows if r["title"] in held]
        ml = train_ml(train_rows)
        ml_cat, ml_prio = _accuracies(ml, test_rows)
        rb_cat, rb_prio = _accuracies(RuleBasedClassifier(), test_rows)
        hy_cat, hy_prio = _accuracies(HybridClassifier(ml, RuleBasedClassifier()), test_rows)
        print(f"  Pli {fold + 1} ({len(held):2d} sujets retirés, test={len(test_rows):3d}) : "
              f"catégorie ML {ml_cat:6.1%} / Règles {rb_cat:6.1%}   |   "
              f"priorité ML {ml_prio:6.1%} / Règles {rb_prio:6.1%}   |   "
              f"HYBRIDE {hy_cat:6.1%} / {hy_prio:6.1%}")

    print("\n--- 2b. Formulations d'urgence inédites (leave-one-phrasing-out) ---")
    print("     Une des 3 formulations de CHAQUE priorité est retirée ; le sujet reste connu.")
    for fold in range(3):
        held = {generator.URGENCY_PHRASES[p][fold] for p in generator.URGENCY_PHRASES}
        train_rows = [r for r in rows if _urgency_phrase_of(r, all_phrases) not in held]
        test_rows = [r for r in rows if _urgency_phrase_of(r, all_phrases) in held]
        ml = train_ml(train_rows)
        ml_cat, ml_prio = _accuracies(ml, test_rows)
        rb_cat, rb_prio = _accuracies(RuleBasedClassifier(), test_rows)
        hy_cat, hy_prio = _accuracies(HybridClassifier(ml, RuleBasedClassifier()), test_rows)
        print(f"  Pli {fold + 1} (formulation #{fold + 1} de chaque priorité retirée, "
              f"test={len(test_rows):3d}) : "
              f"catégorie ML {ml_cat:6.1%} / Règles {rb_cat:6.1%}   |   "
              f"priorité ML {ml_prio:6.1%} / Règles {rb_prio:6.1%}   |   "
              f"HYBRIDE {hy_cat:6.1%} / {hy_prio:6.1%}")


def run_manual_protocol(rows):
    """Hors protocole statistique : les six demandes de data/manual_cases.csv, rédigées à
    la main, passées par les trois stratégies entraînées sur la TOTALITÉ du jeu de données -
    donc dans les conditions exactes du service (app.py). Une par une, pour qu'on voie
    laquelle échoue et sur quoi, pas seulement un pourcentage."""
    path = Path(__file__).parent / "data" / "manual_cases.csv"
    with path.open(encoding="utf-8") as f:
        cases = list(csv.DictReader(f))

    ml = train_ml(rows)
    strategies = [
        ("Règles", RuleBasedClassifier()),
        ("ML", ml),
        ("HYBRIDE (servi)", HybridClassifier(ml, RuleBasedClassifier())),
    ]

    print("\n" + "#" * 78)
    print("# CONTRÔLE HORS JEU DE DONNÉES - 6 demandes rédigées à la main")
    print("# Entraînement sur les 468 lignes, exactement comme le service en marche.")
    print("#" * 78)

    for name, classifier in strategies:
        good_category = good_priority = 0
        print(f"\n--- {name} ---")
        for case in cases:
            prediction = classifier.classify(case["title"], case["description"])
            category_ok = prediction.category == case["category"]
            priority_ok = prediction.priority == case["priority"]
            good_category += category_ok
            good_priority += priority_ok
            print(f"  {case['title'][:44]:44s} "
                  f"catégorie {case['category']:>12s} -> {prediction.category:<12s} {'OK ' if category_ok else 'NON'}   "
                  f"priorité {case['priority']:>8s} -> {prediction.priority:<8s} {'OK ' if priority_ok else 'NON'}")
        print(f"  => {good_category}/{len(cases)} catégories, {good_priority}/{len(cases)} priorités")


def main():
    rows = load_dataset()
    only = sys.argv[1] if len(sys.argv) > 1 else None
    if only in (None, "--split"):
        run_split_protocol(rows)
    if only in (None, "--grouped"):
        run_grouped_protocol(rows)
    if only in (None, "--manual"):
        run_manual_protocol(rows)


if __name__ == "__main__":
    main()
