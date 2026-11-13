#!/usr/bin/env bash
#
# Baja TODO el ecosistema EUDI de Avance.
#
#   ./down.sh        baja los servicios y quita los contenedores
#   ./down.sh -v     baja tambien los volumenes (SE BORRAN LOS DATOS)
#
# POR QUE RECORRE LOS PROYECTOS UNO A UNO
#
# Los contenedores pueden estar bajo dos proyectos distintos: el de la raiz
# (`avance`, si se levantaron con `./up.sh`) o el de su propio stack
# (`registraduria`, `spartans`, `uis-issuer`...), si se levantaron de una en
# una. Bajar solo la raiz dejaria en pie los del segundo caso. Por eso se pasa
# por los dos.
#
# Se mira cada proyecto con `ps --all` (y no solo los que estan en marcha): un
# contenedor parado tambien es un resto, y `down` se lo lleva igual.
#
# La red compartida 'avance-eudi' NO se borra: no pertenece a ningun proyecto.
#
# Los portales de verifiers/ se bajan primero (verifiers/bajar-portales.sh): son dos proyectos por portal
# (la app y su verifier) y no estan en el compose raiz.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# 0) Los portales de ejemplo de verifiers/ (cada app y su verifier). No siguen la convencion
#    <proyecto>/docker-compose/, asi que el recorrido de abajo no los vería.
echo ">> Bajando los portales de ejemplo..."
verifiers/bajar-portales.sh "$@" || true

# 1) Lo que este bajo el proyecto de la raiz.
echo ">> Bajando el ecosistema (proyecto raiz)..."
docker compose down "$@" || true

# 2) Cada stack por su propio compose, por si se levantaron de una en una.
for compose in */docker-compose/docker-compose.yaml; do
  proyecto="${compose%%/*}"
  en_pie="$(docker compose -f "$compose" ps --all -q 2>/dev/null | wc -l)"
  [ "$en_pie" -gt 0 ] || continue
  echo ">> Bajando el stack de $proyecto..."
  docker compose -f "$compose" down "$@" || true
done

echo
echo "Ecosistema detenido. La red compartida 'avance-eudi' se conserva."
