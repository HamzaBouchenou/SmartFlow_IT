"""§12.1 - Classification : "Proposer une catégorie et une priorité à partir du titre et de
la description. Le résultat reste modifiable." §12.2 demande de comparer une approche par
règles/mots-clés à un modèle de classification simple, et de justifier le choix final par
les résultats (voir EVALUATION.md pour les métriques réelles ayant guidé ce choix - ADR-16,
docs/DECISIONS.md).

Both classifiers expose the same interface - classify(title, description) -> Prediction -
so the Flask route (app.py) and the evaluation script (evaluate.py) can compare them without
knowing which one is "the" active strategy.
"""
import re
from dataclasses import dataclass

CATEGORIES = ["MATERIEL", "LOGICIEL", "ACCES_COMPTE", "RESEAU", "ACHAT", "AUTRE"]
PRIORITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]


@dataclass
class Prediction:
    category: str
    category_confidence: float
    priority: str
    priority_confidence: float


class RuleBasedClassifier:
    """Liste blanche de mots-clés par catégorie et par urgence - aucune donnée
    d'entraînement nécessaire, entièrement déterministe et explicable."""

    CATEGORY_KEYWORDS = {
        "MATERIEL": ["écran", "clavier", "souris", "imprimante", "ordinateur", "portable",
                     "batterie", "casque", "matériel", "poste de travail"],
        "LOGICIEL": ["logiciel", "application", "installation", "installer", "mise à jour",
                     "licence logicielle", "bug", "plante", "programme"],
        "ACCES_COMPTE": ["mot de passe", "compte", "accès", "vpn", "droits", "badge",
                         "connexion", "identifiant", "bloqué"],
        "RESEAU": ["wifi", "réseau", "internet", "connexion réseau", "débit", "partage réseau"],
        "ACHAT": ["devis", "commande", "achat", "licence", "budget", "fournisseur", "facture"],
    }

    PRIORITY_KEYWORDS = {
        "CRITICAL": ["critique", "urgence absolue", "bloquant", "totalement bloquant",
                     "production est interrompue", "urgence maximale", "arrêt"],
        "HIGH": ["urgent", "rapidement", "important", "ralentit fortement"],
        "LOW": ["aucune urgence", "pas prioritaire", "quand vous pourrez", "prenez votre temps",
                "pas urgent"],
    }

    def classify(self, title: str, description: str) -> Prediction:
        text = f"{title} {description}".lower()

        category, category_hits = self._best_match(text, self.CATEGORY_KEYWORDS, default="AUTRE")
        priority, priority_hits = self._best_match(text, self.PRIORITY_KEYWORDS, default="MEDIUM")

        # Confiance heuristique : proportionnelle au nombre de mots-clés distincts trouvés,
        # plafonnée à 0.95 (jamais 1.0 - RG-10 veut une aide, jamais une certitude affichée).
        category_confidence = min(0.5 + 0.15 * category_hits, 0.95) if category_hits else 0.3
        priority_confidence = min(0.5 + 0.2 * priority_hits, 0.95) if priority_hits else 0.4

        return Prediction(category, category_confidence, priority, priority_confidence)

    @staticmethod
    def _best_match(text, keyword_map, default):
        best_label, best_hits = default, 0
        for label, keywords in keyword_map.items():
            hits = sum(1 for kw in keywords if kw in text)
            if hits > best_hits:
                best_label, best_hits = label, hits
        return best_label, best_hits


class MlClassifier:
    """TF-IDF + régression logistique, un pipeline indépendant par cible (catégorie,
    priorité) - entraîné à l'initialisation du service sur data/training_data.csv (§12.2 -
    "base de données d'exemples anonymisés ou générés"), pas d'artefact binaire à
    maintenir séparément du script qui l'a produit."""

    def __init__(self, category_pipeline, priority_pipeline):
        self._category_pipeline = category_pipeline
        self._priority_pipeline = priority_pipeline

    @classmethod
    def train(cls, titles, descriptions, categories, priorities):
        from sklearn.feature_extraction.text import TfidfVectorizer
        from sklearn.linear_model import LogisticRegression
        from sklearn.pipeline import Pipeline

        texts = [f"{t} {d}" for t, d in zip(titles, descriptions)]

        category_pipeline = Pipeline([
            ("tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=1)),
            ("clf", LogisticRegression(max_iter=1000)),
        ])
        category_pipeline.fit(texts, categories)

        priority_pipeline = Pipeline([
            ("tfidf", TfidfVectorizer(ngram_range=(1, 2), min_df=1)),
            ("clf", LogisticRegression(max_iter=1000)),
        ])
        priority_pipeline.fit(texts, priorities)

        return cls(category_pipeline, priority_pipeline)

    def classify(self, title: str, description: str) -> Prediction:
        text = [f"{title} {description}"]
        category = self._category_pipeline.predict(text)[0]
        category_confidence = float(max(self._category_pipeline.predict_proba(text)[0]))
        priority = self._priority_pipeline.predict(text)[0]
        priority_confidence = float(max(self._priority_pipeline.predict_proba(text)[0]))
        return Prediction(category, category_confidence, priority, priority_confidence)
