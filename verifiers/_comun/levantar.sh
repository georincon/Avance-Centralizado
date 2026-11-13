#!/usr/bin/env bash
# Levanta UNA instancia del verifier (endpoint + UI oficiales) en el ecosistema Avance. Se pueden
# tener todas las que se quiera, cada una bajo su prefijo. Solo requiere Docker.
#
#   ./levantar.sh <nombre>        → https://civica-desarrollo.avance.org.co/verifier-<nombre>/
#
# 1. Si no existe instancias/<nombre>/.env, lo crea desde .env.example con secretos
#    aleatorios y PREFIJO=verifier-<nombre>. Si existe, le agrega las variables nuevas. Antes del
#    primer arranque conviene editar VERIFIER_NOMBRE y VERIFIER_PROPOSITO: van en el certificado y
#    la wallet los muestra. También se pueden dar al crearla:
#      VERIFIER_NOMBRE="…" VERIFIER_PROPOSITO="…" ./levantar.sh <n>
# 2. Genera la clave y el CSR del verifier (instancias/<nombre>/secretos/).
# 3. Consigue el certificado de acceso del tl-publisher de Avance: solo, si tiene la clave de
#    operador (TL_OPERATOR_KEY); si no, explica cómo pedirlo. Lo renueva si vence en menos de 30
#    días o si no es apto para la wallet.
# 4. Descarga del tl-publisher la URL del trust-validator y qué documentos son PID, QEAA o PubEAA
#    (vuelve a ejecutarlo cuando se registre un emisor nuevo).
# 5. Levanta el stack (proyecto verifier-<nombre>) y espera a que responda por el proxy único.
#
# Requiere que srv-nw-nginx enrute /verifier-<nombre> (ver docs/PLAN.md, Fase 2).
# Para el resto de comandos de compose: ./compose.sh <nombre> ps|logs|down|…

set -euo pipefail
cd "$(dirname "$0")"

nombre="${1:?Uso: ./levantar.sh <nombre>}"
[[ "$nombre" =~ ^[a-z0-9][a-z0-9-]*$ ]] || { echo "El nombre solo admite minúsculas, dígitos y '-'" >&2; exit 1; }

dir="instancias/$nombre"
env="$dir/.env"
aleatorio() { head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9'; }

# --- 1. .env --------------------------------------------------------------------------
if [[ ! -f "$env" ]]; then
  echo "▸ Creando $env"
  mkdir -p "$dir"
  while IFS= read -r linea; do
    case "$linea" in
      *=generar) echo "${linea%=generar}=$(aleatorio)" ;;
      INSTANCIA=) echo "INSTANCIA=$nombre" ;;
      PREFIJO=) echo "PREFIJO=verifier-$nombre" ;;
      # Quien lo crea puede dar ya el nombre y el propósito.
      VERIFIER_NOMBRE=*) v="${linea#*=}"; echo "VERIFIER_NOMBRE=\"${VERIFIER_NOMBRE:-${v//\"/}}\"" ;;
      VERIFIER_PROPOSITO=*) v="${linea#*=}"; echo "VERIFIER_PROPOSITO=\"${VERIFIER_PROPOSITO:-${v//\"/}}\"" ;;
      CONSERVAR_PRESENTACIONES=*) echo "CONSERVAR_PRESENTACIONES=${CONSERVAR_PRESENTACIONES:-${linea#*=}}" ;;
      VIGENCIA_SOLICITUD=*) echo "VIGENCIA_SOLICITUD=${VIGENCIA_SOLICITUD:-${linea#*=}}" ;;
      WRPRC_VCTS=*) echo "WRPRC_VCTS=${WRPRC_VCTS:-${linea#*=}}" ;;
      *) echo "$linea" ;;
    esac
  done < .env.example > "$env"
else
  while IFS= read -r linea; do
    [[ "$linea" =~ ^([A-Z_][A-Z0-9_]*)= ]] || continue
    grep -q "^${BASH_REMATCH[1]}=" "$env" && continue
    echo "▸ Agregando ${BASH_REMATCH[1]} a $env"
    if [[ "$linea" == *=generar ]]; then echo "${linea%=generar}=$(aleatorio)" >> "$env"; else echo "$linea" >> "$env"; fi
  done < .env.example
fi

set -a
# shellcheck disable=SC1090
source "$env"
set +a

docker network inspect avance-eudi >/dev/null 2>&1 \
  || { echo "Falta la red avance-eudi: levanta el ecosistema (../../up.sh)." >&2; exit 1; }
mkdir -p "$dir/secretos"

claves() {
  docker run --rm --user "$(id -u):$(id -g)" \
    -e HOME=/tmp -e SECRETOS_DIR=/secretos \
    -e DOMINIO -e PREFIJO -e KEYSTORE_PASSWORD -e VERIFIER_NOMBRE -e VERIFIER_PAIS \
    -e VERIFIER_PROPOSITO -e ESQUEMA_CLIENT_ID -e WRPRC_VCTS \
    -v "$PWD/scripts:/scripts:ro" -v "$PWD/$dir/secretos:/secretos" \
    eclipse-temurin:21-jre bash /scripts/generar-claves.sh
}

# --- 2 y 3. Clave, CSR y certificado de acceso -------------------------------------------
echo "▸ Material criptográfico ($dir/secretos/)"
claves
estado="$(cat "$dir/secretos/estado")"

if [[ "$estado" != ok ]]; then
  # Clave de operador: la del .env, o la del tl-publisher si está en esta máquina.
  clave="${CLAVE_OPERADOR:-}"
  tlp_env="../../srv-tl-publisher/docker-compose/.env"
  if [[ -z "$clave" && -f "$tlp_env" ]]; then
    clave="$(grep -E '^TL_OPERATOR_KEY=' "$tlp_env" | cut -d= -f2- || true)"
  fi

  if [[ -z "$clave" ]]; then
    cat <<MSG

  Falta el certificado de acceso del verifier ($estado).
  Lo emite el operador del tl-publisher:

    1. En $TL_PUBLISHER_URL/admin → Actores → Registrar verifier
         Nombre:   $VERIFIER_NOMBRE
         Dominio:  $DOMINIO
         CSR:      el contenido de $dir/secretos/acceso.csr
    2. Guarda la cadena que descarga (cadena.pem) en $dir/secretos/cadena.pem
    3. Vuelve a ejecutar ./levantar.sh $nombre

  O pon CLAVE_OPERADOR en $env y levantar.sh lo registra solo.
MSG
    exit 1
  fi

  echo "▸ Pidiendo el certificado de acceso al tl-publisher ($estado)"
  docker run --rm --user "$(id -u):$(id -g)" \
    -e SECRETOS_DIR=/secretos -e TL_PUBLISHER_URL -e CLAVE_OPERADOR="$clave" \
    -e DOMINIO -e VERIFIER_NOMBRE -e VERIFIER_PAIS \
    -v "$PWD/scripts:/scripts:ro" -v "$PWD/$dir/secretos:/secretos" \
    python:3.13-alpine python3 /scripts/registrar.py
  claves
  estado="$(cat "$dir/secretos/estado")"
  [[ "$estado" == ok ]] || { echo "El certificado recibido no sirve: $estado" >&2; exit 1; }
fi

# --- 4. Capa de confianza ----------------------------------------------------------------
# La integración es pública y vive en la raíz del dominio (/tl-api), no bajo /tl-publisher.
base="${TL_PUBLISHER_URL%/tl-publisher}"
echo "▸ Trust-validator y clases de documentos ($base/tl-api/v1/integracion/verifier.env)"
curl -sfS "$base/tl-api/v1/integracion/verifier.env" -o "$dir/confianza.env.nuevo" \
  && mv "$dir/confianza.env.nuevo" "$dir/confianza.env" \
  || { rm -f "$dir/confianza.env.nuevo"
       [[ -f "$dir/confianza.env" ]] || { echo "No se pudo descargar y no hay copia anterior." >&2; exit 1; }
       echo "  (no responde: se usa la copia anterior)"; }
grep '^# Aviso' "$dir/confianza.env" | sed 's/^# /  /' || true

# --- 5. Stack ------------------------------------------------------------------------------
echo "▸ Levantando verifier-$nombre"
# Recrear: el client_id o las clases pueden haber cambiado y viajan en env_file.
./compose.sh "$nombre" up -d --force-recreate

url="https://$DOMINIO/$PREFIJO"
client_id="$(grep '^VERIFIER_ORIGINALCLIENTID=' "$dir/secretos/generado.env" | cut -d= -f2-)"
echo -n "▸ Esperando al verifier"
for _ in $(seq 1 90); do
  # Por el proxy único de esta máquina (srv-nw-nginx), sin salir a internet.
  if curl -skf -o /dev/null "https://localhost/$PREFIJO/wallet/public-keys.json" \
     && curl -skf -o /dev/null "https://localhost/$PREFIJO/"; then
    echo " listo."
    cat <<MSG

  Verifier (UI)        $url/
  API                  $url/ui/presentations/v2   (POST, DCQL)
  client_id            $ESQUEMA_CLIENT_ID:$client_id
  Certificado          $dir/secretos/cadena.pem  (actor #$(cat "$dir/secretos/actor" 2>/dev/null || echo '?') del tl-publisher)

  Probar:              abre $url/ en el computador, elige PID, y escanea el QR con la wallet
                       (en el teléfono: el botón abre la wallet directamente)
  Emisor nuevo:        ./levantar.sh $nombre   (vuelve a leer las clases de documentos)
  Logs:                ./compose.sh $nombre logs -f verifier-api
MSG
    exit 0
  fi
  echo -n "."
  sleep 2
done

echo " no respondió en 3 minutos."
echo "Revisa: ./compose.sh $nombre ps, ./compose.sh $nombre logs verifier-api, y que srv-nw-nginx enrute /$PREFIJO."
exit 1
