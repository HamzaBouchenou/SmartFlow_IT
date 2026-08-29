"""§12.1/§12.3 - both classifiers share the same interface (classify(title, description) ->
Prediction) so they can be compared on the same test cases here, independently of
EVALUATION.md's dataset-driven metrics.
"""
from classification import CATEGORIES, PRIORITIES, MlClassifier, RuleBasedClassifier


def test_rule_based_classifies_hardware_issue_as_materiel():
    classifier = RuleBasedClassifier()

    prediction = classifier.classify("Écran cassé", "Mon écran ne s'allume plus depuis ce matin.")

    assert prediction.category == "MATERIEL"
    assert prediction.category in CATEGORIES


def test_rule_based_classifies_critical_urgency():
    classifier = RuleBasedClassifier()

    prediction = classifier.classify("Panne totale", "Situation critique, urgence maximale, tout est bloquant.")

    assert prediction.priority == "CRITICAL"
    assert prediction.priority in PRIORITIES


def test_rule_based_defaults_to_autre_and_medium_without_keywords():
    classifier = RuleBasedClassifier()

    prediction = classifier.classify("Bonjour", "Ceci est un message sans mot-clé reconnu.")

    assert prediction.category == "AUTRE"
    assert prediction.priority == "MEDIUM"


def test_rule_based_confidence_never_reaches_certainty():
    # RG-10 - une aide, jamais une certitude affichée : la confiance reste toujours < 1.0.
    classifier = RuleBasedClassifier()

    prediction = classifier.classify(
        "Écran cassé", "Mon écran ne s'allume plus, ordinateur, clavier, souris, portable en panne.")

    assert prediction.category_confidence < 1.0
    assert prediction.priority_confidence < 1.0


def test_ml_classifier_trains_and_predicts_within_known_label_sets():
    titles = ["Écran cassé", "Mot de passe oublié", "Devis fournisseur", "Wifi instable"] * 5
    descriptions = [
        "Mon écran ne s'allume plus, urgent.",
        "Je ne peux plus me connecter, aucune urgence.",
        "Merci de solliciter un devis, pas prioritaire.",
        "La wifi coupe sans arrêt, c'est bloquant.",
    ] * 5
    categories = ["MATERIEL", "ACCES_COMPTE", "ACHAT", "RESEAU"] * 5
    priorities = ["HIGH", "LOW", "LOW", "CRITICAL"] * 5

    classifier = MlClassifier.train(titles, descriptions, categories, priorities)
    prediction = classifier.classify("Écran cassé", "Mon écran ne s'allume plus, urgent.")

    assert prediction.category in CATEGORIES
    assert prediction.priority in PRIORITIES
    assert 0.0 <= prediction.category_confidence <= 1.0
    assert 0.0 <= prediction.priority_confidence <= 1.0
