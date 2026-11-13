#!/usr/bin/env bash
#
# Levanta srv-andes-scd (simulador del prestador de firma remota).
#
#   ./levantar.sh            construye (si hace falta) y levanta
#   ./levantar.sh --build    reconstruye la imagen
#   ./levantar.sh --parar    lo detiene
#
# Necesita la red `avance-eudi` (la crea ../up.sh) y la sub-CA QTSP de la PKI
# (../srv-tl-publisher/pki/subca/qtsp, que crea crear-pki.sh).
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

if [ "${1:-}" = "--parar" ]; then
  docker compose -p andes-scd --env-file .env down
  exit 0
fi

[ -d ../srv-tl-publisher/pki/subca/qtsp/private ] || {
  echo "ERROR: falta la sub-CA QTSP (../srv-tl-publisher/pki/subca/qtsp). Crea la PKI primero." >&2
  exit 1
}
docker network inspect avance-eudi >/dev/null 2>&1 || {
  echo "ERROR: falta la red avance-eudi. Levanta el ecosistema con ../up.sh primero." >&2
  exit 1
}

[ -f .env ] || { cp .env.example .env; echo "==> .env creado desde .env.example"; }
mkdir -p datos
UID_GID="$(id -u):$(id -g)" docker compose -p andes-scd --env-file .env up -d ${1:+"$1"}

echo "==> Esperando a que responda..."
for _ in $(seq 1 30); do
  if docker compose -p andes-scd --env-file .env exec -T andes-scd python -c \
      "import urllib.request;urllib.request.urlopen('http://localhost/health')" >/dev/null 2>&1; then
    # shellcheck disable=SC1091
    . ./.env
    echo "==> Listo: https://${DOMINIO}/${PREFIJO}/"
    exit 0
  fi
  sleep 2
done
echo "ERROR: no respondio a tiempo. Revisa: docker compose -p andes-scd --env-file .env logs" >&2
exit 1
