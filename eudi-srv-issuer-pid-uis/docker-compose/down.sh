#!/usr/bin/env bash
#
# Baja eudi-srv-issuer-pid-uis.
#
#   ./down.sh        para los servicios y quita los contenedores
#   ./down.sh -v     borra tambien los volumenes (SE BORRAN LOS DATOS)
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$HERE/../.." && pwd)"

docker compose -f "$HERE/docker-compose.yaml" down "$@"
echo ">> eudi-srv-issuer-pid-uis detenido. La red compartida 'avance-eudi' se conserva."
