#!/usr/bin/env bash
# Levanta el portal de admisiones y becas y su verifier en el ecosistema Avance. Solo requiere Docker.
#
#   ./levantar.sh        → https://civica-desarrollo.avance.org.co/beca/
#
# 1. Crea .env desde .env.example si no existe.
# 2. Levanta (o actualiza) su verifier, ../_comun/levantar.sh beca. Su certificado de registro declara el PID y la
#    credencial académica (WRPRC_VCTS): la presentación es combinada (dos credenciales en una sola solicitud).
# 3. Se asegura de que estén arriba las DOS listas de estado: la del PID (status-list-reg, stack de la Registraduría) y la
#    de la credencial académica (status-list-uis, del stack de la UIS). Esa segunda se levanta sola: no arranca el Keycloak
#    de la UIS, que chocaría con el de la Registraduría.
# 4. Levanta el portal en la red avance-eudi y espera a que responda.
#
# Requiere que srv-nw-nginx enrute /beca (→ app-beca:80).

set -euo pipefail
cd "$(dirname "$0")"

[[ -f .env ]] || { echo "▸ Creando .env"; cp .env.example .env; }
set -a
# shellcheck disable=SC1091
source .env
set +a

echo "▸ Verifier de admisiones (verifier-$VERIFIER_INSTANCIA)"
VERIFIER_NOMBRE="$NOMBRE_ENTIDAD" VERIFIER_PROPOSITO="$PROPOSITO" CONSERVAR_PRESENTACIONES=PT30M VIGENCIA_SOLICITUD=PT5M \
  WRPRC_VCTS="urn:eudi:pid:1,urn:eudi:academic:credential:1" \
  ../_comun/levantar.sh "$VERIFIER_INSTANCIA" | sed 's/^/  │ /'

echo "▸ Listas de estado (PID y credencial académica)"
for s in status-list-reg status-list-uis; do
  if [[ "$(docker inspect -f '{{.State.Running}}' "$s" 2>/dev/null)" == "true" ]]; then
    echo "  $s: arriba"
  elif [[ "$s" == "status-list-uis" ]]; then
    ( cd ../../eudi-srv-issuer-pid-uis/docker-compose && docker compose -p uis-issuer up -d --no-deps status-list-uis >/dev/null 2>&1 ) \
      && echo "  $s: levantada sola" || echo "  $s: NO se pudo levantar (verificar la credencial académica fallará)"
  else
    echo "  $s: ABAJO. Levanta el stack de la Registraduría desde el panel (verificar el PID fallará)."
  fi
done

echo "▸ Levantando el portal"
docker compose -p beca --env-file .env up -d --build

echo -n "▸ Esperando al portal"
for _ in $(seq 1 60); do
  if curl -skf -o /dev/null "https://localhost/$PREFIJO/salud"; then
    echo " listo."
    cat <<MSG

  Portal               https://$DOMINIO/$PREFIJO/
  Su verifier          https://$DOMINIO/verifier-$VERIFIER_INSTANCIA/
  Pide                 PID: solo «mayor de 18». Académica: nivel, estado, promedio y programa. La identidad es un paso aparte

  Logs:                docker compose -p beca logs -f   (solo «convocatoria, paso, resultado»)
MSG
    exit 0
  fi
  echo -n "."; sleep 2
done
echo " no respondió en 2 minutos. Revisa: docker compose -p beca logs"
exit 1
