"""Service IA SmartFlow — §12 du cahier des charges.
Le module est une aide a la qualification et a la recherche. Il n'approuve jamais
une demande, ne la cloture jamais et ne modifie aucune donnee sensible (RG-10).
L'application principale doit rester utilisable si ce service est arrete (§12.2) -
infrastructure/ai côté Spring porte ce mode désactivé, pas ce service lui-même.
"""
import csv
from pathlib import Path

from flask import Flask, jsonify, request

from classification import MlClassifier
from summarization import summarize

app = Flask(__name__)

# §12.2/ADR-16 (docs/DECISIONS.md, EVALUATION.md) - MlClassifier a été retenu après
# comparaison avec RuleBasedClassifier (100% vs 82%/81% d'exactitude sur le jeu de test).
# Entraîné une fois au démarrage du process sur data/training_data.csv - un jeu de cette
# taille (~470 lignes) s'entraîne en une fraction de seconde, pas besoin d'un artefact
# binaire séparé à maintenir en plus du script qui l'a produit.
_DATASET_PATH = Path(__file__).parent / "data" / "training_data.csv"


def _load_classifier():
    with _DATASET_PATH.open(encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    return MlClassifier.train(
        [r["title"] for r in rows], [r["description"] for r in rows],
        [r["category"] for r in rows], [r["priority"] for r in rows])


_classifier = _load_classifier()


@app.get("/health")
def health():
    """Point de sante exige au §15.3."""
    return jsonify(statut="ok", service="smartflow-ai")


@app.post("/classify")
def classify():
    """§12.1 - catégorie + priorité suggérées à partir du titre et de la description.
    RG-10 : ce endpoint ne fait que proposer une valeur ; infrastructure/ai côté Spring la
    stocke dans AiAnalysis.suggestedValue, jamais directement sur Request."""
    body = request.get_json(silent=True) or {}
    title = (body.get("title") or "").strip()
    description = (body.get("description") or "").strip()
    if not title and not description:
        return jsonify(error="title ou description requis"), 400

    prediction = _classifier.classify(title, description)
    return jsonify(
        category=prediction.category,
        categoryConfidence=round(prediction.category_confidence, 4),
        priority=prediction.priority,
        priorityConfidence=round(prediction.priority_confidence, 4),
        method="ml",
    )


@app.post("/summarize")
def summarize_route():
    """§12.1 - résumé court d'une demande longue et de ses derniers échanges."""
    body = request.get_json(silent=True) or {}
    text = (body.get("text") or "").strip()
    if not text:
        return jsonify(error="text requis"), 400

    max_sentences = body.get("maxSentences", 3)
    return jsonify(summary=summarize(text, max_sentences=max_sentences), method="extractive")


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
