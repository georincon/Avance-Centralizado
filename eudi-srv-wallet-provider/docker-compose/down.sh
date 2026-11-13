#!/usr/bin/env bash
#
# Baja eudi-srv-wallet-provider.
#
#   ./down.sh        para los servicios y quita los contenedores
#   ./down.sh -v     borra tambien los volumenes (SE BORRAN LOS DATOS)
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$HERE/../.." && pwd)"

docker compose -f "$RAIZ/docker-compose.yaml" rm -f -s spartans-wallet-provider postgres-spartans status-list-spartans "$@"
echo ">> eudi-srv-wallet-provider detenido. La red compartida 'avance-eudi' se conserva."
