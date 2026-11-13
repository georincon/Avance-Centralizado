#!/usr/bin/env bash
#
# Republica SOLO la lista RPREG (secuencia nueva) y comprueba que lleva el bloque LoTE.
# Las demas listas ya salieron bien en `republicar-listas.sh`; RPREG fallo porque el verifier de la
# discoteca estaba registrado con campos de contacto mal formados (corregido en admin.py).
#
#   ./republicar-rpreg.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

RAIZ="$(cd ../.. && pwd)"
K="$(grep '^TL_PUBLISHER_API_KEY=' "$RAIZ/srv-tl-publisher/docker-compose/.env" | cut -d= -f2-)"
B="https://civica-desarrollo.avance.org.co"

echo ">> Republicando RPREG..."
curl -fsS -X POST -H "X-API-Key: $K" "$B/tl-api/v1/publish/RPREG"
echo
echo ">> Comprobación:"
python3 - "$B" <<'PY'
import base64, json, sys, urllib.request

base = sys.argv[1]
t = urllib.request.urlopen(f"{base}/tl-publisher/TL/RPREG/current.json").read().decode().strip().split(".")
p = json.loads(base64.urlsafe_b64decode(t[1] + "=" * (-len(t[1]) % 4)))
lote = p.get("LoTE")
if not lote:
    sys.exit("  RPREG SIN LoTE  <-- la wallet no podra leerla")
info = lote["ListAndSchemeInformation"]
servicios = [s["ServiceInformation"] for e in lote["TrustedEntitiesList"] for s in e["TrustedEntityServices"]]
print(f"  RPREG secuencia {info['LoTESequenceNumber']}: {len(servicios)} ancla(s) de CA")
for s in servicios:
    print("   -", s["ServiceName"][0]["value"][:70])
PY
