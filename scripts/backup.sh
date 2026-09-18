#!/usr/bin/env bash
# Sauvegarde de la base PostgreSQL de SmartFlow IT (§15.3 - "Script documenté de sauvegarde
# et de restauration PostgreSQL").
#
# Sauvegarde le contenu du service `postgres` de docker-compose.yml, sans jamais s'installer
# sur le poste : pg_dump est exécuté *dans* le conteneur, donc sa version correspond toujours
# exactement à celle du serveur (une sauvegarde prise par un pg_dump plus ancien que le
# serveur est refusée par PostgreSQL lui-même).
#
# Format : archive personnalisée (-Fc), compressée et restaurable sélectivement par
# scripts/restore.sh. Jamais du SQL brut : une restauration partielle en serait impossible.
#
# Usage :
#   ./scripts/backup.sh                    -> backups/smartflow-<horodatage>.dump
#   ./scripts/backup.sh /chemin/vers/dir   -> dans le répertoire indiqué
#
# Le mot de passe n'est jamais passé en argument (il serait visible dans `ps`) : il est lu
# depuis .env comme le fait docker compose (§8 - aucun secret dans le code).

set -euo pipefail

cd "$(dirname "$0")/.."

readonly OUT_DIR="${1:-backups}"
readonly SERVICE="postgres"

if [ ! -f .env ]; then
    echo "Erreur : .env introuvable. Copiez .env.example en .env d'abord." >&2
    exit 1
fi

# shellcheck disable=SC1091
set -a; . ./.env; set +a
readonly DB_NAME="${DB_NAME:-smartflow}"
readonly DB_USER="${DB_USER:-smartflow}"

if ! docker compose ps --status running --services | grep -qx "$SERVICE"; then
    echo "Erreur : le service '$SERVICE' n'est pas démarré. Lancez 'docker compose up -d $SERVICE'." >&2
    exit 1
fi

mkdir -p "$OUT_DIR"
readonly STAMP="$(date +%Y%m%d-%H%M%S)"
readonly TARGET="$OUT_DIR/smartflow-$STAMP.dump"

echo "Sauvegarde de la base '$DB_NAME' vers $TARGET ..."
docker compose exec -T "$SERVICE" \
    pg_dump --format=custom --no-owner --no-privileges --username "$DB_USER" "$DB_NAME" > "$TARGET"

# Une sauvegarde vide est un échec silencieux : mieux vaut le dire tout de suite que le
# découvrir le jour de la restauration.
if [ ! -s "$TARGET" ]; then
    echo "Erreur : l'archive produite est vide, sauvegarde considérée en échec." >&2
    rm -f "$TARGET"
    exit 1
fi

echo "Terminé : $TARGET ($(du -h "$TARGET" | cut -f1))"
echo "Restauration : ./scripts/restore.sh $TARGET"
