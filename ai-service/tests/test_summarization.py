"""§12.1/§12.3 - le résumé doit rester une extraction du texte source (aucune information
inventée) et se conformer à la longueur demandée."""
from summarization import summarize


def test_short_text_is_returned_unchanged():
    text = "Une seule phrase courte."

    assert summarize(text, max_sentences=3) == "Une seule phrase courte."


def test_summary_never_exceeds_max_sentences():
    text = (
        "L'écran ne s'allume plus depuis ce matin. J'ai déjà essayé un autre câble. "
        "Le problème persiste même sur un autre poste. C'est assez urgent pour mon équipe. "
        "Merci de traiter rapidement cette demande. Le matériel est sous garantie."
    )

    summary = summarize(text, max_sentences=2)

    assert summary.count(".") <= 2 or len(summary.split(". ")) <= 2


def test_summary_only_contains_sentences_from_the_source_text():
    text = (
        "Le serveur de fichiers est inaccessible depuis hier soir. "
        "Plusieurs collaborateurs sont bloqués dans leur travail quotidien. "
        "Une tentative de redémarrage n'a rien changé. "
        "Merci d'intervenir rapidement sur ce dossier prioritaire."
    )

    summary = summarize(text, max_sentences=2)

    for sentence in summary.split(". "):
        assert sentence.strip(". ") in text


def test_empty_text_returns_empty_summary():
    assert summarize("", max_sentences=3) == ""
    assert summarize("   ", max_sentences=3) == ""
