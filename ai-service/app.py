
"""Service IA SmartFlow — §12 du cahier des charges.
Le module est une aide a la qualification et a la recherche. Il n'approuve jamais
une demande, ne la cloture jamais et ne modifie aucune donnee sensible (RG-10).
L'application principale doit rester utilisable si ce service est arrete (§12.2).
"""
from flask import Flask, jsonify

app = Flask(__name__)


@app.get("/health")
def health():
    """Point de sante exige au §15.3."""
    return jsonify(statut="ok", service="smartflow-ai")


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
