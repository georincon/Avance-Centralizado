#!/usr/bin/env bash
#
# Baja srv-onboarding.
#
#   ./down.sh        para los servicios y quita los contenedores
#   ./down.sh -v     borra tambien los volumenes (SE BORRAN LOS DATOS)
#
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$HERE/../.." && pwd)"

docker compose -f "$RAIZ/docker-compose.yaml" rm -f -s onboarding-backend onboarding-biometrics onboarding-postgres "$@"
echo ">> srv-onboarding detenido. La red compartida 'avance-eudi' se conserva."
