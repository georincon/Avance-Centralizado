#!/usr/bin/env bash
#
# Baja los portales de ejemplo y sus verifiers. Lo usa ../down.sh; también se puede lanzar solo.
#
#   ./bajar-portales.sh          baja y quita los contenedores
#   ./bajar-portales.sh -v       baja también los volúmenes (SE BORRAN LOS DATOS de los verifiers)
#
# No toca los datos de los portales con archivos propios (eps/datos, notaria/datos, aerolinea/datos): son carpetas del
# repositorio, no volúmenes de Docker.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

PORTALES=(migracion cancilleria pensiones acceso beca aerolinea notaria eps contratacion banco discoteca)   # al revés de como se levantan
export UID_GID="$(id -u):$(id -g)"

for n in "${PORTALES[@]}"; do
  [ -f "$n/.env" ] && ( cd "$n" && docker compose -p "$n" --env-file .env down "$@" >/dev/null 2>&1 )
  [ -f "_comun/instancias/$n/.env" ] && ./_comun/compose.sh "$n" down "$@" >/dev/null 2>&1
  echo "  $n: bajado"
done
