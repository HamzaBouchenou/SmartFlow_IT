"""Service IA SmartFlow — §12 du cahier des charges.
Le module est une aide a la qualification et a la recherche. Il n'approuve jamais
une demande, ne la cloture jamais et ne modifie aucune donnee sensible (RG-10).
L'application principale doit rester utilisable si ce service est arrete (§12.2) -
infrastructure/ai côté Spring porte ce mode désactivé, pas ce service lui-même.
"""
import csv
from pathlib import Path

from flask import Flask, jsonify, request
from werkzeug.exceptions import HTTPException

from classification import HybridClassifier, MlClassifier, RuleBasedClassifier
from summarization import summarize

app = Flask(__name__)

# Aucune des deux fonctions du §12.1 ne travaille sur un corps volumineux : une demande et
# ses derniers échanges tiennent très largement sous cette borne. Sans elle, Flask accepte
# un corps de taille arbitraire et le charge entièrement en mémoire avant que la route ne
# décide quoi que ce soit. Werkzeug répond 413 au-delà - remis au format d'erreur commun
# par le gestionnaire ci-dessous, jamais en page HTML.
app.config["MAX_CONTENT_LENGTH"] = 1 * 1024 * 1024

# Nombre de phrases par défaut et borne haute du résumé (§12.1 - "un résumé court").
_DEFAULT_MAX_SENTENCES = 3
_MAX_SENTENCES_LIMIT = 20


@app.errorhandler(HTTPException)
def handle_http_exception(exc):
    """Toute erreur de ce service reste du JSON, jamais la page HTML par défaut de Flask :
    `AiClient` côté Spring désérialise une réponse JSON et n'a aucun moyen de tirer un
    diagnostic d'un corps HTML (§11.1 - un format d'erreur unique)."""
    return jsonify(error=exc.description, status=exc.code), exc.code


@app.errorhandler(Exception)
def handle_unexpected_exception(exc):
    """Filet de sécurité : une exception non prévue reste elle aussi du JSON. Le détail de
    l'exception n'est jamais renvoyé au client (§8 - rien d'interne dans une réponse), il
    part dans les logs du service."""
    app.logger.exception("Erreur inattendue du service IA", exc_info=exc)
    return jsonify(error="Erreur interne du service IA.", status=500), 500


# §12.2/ADR-16 puis ADR-21 (docs/DECISIONS.md, EVALUATION.md) - la stratégie active est
# hybride : la catégorie vient du MlClassifier, la priorité du RuleBasedClassifier. ADR-16
# avait retenu le tout-ML sur un "100 % contre 82 %" mesuré par un découpage par ligne d'un
# jeu composé par gabarit, qui ne mesurait que de la mémorisation ; réévaluée par groupe
# (`evaluate.py --grouped`), la priorité prédite par le ML tombe à 0-3 % contre 74-99 % pour
# les règles, tandis que la catégorie reste au ML. ADR-21 a tranché cette révision, et
# HybridClassifier en porte le raisonnement complet.
#
# Le modèle est entraîné une fois au démarrage du process sur data/training_data.csv - un
# jeu de cette taille (~470 lignes) s'entraîne en une fraction de seconde, pas besoin d'un
# artefact binaire séparé à maintenir en plus du script qui l'a produit.
_DATASET_PATH = Path(__file__).parent / "data" / "training_data.csv"


def _load_classifier():
    with _DATASET_PATH.open(encoding="utf-8") as f:
        rows = list(csv.DictReader(f))
    ml = MlClassifier.train(
        [r["title"] for r in rows], [r["description"] for r in rows],
        [r["category"] for r in rows], [r["priority"] for r in rows])
    return HybridClassifier(category_source=ml, priority_source=RuleBasedClassifier())


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
    # §12.2/ADR-21 - `method` dit quelle stratégie a répondu, et les deux champs qui suivent
    # d'où vient chaque suggestion prise séparément : depuis que les deux cibles n'ont plus
    # la même source, un seul mot ne suffit plus à le dire.
    return jsonify(
        category=prediction.category,
        categoryConfidence=round(prediction.category_confidence, 4),
        priority=prediction.priority,
        priorityConfidence=round(prediction.priority_confidence, 4),
        method=_classifier.METHOD,
        categoryMethod=_classifier.category_method,
        priorityMethod=_classifier.priority_method,
    )


@app.post("/summarize")
def summarize_route():
    """§12.1 - résumé court d'une demande longue et de ses derniers échanges."""
    body = request.get_json(silent=True) or {}
    text = (body.get("text") or "").strip()
    if not text:
        return jsonify(error="text requis"), 400

    max_sentences, error = _read_max_sentences(body)
    if error:
        return jsonify(error=error), 400

    return jsonify(summary=summarize(text, max_sentences=max_sentences), method="extractive")


def _read_max_sentences(body):
    """`maxSentences` est facultatif, mais s'il est fourni il doit être un entier >= 1 :
    une chaîne faisait remonter un TypeError du slicing en 500, et un 0 ou un négatif
    produisaient silencieusement un résumé vide ou tronqué par la fin (slicing négatif)
    plutôt qu'une erreur. Un défaut absurde vaut moins qu'un refus explicite."""
    raw = body.get("maxSentences", _DEFAULT_MAX_SENTENCES)
    # bool est un sous-type de int en Python : `true` n'est pas un nombre de phrases.
    if isinstance(raw, bool) or not isinstance(raw, int):
        return None, "maxSentences doit être un entier"
    if raw < 1 or raw > _MAX_SENTENCES_LIMIT:
        return None, f"maxSentences doit être compris entre 1 et {_MAX_SENTENCES_LIMIT}"
    return raw, None


if __name__ == "__main__":
    # Développement local uniquement. L'image Docker sert l'application par gunicorn
    # (voir Dockerfile) - le serveur ci-dessous ne borne aucune requête et ne traite
    # qu'une connexion à la fois.
    app.run(host="0.0.0.0", port=5000)
