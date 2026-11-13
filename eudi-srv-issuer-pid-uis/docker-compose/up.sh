#!/usr/bin/env bash
#
# Levanta eudi-srv-issuer-pid-uis.
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
# Este stack NO esta en el compose raiz (a proposito: se levanta solo cuando
# hace falta), asi que usa su propio compose y su propio proyecto.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$(cd "$HERE/../.." && pwd)"

NETWORK="${NW_NETWORK:-avance-eudi}"
if ! docker network inspect "$NETWORK" >/dev/null 2>&1; then
  echo ">> Creando la red compartida '$NETWORK'..."
  docker network create "$NETWORK" >/dev/null
fi

docker compose -f "$HERE/docker-compose.yaml" up -d "$@"
