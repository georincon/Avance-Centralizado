#!/usr/bin/env bash
#
# Revoca credenciales de un titular y gestiona el bloqueo de emision y los avisos de revocacion.
#
#   ./revocar.sh [--si] [--motivo CODIGO] [--bloquear] <registraduria|uis> <titular>
#   ./revocar.sh bloquear   [--motivo CODIGO] <registraduria|uis> <titular>
#   ./revocar.sh desbloquear <registraduria|uis> <titular>
#   ./revocar.sh avisos      <registraduria|uis>          # avisos de revocacion sin entregar
#   ./revocar.sh avisado     <registraduria|uis> <id>     # confirma que ya se aviso al titular
#
# El titular es el numero de cedula (registraduria) o el numero de identificacion del estudiante (uis; es tambien su usuario).
# Motivos: user-request (lo pidio el titular), data-changed (cambiaron sus datos), fraud (posible fraude o
# suplantacion), legal (decision legal o administrativa), other (por defecto). El motivo se le informa al titular.
#
# Revocar PIDE CONFIRMACION (hay que escribir SI; cualquier otra respuesta cancela) y no se puede deshacer. Sin
# terminal interactivo y sin --si no revoca nada. --bloquear impide ademas que se le emitan credenciales nuevas.
# Revocar NUNCA borra datos del titular.
#
# Llama a la API interna del issuer, solo alcanzable desde la red docker (el HAProxy responde 404 a /internal/ desde
# fuera), por eso se ejecuta desde un contenedor efimero en la red `avance-eudi`. La clave se lee del contenedor del
# issuer (ISSUER_REVOCATION_API_KEY). Ver docs/REVOCACION.md
set -euo pipefail

uso() { sed -n '3,22p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

CONFIRMADO=0; BLOQUEAR=0; MOTIVO="other"; ARGS=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --si|-y|--yes) CONFIRMADO=1 ;;
    --bloquear)    BLOQUEAR=1 ;;
    --motivo)      [[ $# -ge 2 ]] || uso; MOTIVO="$2"; shift ;;
    -h|--help)     uso ;;
    *)             ARGS+=("$1") ;;
  esac
  shift
done
set -- "${ARGS[@]+"${ARGS[@]}"}"

ACCION="revocar"
case "${1:-}" in bloquear|desbloquear|avisos|avisado) ACCION="$1"; shift ;; esac

case "$MOTIVO" in user-request|data-changed|fraud|legal|other) ;; *) echo "Motivo no valido: $MOTIVO" >&2; exit 2 ;; esac

STACK="${1:-}"; DATO="${2:-}"
case "$ACCION" in
  avisos) [[ $# -eq 1 ]] || uso ;;
  *)      [[ $# -eq 2 ]] || uso ;;
esac
case "$STACK" in
  registraduria|reg) CONTAINER=pid-issuer-reg; BASE="http://pid-issuer:8080/pid-registraduria" ;;
  uis)               CONTAINER=uis-issuer;     BASE="http://uis-issuer:8080/uis-issuer" ;;
  *) echo "Stack desconocido: $STACK (registraduria | uis)" >&2; exit 2 ;;
esac
if [[ "$ACCION" == avisado ]]; then
  [[ "$DATO" =~ ^[0-9]{1,18}$ ]] || { echo "El id del aviso debe ser un numero" >&2; exit 2; }
elif [[ "$ACCION" != avisos ]]; then
  [[ "$DATO" =~ ^[A-Za-z0-9._@-]{1,100}$ ]] || { echo "Titular no valido (solo letras, numeros y . _ @ -)" >&2; exit 2; }
fi

KEY="$(docker exec "$CONTAINER" printenv ISSUER_REVOCATION_API_KEY 2>/dev/null || true)"
[[ -n "$KEY" ]] || { echo "El contenedor $CONTAINER no esta en marcha o no tiene ISSUER_REVOCATION_API_KEY" >&2; exit 1; }

IMAGE="${REVOCAR_IMAGEN:-postgres:16-alpine}"   # cualquier imagen con wget
llamar() { # POST <ruta> [json]   |   GET <ruta>
  local metodo="$1" ruta="$2" cuerpo="${3:-}"
  if [[ "$metodo" == GET ]]; then
    docker run --rm --network "${NW_NETWORK:-avance-eudi}" "$IMAGE" wget -qO- --header "X-Api-Key: $KEY" "$BASE$ruta"
  else
    docker run --rm --network "${NW_NETWORK:-avance-eudi}" "$IMAGE" \
      wget -qO- --header 'Content-Type: application/json' --header "X-Api-Key: $KEY" --post-data "$cuerpo" "$BASE$ruta"
  fi
  echo
}

confirmar() { # texto...
  if [[ $CONFIRMADO -ne 1 ]]; then
    [[ -t 0 ]] || { echo "Sin terminal interactivo: usa --si para confirmar." >&2; exit 2; }
    printf '%s\n' "$@"
    read -r -p "¿Seguro? Escribe SI para continuar (cualquier otra cosa cancela): " RESPUESTA
    [[ "$RESPUESTA" == "SI" ]] || { echo "Cancelado: no se cambio nada."; exit 1; }
  fi
}

case "$ACCION" in
  revocar)
    EXTRA=""; TXT=""
    if [[ $BLOQUEAR -eq 1 ]]; then EXTRA=',"block":true'; TXT="Ademas se BLOQUEARA la emision de credenciales nuevas a este titular."; fi
    confirmar "Vas a revocar TODAS las credenciales vigentes de '$DATO' en $STACK (motivo: $MOTIVO)." \
              "Dejaran de ser validas ante cualquier verificador y NO se puede deshacer. No se borra ningun dato del titular." \
              ${TXT:+"$TXT"}
    llamar POST /internal/v1/credentials/revoke "{\"subject\":\"$DATO\",\"reason\":\"$MOTIVO\"$EXTRA}" ;;
  bloquear)
    confirmar "Vas a BLOQUEAR la emision de credenciales nuevas a '$DATO' en $STACK (motivo: $MOTIVO)." \
              "Sus datos y credenciales actuales no cambian. Se puede levantar con: ./revocar.sh desbloquear $STACK $DATO"
    llamar POST /internal/v1/subjects/block "{\"subject\":\"$DATO\",\"reason\":\"$MOTIVO\"}" ;;
  desbloquear)
    confirmar "Vas a permitir de nuevo la emision de credenciales a '$DATO' en $STACK."
    llamar POST /internal/v1/subjects/unblock "{\"subject\":\"$DATO\"}" ;;
  avisos)
    llamar GET /internal/v1/revocation-notices ;;
  avisado)
    confirmar "¿Confirmas que ya informaste al titular del aviso $DATO de la revocacion y de su motivo?"
    llamar POST "/internal/v1/revocation-notices/$DATO/notified" "" ;;
esac
