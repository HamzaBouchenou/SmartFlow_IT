"""§15.3 - "endpoint de santé pour le back-end, la base et le service IA" : ce test est ce
qui donne un contenu réel au job CI du service IA (§15.2 - "exécution des tests unitaires
et d'intégration") tant que la classification et le résumé assistés par IA (§12, S9) ne
sont pas encore implémentés.
"""
from app import app


def test_health_reports_ok_status():
    client = app.test_client()

    response = client.get("/health")

    assert response.status_code == 200
    assert response.get_json() == {"statut": "ok", "service": "smartflow-ai"}
