#!/usr/bin/env bash
# Levanta el portal de control migratorio y su verifier en el ecosistema Avance. Solo requiere Docker.
#
#   ./levantar.sh        → https://civica-desarrollo.avance.org.co/migracion/
#
# 1. Crea .env desde .env.example si no existe.
# 2. Levanta (o actualiza) su verifier, ../_comun/levantar.sh migracion. Su certificado declara el PID y el pasaporte (WRPRC_VCTS).
# 3. Levanta el portal en la red avance-eudi y espera a que responda.
#
# Requiere que srv-nw-nginx enrute /migracion (→ app-migracion:80) y que el stack de la Registraduría esté arriba: la status list
# del PID vive ahí.

set -euo pipefail
cd "$(dirname "$0")"

[[ -f .env ]] || { echo "▸ Creando .env"; cp .env.example .env; }
set -a
# shellcheck disable=SC1091
source .env
set +a

echo "▸ Verifier de Migración (verifier-$VERIFIER_INSTANCIA)"
VERIFIER_NOMBRE="$NOMBRE_ENTIDAD" VERIFIER_PROPOSITO="$PROPOSITO" WRPRC_VCTS="urn:eudi:pid:1,urn:co:cancilleria:pasaporte:1" CONSERVAR_PRESENTACIONES=PT10M VIGENCIA_SOLICITUD=PT5M \
  ../_comun/levantar.sh "$VERIFIER_INSTANCIA" | sed 's/^/  │ /'

echo "▸ Levantando el portal"
docker compose -p migracion --env-file .env up -d --build

echo -n "▸ Esperando al portal"
for _ in $(seq 1 60); do
  if curl -skf -o /dev/null "https://localhost/$PREFIJO/salud"; then
    echo " listo."
    cat <<MSG

  Portal               https://$DOMINIO/$PREFIJO/
  Su verifier          https://$DOMINIO/verifier-$VERIFIER_INSTANCIA/
  Pide                 PID (nombre, documento, retrato) y pasaporte de la Cancillería, para mostrarlos al funcionario

  Logs:                docker compose -p migracion logs -f   (solo «permitido» o «denegado»)
MSG
    exit 0
  fi
  echo -n "."; sleep 2
done
echo " no respondió en 2 minutos. Revisa: docker compose -p migracion logs"
exit 1
