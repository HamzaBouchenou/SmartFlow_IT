#!/usr/bin/env bash
# Restauration de la base PostgreSQL de SmartFlow IT (§15.3).
#
# Remplace le contenu de la base par celui d'une archive produite par scripts/backup.sh.
# Opération destructrice : le schéma public est supprimé puis recréé, donc toute donnée
# postérieure à l'archive est perdue. Le script le dit et demande confirmation.
#
# L'API est arrêtée pendant l'opération : restaurer sous une application qui écrit encore
# donnerait un état incohérent, et Flyway pourrait tenter de migrer une base à moitié
# restaurée. Elle est redémarrée à la fin, ce qui rejoue la validation Flyway sur la base
# restaurée - c'est la vérification que l'archive est bien au niveau de migration attendu.
#
# L'API est redémarrée par `docker compose up -d api`, qui relit l'environnement : les
# réglages passés en ligne de commande au démarrage initial (par ex. `AI_ENABLED=true docker
# compose up`) seraient perdus. Posez-les dans .env plutôt qu'en ligne, c'est ce que le
# guide d'exploitation recommande déjà.
#
# Usage :
#   ./scripts/restore.sh backups/smartflow-20260911-120000.dump
#   FORCE=1 ./scripts/restore.sh <archive>   -> sans confirmation (usage script/CI)

set -euo pipefail

cd "$(dirname "$0")/.."

readonly ARCHIVE="${1:-}"
readonly SERVICE="postgres"

if [ -z "$ARCHIVE" ]; then
    echo "Usage : $0 <archive .dump>" >&2
    exit 1
fi
if [ ! -s "$ARCHIVE" ]; then
    echo "Erreur : archive '$ARCHIVE' introuvable ou vide." >&2
    exit 1
fi
if [ ! -f .env ]; then
    echo "Erreur : .env introuvable. Copiez .env.example en .env d'abord." >&2
    exit 1
fi

# shellcheck disable=SC1091
set -a; . ./.env; set +a
readonly DB_NAME="${DB_NAME:-smartflow}"
readonly DB_USER="${DB_USER:-smartflow}"

if [ "${FORCE:-0}" != "1" ]; then
    echo "ATTENTION : le contenu actuel de la base '$DB_NAME' va être REMPLACÉ par $ARCHIVE."
    printf "Tapez 'oui' pour continuer : "
    read -r answer
    [ "$answer" = "oui" ] || { echo "Annulé."; exit 1; }
fi

echo "1/4 Arrêt de l'API (aucune écriture pendant la restauration) ..."
docker compose stop api >/dev/null

echo "2/4 Démarrage de la base si nécessaire ..."
docker compose up -d "$SERVICE" >/dev/null
until docker compose exec -T "$SERVICE" pg_isready -U "$DB_USER" >/dev/null 2>&1; do sleep 1; done

echo "3/4 Restauration ..."
# --clean --if-exists remet le schéma à zéro avant de réinsérer : sans lui, une table déjà
# présente ferait échouer la restauration ligne à ligne plutôt que de la remplacer.
docker compose exec -T "$SERVICE" \
    pg_restore --clean --if-exists --no-owner --no-privileges \
    --username "$DB_USER" --dbname "$DB_NAME" < "$ARCHIVE"

echo "4/4 Redémarrage de l'API (Flyway revalide le schéma restauré) ..."
docker compose up -d api >/dev/null

echo "Terminé. Vérifiez l'état : docker compose ps ; curl -s localhost:8080/actuator/health"
