#!/usr/bin/env bash
# docker compose para una instancia: proyecto verifier-<nombre> con su .env.
#
#   ./compose.sh <nombre> ps
#   ./compose.sh <nombre> logs -f verifier-api
#   ./compose.sh <nombre> restart verifier-api
#   ./compose.sh <nombre> down
#
# Instancias existentes: ls instancias/

set -euo pipefail
cd "$(dirname "$0")"

nombre="${1:?Uso: ./compose.sh <nombre> <comando de docker compose>}"
shift
env="instancias/$nombre/.env"
[[ -f "$env" ]] || { echo "No existe la instancia '$nombre' ($env). Créala con ./levantar.sh $nombre" >&2; exit 1; }

exec docker compose -p "verifier-$nombre" --env-file "$env" -f compose.yml "$@"
