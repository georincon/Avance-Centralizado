#!/usr/bin/env bash
# Levanta el portal del empleador y su verifier en el ecosistema Avance. Solo requiere Docker.
#
#   ./levantar.sh        → https://civica-desarrollo.avance.org.co/contratacion/
#
# 1. Crea .env desde .env.example si no existe.
# 2. Levanta (o actualiza) su verifier, ../_comun/levantar.sh contratacion. Su certificado de registro declara la
#    credencial académica (WRPRC_VCTS), no el PID. El verifier guarda cada presentación solo en memoria 30 min y
#    sus solicitudes (el QR) vencen a los 5.
# 3. Levanta el portal en la red avance-eudi y espera a que responda.
#
# Requiere que srv-nw-nginx enrute /contratacion (→ app-contratacion:80) y, para verificar, que el stack de la
# UIS esté arriba: la status list de sus credenciales vive ahí.

set -euo pipefail
cd "$(dirname "$0")"

[[ -f .env ]] || { echo "▸ Creando .env"; cp .env.example .env; }
set -a
# shellcheck disable=SC1091
source .env
set +a

echo "▸ Verifier del empleador (verifier-$VERIFIER_INSTANCIA)"
VERIFIER_NOMBRE="$NOMBRE_EMPLEADOR" VERIFIER_PROPOSITO="$PROPOSITO" CONSERVAR_PRESENTACIONES=PT30M VIGENCIA_SOLICITUD=PT5M \
  WRPRC_VCTS=urn:eudi:academic:credential:1 \
  ../_comun/levantar.sh "$VERIFIER_INSTANCIA" | sed 's/^/  │ /'

echo "▸ Levantando el portal"
docker compose -p contratacion --env-file .env up -d --build

echo -n "▸ Esperando al portal"
for _ in $(seq 1 60); do
  if curl -skf -o /dev/null "https://localhost/$PREFIJO/salud"; then
    echo " listo."
    cat <<MSG

  Portal               https://$DOMINIO/$PREFIJO/
  Su verifier          https://$DOMINIO/verifier-$VERIFIER_INSTANCIA/
  Pide                 solo programa, título y fecha de grado de graduados del nivel elegido; la identidad es un paso aparte

  Logs:                docker compose -p contratacion logs -f   (solo «paso, nivel, resultado»)
MSG
    exit 0
  fi
  echo -n "."; sleep 2
done
echo " no respondió en 2 minutos. Revisa: docker compose -p contratacion logs"
exit 1
