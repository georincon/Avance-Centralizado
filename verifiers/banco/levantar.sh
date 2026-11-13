#!/usr/bin/env bash
# Levanta el banco y su verifier en el ecosistema Avance. Solo requiere Docker.
#
#   ./levantar.sh        → https://civica-desarrollo.avance.org.co/banco/
#
# 1. Crea .env desde .env.example si no existe.
# 2. Levanta (o actualiza) su verifier, ../_comun/levantar.sh banco, con el nombre del banco y su
#    propósito. El verifier guarda cada presentación (nombre, fecha de nacimiento, foto) solo en
#    memoria y las borra a los 30 minutos; sus solicitudes (el QR) vencen a los 5.
# 3. Genera la PKI del banco como EMISOR (CA raíz + certificado de firma) en secretos/.
# 4. Levanta el banco en la red avance-eudi y espera a que responda.
# 5. Lo registra en el tl-publisher como emisor de la lista QEAA (solo la primera vez), para que la wallet confíe en
#    la credencial de cuenta bancaria que emite, y pone su CA como ancla del trust-validator.
#
# Requiere que srv-nw-nginx enrute /banco (→ app-banco:80).

set -euo pipefail
cd "$(dirname "$0")"

[[ -f .env ]] || { echo "▸ Creando .env"; cp .env.example .env; }
set -a
# shellcheck disable=SC1091
source .env
set +a

echo "▸ Verifier del banco (verifier-$VERIFIER_INSTANCIA)"
VERIFIER_NOMBRE="$NOMBRE_BANCO" VERIFIER_PROPOSITO="$PROPOSITO" CONSERVAR_PRESENTACIONES=PT30M VIGENCIA_SOLICITUD=PT5M \
  ../_comun/levantar.sh "$VERIFIER_INSTANCIA" | sed 's/^/  │ /'

echo "▸ PKI del banco como emisor (secretos/)"
mkdir -p secretos datos
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e SECRETOS_DIR=/secretos \
  -e NOMBRE_BANCO -e PAIS \
  -v "$PWD/scripts:/scripts:ro" -v "$PWD/secretos:/secretos" \
  eclipse-temurin:21-jre bash /scripts/generar-claves.sh

echo "▸ Levantando el banco"
mkdir -p datos
export UID_GID="$(id -u):$(id -g)"
docker compose -p banco --env-file .env up -d --build

echo -n "▸ Esperando al banco"
for _ in $(seq 1 60); do
  if curl -skf -o /dev/null "https://localhost/$PREFIJO/salud"; then
    echo " listo."
    URL_PUBLICA="https://$DOMINIO/$PREFIJO"

    echo "▸ Registro en el tl-publisher (lista QEAA)"
    clave="${CLAVE_OPERADOR:-}"
    if [[ -z "$clave" && -f ../../srv-tl-publisher/docker-compose/.env ]]; then
      clave="$(grep -E '^TL_OPERATOR_KEY=' ../../srv-tl-publisher/docker-compose/.env | cut -d= -f2- || true)"
    fi
    if [[ -f secretos/actor ]]; then
      echo "  ya registrado (actor #$(cat secretos/actor))"
    elif [[ -n "$clave" ]]; then
      docker run --rm --user "$(id -u):$(id -g)" \
        -e SECRETOS_DIR=/secretos -e TL_PUBLISHER_URL -e CLAVE_OPERADOR="$clave" -e NOMBRE_BANCO -e PAIS \
        -e URL_PUBLICA="$URL_PUBLICA" \
        -v "$PWD/scripts:/scripts:ro" -v "$PWD/secretos:/secretos" \
        python:3.13-alpine python3 /scripts/registrar.py
    else
      echo "  Falta la clave de operador: pide el alta en $TL_PUBLISHER_URL/admin (rol qeaa, certificado secretos/raiz.crt, URL $URL_PUBLICA)"
      echo "  y guarda el número de actor en secretos/actor. Mientras tanto la wallet rechazará la credencial de cuenta."
    fi

    echo "▸ Ancla del banco en el trust-validator"
    AVANCE="$(cd ../.. && pwd)"
    huella="$(openssl x509 -in secretos/raiz.crt -noout -fingerprint -sha256 | cut -d= -f2 | tr -d : | cut -c1-8 | tr A-F a-f)"
    ancla="$AVANCE/srv-tl-publisher/pki/publico/ca/qeaa-banco-root-ca-$huella.crt"
    if [[ -f "$ancla" ]]; then
      echo "  ya está"
    else
      cp secretos/raiz.crt "$ancla"
      "$AVANCE/eudi-srv-trust-validator/scripts/construir-jks-anclas.sh" >/dev/null
      docker restart trust-validator >/dev/null && echo "  añadida; trust-validator reiniciado"
    fi
    cat <<MSG

  Banco                https://$DOMINIO/$PREFIJO/
  Su verifier          https://$DOMINIO/verifier-$VERIFIER_INSTANCIA/
  Emisor (OpenID4VCI)  https://$DOMINIO/.well-known/openid-credential-issuer/$PREFIJO   (credencial de cuenta bancaria)
  CA raíz del emisor   secretos/raiz.crt  (lista QEAA del tl-publisher)
  Clientes             datos/clientes.json   (datos personales de la prueba: bórralo para empezar de cero)

  Logs:                docker compose -p banco logs -f
MSG
    exit 0
  fi
  echo -n "."
  sleep 2
done
echo " no respondió en 2 minutos. Revisa: docker compose -p banco logs"
exit 1
