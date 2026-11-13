#!/usr/bin/env bash
# Levanta el portal del fondo de pensiones y su verifier en el ecosistema Avance. Solo requiere Docker.
#
#   ./levantar.sh        → https://civica-desarrollo.avance.org.co/pensiones/
#
# 1. Crea .env desde .env.example si no existe.
# 2. Levanta (o actualiza) su verifier, ../_comun/levantar.sh pensiones. Su certificado de registro declara el PID y la fe de vida de la
#    notaría (WRPRC_VCTS); la presentación es combinada (dos credenciales en una sola solicitud).
# 3. Levanta el portal en la red avance-eudi y espera a que responda.
#
# Requiere que srv-nw-nginx enrute /pensiones (→ app-pensiones:80), la notaría arriba (su status list es la de la fe de vida) y la
# Registraduría arriba (status list del PID).

set -euo pipefail
cd "$(dirname "$0")"

[[ -f .env ]] || { echo "▸ Creando .env"; cp .env.example .env; }
set -a
# shellcheck disable=SC1091
source .env
set +a

echo "▸ Verifier del fondo (verifier-$VERIFIER_INSTANCIA)"
VERIFIER_NOMBRE="$NOMBRE_ENTIDAD" VERIFIER_PROPOSITO="$PROPOSITO" CONSERVAR_PRESENTACIONES=PT10M VIGENCIA_SOLICITUD=PT5M \
  WRPRC_VCTS="urn:eudi:pid:1,urn:co:notaria:fe-de-vida:1" \
  ../_comun/levantar.sh "$VERIFIER_INSTANCIA" | sed 's/^/  │ /'

echo "▸ Levantando el portal"
docker compose -p pensiones --env-file .env up -d --build

echo -n "▸ Esperando al portal"
for _ in $(seq 1 60); do
  if curl -skf -o /dev/null "https://localhost/$PREFIJO/salud"; then
    echo " listo."
    cat <<MSG

  Portal               https://$DOMINIO/$PREFIJO/
  Su verifier          https://$DOMINIO/verifier-$VERIFIER_INSTANCIA/
  Pide                 fe de vida (titular, vigencia, entidad) + PID (nombre y documento) en una sola presentación

  Logs:                docker compose -p pensiones logs -f   (solo «resultado»)
MSG
    exit 0
  fi
  echo -n "."; sleep 2
done
echo " no respondió en 2 minutos. Revisa: docker compose -p pensiones logs"
exit 1
