#!/usr/bin/env bash
#
# Fase 1: añade la URL OpenID4VCI de su emisor a los puntos de suministro de dos actores del
# tl-publisher y republica las listas. NO reemplaza los puntos que ya tienen: solo añade.
#
#   #14  PID Registraduria  + https://civica-desarrollo.avance.org.co/pid-registraduria
#   #16  UIS Issuer PubEAA  + https://civica-desarrollo.avance.org.co/uis-issuer
#
# Republicar crea una secuencia nueva en cada lista (no se puede deshacer; las anteriores quedan).
# Antes de tocar nada guarda el registro en actores-antes.json, junto a este script.
#
# Los dos emisores no pueden estar arriba a la vez (sus Keycloak chocan en el borde). El tl-publisher
# recuerda la ultima lectura buena de cada uno, asi que basta con leer cada emisor una vez estando arriba:
#   1. Con la Registraduria arriba: ejecutar este script (lee y recuerda la Registraduria).
#   2. Cambiar a la UIS en el panel y pedir una vez
#        curl -s https://civica-desarrollo.avance.org.co/tl-api/v1/integracion/verifier.env
#      (lee y recuerda la UIS). Desde ahi salen las dos clases, este arriba el que este.
#
#   ./corregir-puntos.sh            ejecuta
#   ./corregir-puntos.sh --sin-publicar   solo cambia los puntos
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

RAIZ="$(cd ../.. && pwd)"
K="$(grep '^TL_OPERATOR_KEY=' "$RAIZ/srv-tl-publisher/docker-compose/.env" | cut -d= -f2-)"
B="https://civica-desarrollo.avance.org.co/tl-publisher/api/v1"

# La copia se hace una sola vez: es el estado ANTES del primer cambio.
[ -f actores-antes.json ] || curl -fsS -H "X-Api-Key: $K" "$B/actores" -o actores-antes.json
echo ">> Copia del registro: $PWD/actores-antes.json"

python3 - actores-antes.json "$K" "$B" <<'EOF'
import json, sys, urllib.request

antes, K, B = json.load(open(sys.argv[1])), sys.argv[2], sys.argv[3]
nuevo = {
    14: "https://civica-desarrollo.avance.org.co/pid-registraduria",
    16: "https://civica-desarrollo.avance.org.co/uis-issuer",
}
for a in antes:
    if a["id"] not in nuevo:
        continue
    puntos = list(a["puntos"])
    if nuevo[a["id"]] not in puntos:
        puntos.append(nuevo[a["id"]])
    r = urllib.request.Request(
        f"{B}/actores/{a['id']}/puntos", method="PUT",
        data=json.dumps({"puntos": puntos}).encode(),
        headers={"X-Api-Key": K, "Content-Type": "application/json"})
    print(f">> actor #{a['id']}:", urllib.request.urlopen(r).read().decode())
EOF

if [ "${1:-}" != "--sin-publicar" ]; then
  echo ">> Republicando las listas..."
  curl -fsS -X POST -H "X-Api-Key: $K" "$B/publicar"
  echo
fi

echo ">> Comprobación:"
curl -fsS https://civica-desarrollo.avance.org.co/tl-api/v1/integracion/verifier.env | grep -v '^# Aviso' | tail -3
