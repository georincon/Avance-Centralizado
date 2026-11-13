#!/usr/bin/env bash
#
# Baja eudi-srv-trust-validator.
#
#   ./down.sh        para los servicios y quita los contenedores
#   ./down.sh -v     borra tambien los volumenes (SE BORRAN LOS DATOS)
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$HERE/../.." && pwd)"

docker compose -f "$RAIZ/docker-compose.yaml" rm -f -s trust-validator "$@"
echo ">> eudi-srv-trust-validator detenido. La red compartida 'avance-eudi' se conserva."
