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
    assert body["method"] == "ml"


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
