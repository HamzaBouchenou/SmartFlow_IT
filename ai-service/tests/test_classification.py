"""§12.1/§12.3 - les trois classifieurs partagent la même interface (classify(title,
description) -> Prediction), donc ils se comparent ici sur les mêmes cas, indépendamment
des métriques sur jeu de données d'EVALUATION.md. HybridClassifier (ADR-21) est celui que
le service assemble réellement.
"""
from classification import (CATEGORIES, PRIORITIES, HybridClassifier, MlClassifier,
                             RuleBasedClassifier)


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


def test_rule_based_reads_a_negated_urgency_as_low_not_high():
    # Régression : « urgent » est un morceau de « pas urgent ». Les deux mots-clés se
    # déclenchaient donc ensemble, l'égalité se tranchait par l'ordre de déclaration, et
    # une urgence NIÉE ressortait en HIGH. Le mot-clé le plus long l'emporte désormais.
    classifier = RuleBasedClassifier()

    prediction = classifier.classify(
        "Renouvellement d'abonnement",
        "Merci de préparer un devis. Ce n'est pas urgent, le contrat court jusqu'en décembre.")

    assert prediction.priority == "LOW"


def test_rule_based_negation_does_not_reach_across_a_clause():
    # Régression : la négation ne porte que dans sa propre proposition. « ... tant que ce
    # n'est pas résolu, urgence maximale » doit rester CRITICAL - le « pas » appartient à la
    # proposition précédente, la virgule l'y enferme.
    classifier = RuleBasedClassifier()

    prediction = classifier.classify(
        "Blocage total",
        "Blocage total, aucune activité possible tant que ce n'est pas résolu, urgence maximale.")

    assert prediction.priority == "CRITICAL"


def test_rule_based_negation_never_disturbs_the_category():
    # Nier un mot d'urgence en inverse le sens ; nier un mot de sujet n'en change pas le
    # sujet. Une demande qui dit « aucun accès » reste une demande d'accès.
    classifier = RuleBasedClassifier()

    prediction = classifier.classify("Compte bloqué", "Je n'ai aucun accès à mon compte depuis ce matin.")

    assert prediction.category == "ACCES_COMPTE"


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


def _trained_ml():
    titles = ["Écran cassé", "Mot de passe oublié", "Devis fournisseur", "Wifi instable"] * 5
    descriptions = [
        "Mon écran ne s'allume plus, urgent.",
        "Je ne peux plus me connecter, aucune urgence.",
        "Merci de solliciter un devis, pas prioritaire.",
        "La wifi coupe sans arrêt, c'est bloquant.",
    ] * 5
    categories = ["MATERIEL", "ACCES_COMPTE", "ACHAT", "RESEAU"] * 5
    priorities = ["HIGH", "LOW", "LOW", "CRITICAL"] * 5
    return MlClassifier.train(titles, descriptions, categories, priorities)


def test_hybrid_takes_its_category_from_one_source_and_its_priority_from_the_other():
    # ADR-21 - le coeur de la décision : chaque cible vient de la source qui la mesure le
    # mieux (EVALUATION.md, protocole 2). Prouvé sans dépendre d'un jeu de données, en
    # composant deux sources dont on connaît déjà les réponses séparément.
    ml = _trained_ml()
    rules = RuleBasedClassifier()
    text = ("Panne totale", "Situation critique, urgence maximale, tout est bloquant.")

    hybrid = HybridClassifier(category_source=ml, priority_source=rules)
    prediction = hybrid.classify(*text)

    assert prediction.category == ml.classify(*text).category
    assert prediction.category_confidence == ml.classify(*text).category_confidence
    assert prediction.priority == rules.classify(*text).priority
    assert prediction.priority_confidence == rules.classify(*text).priority_confidence


def test_hybrid_reports_the_origin_of_each_suggestion():
    hybrid = HybridClassifier(category_source=_trained_ml(), priority_source=RuleBasedClassifier())

    assert hybrid.METHOD == "hybrid"
    assert hybrid.category_method == "ml"
    assert hybrid.priority_method == "rules"


def test_hybrid_priority_survives_an_urgency_phrasing_the_model_never_saw():
    # La raison d'être d'ADR-21 : sur une formulation d'urgence absente de l'entraînement,
    # le ML seul se trompe de classe (mémorisation), les règles tiennent - donc l'hybride
    # tient. Les mots employés ici n'apparaissent dans aucune ligne d'entraînement de
    # _trained_ml().
    ml = _trained_ml()
    hybrid = HybridClassifier(category_source=ml, priority_source=RuleBasedClassifier())

    prediction = hybrid.classify("Serveur applicatif indisponible",
                                  "La production est interrompue, totalement bloquant pour le service.")

    assert prediction.priority == "CRITICAL"
    assert prediction.priority in PRIORITIES
