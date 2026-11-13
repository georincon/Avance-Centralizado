#!/usr/bin/env bash
# Levanta la discoteca y su verifier en el ecosistema Avance. Solo requiere Docker.
#
#   ./levantar.sh        → https://civica-desarrollo.avance.org.co/discoteca/
#
# 1. Crea .env desde .env.example si no existe.
# 2. Levanta (o actualiza) su verifier, ../_comun/levantar.sh discoteca, con el nombre
#    del local, su propósito y sin guardar presentaciones más de 5 minutos.
# 3. Levanta la discoteca en la red avance-eudi y espera a que responda.
#
# Requiere que srv-nw-nginx enrute /discoteca (→ app-discoteca:80).

set -euo pipefail
cd "$(dirname "$0")"

[[ -f .env ]] || { echo "▸ Creando .env"; cp .env.example .env; }
set -a
# shellcheck disable=SC1091
source .env
set +a

echo "▸ Verifier de la discoteca (verifier-$VERIFIER_INSTANCIA)"
# Que el verifier tampoco conserve lo que recibe: 5 min (lo borra en su barrido
# de cada 30 min) y solicitudes que vencen en 5 min.
VERIFIER_NOMBRE="$NOMBRE" VERIFIER_PROPOSITO="$PROPOSITO" CONSERVAR_PRESENTACIONES=PT5M VIGENCIA_SOLICITUD=PT5M \
  ../_comun/levantar.sh "$VERIFIER_INSTANCIA" | sed 's/^/  │ /'

echo "▸ Levantando la discoteca"
docker compose -p discoteca --env-file .env up -d --build

echo -n "▸ Esperando a la discoteca"
for _ in $(seq 1 60); do
  if curl -skf -o /dev/null "https://localhost/$PREFIJO/salud"; then
    echo " lista."
    cat <<MSG

  Puerta               https://$DOMINIO/$PREFIJO/   (ábrela en la tablet o el teléfono del personal)
  Su verifier          https://$DOMINIO/verifier-$VERIFIER_INSTANCIA/
  Pide                 solo age_equal_or_over.$EDAD_MINIMA del PID; no guarda nada

  Logs:                docker compose -p discoteca logs -f   (solo «entra», «menor» o «error»)
MSG
    exit 0
  fi
  echo -n "."; sleep 2
done
echo " no respondió en 2 minutos. Revisa: docker compose -p discoteca logs"
exit 1
