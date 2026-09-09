"""§12.1 - Classification : "Proposer une catégorie et une priorité à partir du titre et de
la description. Le résultat reste modifiable." §12.2 demande de comparer une approche par
règles/mots-clés à un modèle de classification simple, et de justifier le choix final par
les résultats (voir EVALUATION.md pour les métriques réelles ayant guidé ce choix - ADR-16,
docs/DECISIONS.md).

Les trois classifieurs exposent la même interface - classify(title, description) ->
Prediction, plus `METHOD`/`category_method`/`priority_method` qui disent d'où vient chaque
suggestion - si bien que la route Flask (app.py) et le script d'évaluation (evaluate.py)
les comparent sans savoir lequel est "la" stratégie active.
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

    METHOD = "rules"
    category_method = "rules"
    priority_method = "rules"

    # Marqueurs de négation cherchés juste avant un mot-clé trouvé. Une liste courte et
    # générale de la négation française, jamais une formulation empruntée au jeu de données :
    # ce classifieur sert aussi de référence de comparaison (EVALUATION.md, protocole 2), et
    # y recopier une phrase du jeu de test fausserait la comparaison qu'il sert à établir.
    NEGATIONS = ("pas", "sans", "aucun", "aucune", "jamais", "non", "ni")
    NEGATION_WINDOW = 25

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

        # La négation ne se traite que pour l'urgence. Nier un mot d'urgence en inverse le
        # sens (« pas bloquant ») ; nier un mot de sujet n'en change pas le sujet - « je n'ai
        # aucun accès à mon compte » reste une demande d'ACCES_COMPTE.
        category, category_hits = self._best_match(text, self.CATEGORY_KEYWORDS, default="AUTRE",
                                                     negation_aware=False)
        priority, priority_hits = self._best_match(text, self.PRIORITY_KEYWORDS, default="MEDIUM",
                                                     negation_aware=True)


        # Confiance heuristique : proportionnelle au nombre de mots-clés distincts trouvés,
        # plafonnée à 0.95 (jamais 1.0 - RG-10 veut une aide, jamais une certitude affichée).
        category_confidence = min(0.5 + 0.15 * category_hits, 0.95) if category_hits else 0.3
        priority_confidence = min(0.5 + 0.2 * priority_hits, 0.95) if priority_hits else 0.4

        return Prediction(category, category_confidence, priority, priority_confidence)

    @staticmethod
    def _best_match(text, keyword_map, default, negation_aware):
        """À égalité de nombre de mots-clés trouvés, le mot-clé le plus long l'emporte.

        Ce n'est pas un détail de départage : les mots-clés se contiennent les uns les
        autres, et « urgent » est un morceau de « pas urgent ». Sans cette règle, « ce
        n'est pas urgent » comptait un point pour HIGH et un point pour LOW, et l'égalité
        se tranchait par l'ordre de déclaration du dictionnaire - donc en faveur de HIGH.
        Une urgence niée était lue comme une urgence. Comparer aussi la longueur du plus
        long mot-clé trouvé fait gagner la formulation la plus spécifique, qui est toujours
        celle qui porte le sens. Une occurrence niée ne compte de toute façon plus du tout
        (voir _occurs_affirmed).
        """
        best_label, best_hits, best_length = default, 0, 0
        for label, keywords in keyword_map.items():
            matched = [kw for kw in keywords
                        if (RuleBasedClassifier._occurs_affirmed(text, kw) if negation_aware else kw in text)]
            if not matched:
                continue
            hits, length = len(matched), max(len(kw) for kw in matched)
            if (hits, length) > (best_hits, best_length):
                best_label, best_hits, best_length = label, hits, length
        return best_label, best_hits

    @classmethod
    def _occurs_affirmed(cls, text, keyword):
        """Un mot-clé ne compte que s'il apparaît au moins une fois SANS négation devant lui.

        « Ce n'est pas bloquant », « simple remarque sans caractère urgent » : la liste
        blanche y voyait « bloquant » et « urgent », donc CRITICAL et HIGH - l'inverse exact
        de ce que la phrase dit. Une fenêtre de quelques mots en amont suffit à le voir, et
        reste dans l'esprit de l'approche : déterministe, explicable, sans modèle.

        Une occurrence niée est retirée, jamais retournée en indice de la priorité opposée :
        « pas bloquant » ne veut pas dire « faible priorité », seulement « pas critique ».
        La demande retombe alors sur le défaut (MEDIUM), qui est bien ce que la phrase
        laisse entendre.
        """
        for match in re.finditer(re.escape(keyword), text):
            if not cls._negated_before(text, match.start()):
                return True
        return False

    @classmethod
    def _negated_before(cls, text, index):
        """La négation ne porte que dans sa propre proposition.

        La fenêtre s'arrête donc à la ponctuation qui précède (« , » « ; » « : » « . »),
        et à quelques mots au plus. Sans cette borne, « blocage total, aucune activité
        possible tant que ce n'est pas résolu, urgence maximale » voyait le « pas » d'une
        proposition antérieure annuler « urgence maximale » - une phrase qui hurle CRITICAL
        retombait sur le défaut MEDIUM. Mesuré : cette borne vaut 99 % contre 50 % sur le
        pli 2b qui contient précisément cette formulation (EVALUATION.md).
        """
        window = text[max(0, index - cls.NEGATION_WINDOW):index]
        clause_start = max(window.rfind(delimiter) for delimiter in ".,;:!?")
        window = window[clause_start + 1:]
        return any(re.search(rf"\b{negation}\b", window) for negation in cls.NEGATIONS)


class MlClassifier:
    """TF-IDF + régression logistique, un pipeline indépendant par cible (catégorie,
    priorité) - entraîné à l'initialisation du service sur data/training_data.csv (§12.2 -
    "base de données d'exemples anonymisés ou générés"), pas d'artefact binaire à
    maintenir séparément du script qui l'a produit."""

    METHOD = "ml"
    category_method = "ml"
    priority_method = "ml"

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


class HybridClassifier:
    """ADR-21 (docs/DECISIONS.md) - la stratégie active du service : la **catégorie** vient
    d'une source, la **priorité** d'une autre.

    Pourquoi cet assemblage plutôt qu'un vainqueur unique. §12.2 demande de comparer les
    deux approches et de justifier le choix final par les résultats ; mesurés sur un
    découpage par groupe (EVALUATION.md, protocole 2), ces résultats ne désignent pas le
    même gagnant pour les deux cibles. La catégorie est portée par un vocabulaire thématique
    ouvert, que le ML apprend et qui tranche des confusions qu'une liste de mots-clés ne
    peut pas (RESEAU vs ACCES_COMPTE partagent « connexion », « accès », « vpn »). La
    priorité, elle, est portée par un vocabulaire d'urgence court, fermé et récurrent
    (« bloquant », « urgent », « pas prioritaire ») : les règles y tiennent 74-99 % là où le
    modèle, entraîné sur douze formulations figées, tombe à 0-3 % dès qu'on lui en présente
    une treizième. Tracer la frontière par cible est une réponse plus fidèle à la question
    du §12.2 qu'un classement global.

    Chaque source calcule les deux cibles et l'on n'en retient qu'une : c'est deux fois un
    calcul négligeable (une inclusion de chaînes d'un côté, une prédiction TF-IDF de
    l'autre), contre une composition qui reste lisible et un `Prediction` inchangé pour tous
    les appelants.
    """

    METHOD = "hybrid"

    def __init__(self, category_source, priority_source):
        self._category_source = category_source
        self._priority_source = priority_source

    @property
    def category_method(self) -> str:
        return self._category_source.category_method

    @property
    def priority_method(self) -> str:
        return self._priority_source.priority_method

    def classify(self, title: str, description: str) -> Prediction:
        category = self._category_source.classify(title, description)
        priority = self._priority_source.classify(title, description)
        return Prediction(category.category, category.category_confidence,
                           priority.priority, priority.priority_confidence)
