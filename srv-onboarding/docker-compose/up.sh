#!/usr/bin/env bash
#
# Levanta srv-onboarding.
#
#   ./up.sh            levanta el stack
#   ./up.sh --build    lo levanta reconstruyendo las imagenes
#
# POR QUE SE LEVANTA DESDE EL COMPOSE RAIZ
#
# El compose raiz (`{RAIZ}/docker-compose.yaml`) funde los proyectos en uno solo:
# los contenedores viven bajo el proyecto `avance`. Si este stack se levantara
# con su propio compose, sus contenedores crearian un proyecto aparte con el
# MISMO nombre de contenedor, y Docker no los dejaria coexistir. Por eso, si el
# stack esta en el compose raiz, se levanta desde ahi.
#
# Este stack esta en el compose raiz, asi que sus contenedores viven bajo el
# proyecto `avance`, junto a los demas.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$HERE/../.." && pwd)"

NETWORK="${NW_NETWORK:-avance-eudi}"
if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
  echo ">> Creando la red compartida '$NETWORK'..."
  docker network create "$NETWORK" >/dev/null
fi

docker compose -f "$RAIZ/docker-compose.yaml" up -d onboarding-backend onboarding-biometrics onboarding-postgres "$@"
