FROM python:3.12-slim
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
EXPOSE 5000
# §15.1 - gunicorn plutôt que `python app.py` : le serveur de développement de Flask est
# mono-thread, sans délai d'attente, et avertit lui-même à chaque démarrage qu'il ne doit
# pas servir en déploiement. `--preload` entraîne le classifieur une seule fois avant le
# fork des workers (app.py l'entraîne à l'import) au lieu d'une fois par worker, et
# `--timeout` borne une requête bloquée, ce que le serveur de développement ne faisait pas.
CMD ["gunicorn", "--bind", "0.0.0.0:5000", "--workers", "2", "--threads", "4", \
     "--timeout", "30", "--preload", "--access-logfile", "-", "app:app"]
