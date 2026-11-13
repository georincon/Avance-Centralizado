#!/usr/bin/env bash
#
# Revoca la WALLET de un titular (la instalacion en su celular) en la lista de estado del proveedor de wallet.
# Sirve para perdida o robo del celular: despues, el trabajo periodico de los emisores (cada 8 h) revoca todos los
# documentos de esa wallet, no solo el PID.
#
#   ./revocar-wallet.sh <registraduria|uis> <titular>        # simulacion: muestra que revocaria, no toca nada
#   ./revocar-wallet.sh --si <registraduria|uis> <titular>   # revoca (pide escribir SI; --si lo omite en scripts: usar con --confirmo)
#
# El titular es la cedula (registraduria) o el usuario del estudiante (uis).
# Usa los indices que el emisor guardo al emitir (client_status_* = la wallet, key_storage_status_* = su modulo de
# claves). Solo alcanza credenciales emitidas desde que se guarda el titular (2026-10-30): las anteriores no tienen
# titular y se resuelven al vencer o por el trabajo periodico.
#
# Es IRREVERSIBLE: el bit de una lista de estado no vuelve a 0. Para revocar ademas los documentos del titular de
# inmediato (sin esperar el trabajo de 8 h) y/o bloquear su emision: ./revocar.sh [--bloquear] [--motivo fraud] ...
# Ver docs/REVOCACION.md y docs/RECUPERACION-WALLET-ARF.md
set -euo pipefail

uso() { sed -n '3,17p' "$0" | sed 's/^# \{0,1\}//'; exit 2; }

CONFIRMADO=0; APLICAR=0; ARGS=()
for a in "$@"; do case "$a" in --si) APLICAR=1 ;; --confirmo) CONFIRMADO=1 ;; -h|--help) uso ;; *) ARGS+=("$a") ;; esac; done
set -- "${ARGS[@]+"${ARGS[@]}"}"
[[ $# -eq 2 ]] || uso
STACK="$1"; SUJETO="$2"
[[ "$SUJETO" =~ ^[A-Za-z0-9._@-]{1,100}$ ]] || { echo "Titular no valido (solo letras, numeros y . _ @ -)" >&2; exit 2; }

case "$STACK" in
  registraduria|reg) DB=postgres-reg;         USUARIO=pid_issuer; TABLA=issued_credential ;;
  uis)               DB=postgres-uis-issuer;  USUARIO=uis_issuer; TABLA=academic_credential ;;
  *) echo "Stack desconocido: $STACK (registraduria | uis)" >&2; exit 2 ;;
esac
SERVICIO="${STATUSLIST_WALLET_URL:-http://status-list-spartans:5000/token_status_list/set}"
CONTENEDOR_ESTADO="${STATUSLIST_WALLET_CONTENEDOR:-status-list-spartans}"
IMAGEN="${REVOCAR_IMAGEN:-postgres:16-alpine}"   # cualquier imagen con wget

# Las dos listas (wallet y modulo de claves) de las credenciales vigentes del titular, sin repetir.
CONSULTA="select distinct u, i from (
  select client_status_list_uri u, client_status_list_index i from $TABLA where subject = '$SUJETO' and revoked_at is null and expires_at > now()
  union all
  select key_storage_status_list_uri, key_storage_status_list_index from $TABLA where subject = '$SUJETO' and revoked_at is null and expires_at > now()
) t where u is not null and i is not null order by 1, 2"
FILAS="$(docker exec "$DB" psql -U "$USUARIO" -d "$USUARIO" -At -F ' ' -c "$CONSULTA")" || { echo "No pude consultar $DB" >&2; exit 1; }

if [[ -z "$FILAS" ]]; then
  echo "El titular '$SUJETO' no tiene credenciales vigentes con titular registrado en $STACK: no hay wallet que revocar."
  echo "(Las emitidas antes del 2026-10-30 no guardan titular.)"
  exit 0
fi
N="$(wc -l <<<"$FILAS")"
echo "Wallet de '$SUJETO' en $STACK: $N entrada(s) de la lista de estado del proveedor de wallet:"
while read -r U I; do echo "  indice $I  ${U##*/token_status_list/}"; done <<<"$FILAS"

if [[ $APLICAR -ne 1 ]]; then
  echo; echo "Simulacion: no se revoco nada. Para revocar: ./revocar-wallet.sh --si $STACK $SUJETO"
  exit 0
fi
if [[ $CONFIRMADO -ne 1 ]]; then
  [[ -t 0 ]] || { echo "Sin terminal interactivo: agrega --confirmo para revocar." >&2; exit 2; }
  echo; echo "Vas a revocar la WALLET de '$SUJETO'. NO se puede deshacer."
  read -r -p "¿Seguro? Escribe SI para continuar (cualquier otra cosa cancela): " R
  [[ "$R" == "SI" ]] || { echo "Cancelado: no se revoco nada."; exit 1; }
fi

KEY="$(docker exec "$CONTENEDOR_ESTADO" printenv API_key 2>/dev/null || true)"
[[ -n "$KEY" ]] || { echo "El contenedor $CONTENEDOR_ESTADO no esta en marcha o no tiene API_key" >&2; exit 1; }

OK=0; MAL=0
while read -r U I; do
  RUTA="/token_status_list/${U#*/token_status_list/}"
  if docker run --rm --network "${NW_NETWORK:-avance-eudi}" "$IMAGEN" wget -qO- --header "X-API-Key: $KEY" \
       --post-data "idx=$I&uri=$RUTA&status=1" "$SERVICIO" >/dev/null 2>&1; then OK=$((OK+1)); else MAL=$((MAL+1)); echo "  fallo el indice $I" >&2; fi
done <<<"$FILAS"
echo "Revocadas: $OK  ·  Fallidas: $MAL"
echo "Los emisores marcaran los documentos de esa wallet en su proximo trabajo periodico (cada 8 h)."
[[ $MAL -eq 0 ]]
