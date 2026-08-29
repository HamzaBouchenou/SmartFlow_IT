"""§12.3 - Critères d'évaluation du module IA : "Précision, rappel, F1-score et matrice de
confusion sur un jeu de test séparé" pour la classification. §12.2 demande de comparer
l'approche par règles/mots-clés à un modèle de classification simple et de justifier le
choix final par les résultats - ce script produit ces résultats (voir EVALUATION.md pour la
sortie effectivement obtenue et ADR-16, docs/DECISIONS.md, pour la décision qu'elle a
motivée).

Usage: python evaluate.py
"""
import csv
from pathlib import Path

from sklearn.metrics import classification_report, confusion_matrix
from sklearn.model_selection import train_test_split

from classification import CATEGORIES, PRIORITIES, MlClassifier, RuleBasedClassifier


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


def main():
    rows = load_dataset()
    train_rows, test_rows = train_test_split(rows, test_size=0.2, random_state=42,
                                              stratify=[r["category"] for r in rows])
    print(f"Jeu d'entraînement: {len(train_rows)} lignes - jeu de test: {len(test_rows)} lignes")

    rule_based = RuleBasedClassifier()
    evaluate("Règles/mots-clés (RuleBasedClassifier)", rule_based, test_rows)

    ml_classifier = MlClassifier.train(
        [r["title"] for r in train_rows], [r["description"] for r in train_rows],
        [r["category"] for r in train_rows], [r["priority"] for r in train_rows])
    evaluate("TF-IDF + régression logistique (MlClassifier)", ml_classifier, test_rows)


if __name__ == "__main__":
    main()
