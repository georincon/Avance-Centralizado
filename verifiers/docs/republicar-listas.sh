#!/usr/bin/env bash
#
# Republica las listas de confianza (secuencia nueva) para que `current.json` lleve el bloque
# LoTE (ETSI TS 119 602) que lee la wallet, y comprueba el resultado.
#
# Hace falta una vez tras el cambio de `srv-tl-publisher/app/lote.py`. Crea una secuencia nueva
# en cada lista (no se puede deshacer; las anteriores quedan). Tambien incorpora a RPREG los
# verifiers registrados desde la ultima publicacion (p. ej. la discoteca).
#
#   ./republicar-listas.sh
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

RAIZ="$(cd ../.. && pwd)"
K="$(grep '^TL_OPERATOR_KEY=' "$RAIZ/srv-tl-publisher/docker-compose/.env" | cut -d= -f2-)"
B="https://civica-desarrollo.avance.org.co"

echo ">> Republicando..."
curl -fsS -X POST -H "X-Api-Key: $K" "$B/tl-publisher/api/v1/publicar"
echo
echo ">> Comprobación: ¿lleva el bloque LoTE cada lista?"
python3 - "$B" <<'PY'
import base64, json, sys, urllib.request

base = sys.argv[1]
for lista in ("PID", "WP", "RPREG", "PUBEAA", "QEAA"):
    t = urllib.request.urlopen(f"{base}/tl-publisher/TL/{lista}/current.json").read().decode().strip().split(".")
    p = json.loads(base64.urlsafe_b64decode(t[1] + "=" * (-len(t[1]) % 4)))
    lote = p.get("LoTE")
    if not lote:
        print(f"  {lista:7} SIN LoTE  <-- la wallet no podra leerla")
        continue
    info = lote["ListAndSchemeInformation"]
    tipos = sorted({s["ServiceInformation"]["ServiceTypeIdentifier"].split("SvcType/")[-1].split("Svctype/")[-1]
                    for e in lote["TrustedEntitiesList"] for s in e["TrustedEntityServices"]})
    print(f"  {lista:7} secuencia {info['LoTESequenceNumber']}  {tipos}")
PY
