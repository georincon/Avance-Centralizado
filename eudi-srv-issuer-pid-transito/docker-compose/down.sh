#!/usr/bin/env bash
#
# Baja eudi-srv-issuer-pid-transito.
#
#   ./down.sh        para los servicios y quita los contenedores
#   ./down.sh -v     borra tambien los volumenes (SE BORRAN LOS DATOS)
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$HERE/../.." && pwd)"

docker compose -f "$RAIZ/docker-compose.yaml" rm -f -s haproxy-tra keycloak-tra transito-issuer postgres-tra status-list-tra "$@"
echo ">> eudi-srv-issuer-pid-transito detenido. La red compartida 'avance-eudi' se conserva."
