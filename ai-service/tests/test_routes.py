"""§11.2/§12.1 - the two AI routes through Flask's own test client, matching
test_health.py's existing pattern."""
from app import app


def test_classify_route_returns_category_and_priority():
    client = app.test_client()

    response = client.post("/classify", json={
        "title": "Écran cassé",
        "description": "Mon écran ne s'allume plus, c'est urgent pour mon équipe.",
    })

    assert response.status_code == 200
    body = response.get_json()
    assert "category" in body and "priority" in body
    assert 0.0 <= body["categoryConfidence"] <= 1.0
    assert 0.0 <= body["priorityConfidence"] <= 1.0
    # ADR-21 - la stratégie servie est hybride, et la réponse dit d'où vient chaque
    # suggestion prise séparément : un consommateur ne peut pas le déduire d'un seul mot.
    assert body["method"] == "hybrid"
    assert body["categoryMethod"] == "ml"
    assert body["priorityMethod"] == "rules"


def test_classify_route_requires_title_or_description():
    client = app.test_client()

    response = client.post("/classify", json={})

    assert response.status_code == 400


def test_summarize_route_returns_a_summary():
    client = app.test_client()

    response = client.post("/summarize", json={
        "text": "Le serveur est en panne. Plusieurs équipes sont bloquées. Merci d'intervenir vite.",
        "maxSentences": 2,
    })

    assert response.status_code == 200
    assert "summary" in response.get_json()


def test_summarize_route_requires_text():
    client = app.test_client()

    response = client.post("/summarize", json={})

    assert response.status_code == 400


def test_summarize_route_rejects_non_integer_max_sentences():
    # Une chaîne faisait auparavant remonter un TypeError du slicing en 500 HTML.
    client = app.test_client()

    response = client.post("/summarize", json={"text": "Un deux. Trois quatre. Cinq six.",
                                               "maxSentences": "trois"})

    assert response.status_code == 400
    assert "maxSentences" in response.get_json()["error"]


def test_summarize_route_rejects_max_sentences_below_one():
    # 0 renvoyait un résumé vide et -1 tronquait la dernière phrase (slicing négatif),
    # tous deux silencieusement.
    client = app.test_client()

    for value in (0, -1):
        response = client.post("/summarize", json={"text": "Un deux. Trois quatre. Cinq six.",
                                                   "maxSentences": value})
        assert response.status_code == 400, value


def test_summarize_route_rejects_max_sentences_above_limit():
    client = app.test_client()

    response = client.post("/summarize", json={"text": "Un deux. Trois quatre.",
                                               "maxSentences": 10_000})

    assert response.status_code == 400


def test_summarize_route_accepts_absent_max_sentences():
    client = app.test_client()

    response = client.post("/summarize", json={"text": "Un deux. Trois quatre. Cinq six. Sept huit."})

    assert response.status_code == 200
    assert response.get_json()["summary"]


def test_oversized_body_is_refused_as_json_not_html():
    # MAX_CONTENT_LENGTH : le refus doit rester au format d'erreur commun, jamais une page
    # HTML - AiClient côté Spring ne sait désérialiser que du JSON.
    client = app.test_client()

    response = client.post("/summarize", json={"text": "Phrase de test. " * 200_000})

    assert response.status_code == 413
    assert response.get_json()["status"] == 413


def test_unknown_route_returns_json_error():
    client = app.test_client()

    response = client.get("/inexistant")

    assert response.status_code == 404
    assert response.get_json()["status"] == 404
