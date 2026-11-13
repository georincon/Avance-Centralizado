#!/usr/bin/env bash
# Levanta el gimnasio de ejemplo con su emisor (tarjeta de socio), su portal, el control de acceso y su verifier. Solo requiere Docker
# (y la capa de confianza y el nginx central levantados).
#
#   ./levantar.sh        → https://DOMINIO/gym/
#
# 1. Crea .env desde .env.example si no existe (con la contraseña de administración).
# 2. Genera su PKI (CA raíz + certificado de firma) en secretos/.
# 3. Levanta el gimnasio en la red "avance-eudi" y espera a que responda.
# 4. La registra en el tl-publisher como emisor de la lista QEAA, con su URL
#    OpenID4VCI (solo la primera vez). Necesita la clave de operador; si no la
#    encuentra, explica cómo pedir el alta.
# 5. Levanta (o actualiza) su verifier: ../_comun/levantar.sh gym. Va
#    después del registro para que el verifier ya sepa que los documentos de la
#    gimnasio son de la clase QEAA.
#
# Los socios, sus membresías y el registro de accesos quedan en datos/socios.json.

set -euo pipefail
cd "$(dirname "$0")"

aleatorio() { head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9'; }
if [[ ! -f .env ]]; then
  echo "▸ Creando .env"
  while IFS= read -r linea; do
    [[ "$linea" == *=generar ]] && echo "${linea%=generar}=$(aleatorio)" || echo "$linea"
  done < .env.example > .env
  chmod 600 .env
else
  # Variables nuevas de .env.example que le falten al .env (p. ej. ADMIN_CLAVE).
  while IFS= read -r linea; do
    [[ "$linea" =~ ^([A-Z_][A-Z0-9_]*)= ]] || continue
    grep -q "^${BASH_REMATCH[1]}=" .env && continue
    echo "▸ Agregando ${BASH_REMATCH[1]} a .env"
    [[ "$linea" == *=generar ]] && echo "${linea%=generar}=$(aleatorio)" >> .env || echo "$linea" >> .env
  done < .env.example
fi
set -a
# shellcheck disable=SC1091
source .env
set +a
URL_PUBLICA="https://$DOMINIO/$PREFIJO"

docker network inspect avance-eudi >/dev/null 2>&1 || { echo "Falta la red avance-eudi (¿está arriba srv-nw-nginx?)"; exit 1; }
mkdir -p secretos datos

echo "▸ PKI del gimnasio (secretos/)"
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e SECRETOS_DIR=/secretos \
  -e GYM_NOMBRE -e PAIS \
  -v "$PWD/scripts:/scripts:ro" -v "$PWD/secretos:/secretos" \
  eclipse-temurin:21-jre bash /scripts/generar-claves.sh

echo "▸ Levantando el gimnasio"
export UID_GID="$(id -u):$(id -g)"
docker compose -p gym --env-file .env up -d --build

echo -n "▸ Esperando al gimnasio"
for i in $(seq 1 60); do
  curl -skf -o /dev/null "https://localhost/$PREFIJO/salud" && { echo " lista."; break; }
  [[ $i == 60 ]] && { echo " no respondió en 2 minutos. Revisa: docker compose -p gym logs"; exit 1; }
  echo -n "."; sleep 2
done

echo "▸ Registro en el tl-publisher (lista QEAA)"
clave="${CLAVE_OPERADOR:-}"
if [[ -z "$clave" && -f ../../srv-tl-publisher/docker-compose/.env ]]; then
  clave="$(grep -E '^TL_OPERATOR_KEY=' ../../srv-tl-publisher/docker-compose/.env | cut -d= -f2- || true)"
fi
if [[ -f secretos/actor ]]; then
  echo "  ya registrada (actor #$(cat secretos/actor))"
elif [[ -n "$clave" ]]; then
  docker run --rm --user "$(id -u):$(id -g)" \
    -e SECRETOS_DIR=/secretos -e TL_PUBLISHER_URL -e CLAVE_OPERADOR="$clave" -e GYM_NOMBRE -e PAIS \
    -e URL_PUBLICA="$URL_PUBLICA" \
    -v "$PWD/scripts:/scripts:ro" -v "$PWD/secretos:/secretos" \
    python:3.13-alpine python3 /scripts/registrar.py
else
  cat <<EOF
  Falta la clave de operador. Pide el alta al operador del tl-publisher:
    $TL_PUBLISHER_URL/admin → Actores → Alta
      Rol:          qeaa
      Nombre:       $GYM_NOMBRE
      Certificado:  secretos/raiz.crt
      URL:          $URL_PUBLICA
  y guarda el número de actor en secretos/actor. Mientras tanto el gimnasio
  funciona, pero la wallet rechazará sus tarjetas (emisor fuera de la lista).
EOF
fi

echo "▸ Ancla del gimnasio en el trust-validator"
# El validador no saca las anclas de las listas en vivo (la librería DSS alpha no extrae ninguna): usa un JKS que
# se construye con las CA de srv-tl-publisher/pki/publico/ca. La CA del gimnasio es propia, así que se copia ahí.
AVANCE="$(cd ../.. && pwd)"
huella="$(openssl x509 -in secretos/raiz.crt -noout -fingerprint -sha256 | cut -d= -f2 | tr -d : | cut -c1-8 | tr A-F a-f)"
ancla="$AVANCE/srv-tl-publisher/pki/publico/ca/qeaa-cumbre-gym-root-ca-$huella.crt"
if [[ -f "$ancla" ]]; then
  echo "  ya está"
else
  cp secretos/raiz.crt "$ancla"
  "$AVANCE/eudi-srv-trust-validator/scripts/construir-jks-anclas.sh" >/dev/null
  docker restart trust-validator >/dev/null && echo "  añadida; trust-validator reiniciado"
fi

echo "▸ Verifier del gimnasio (verifier-$VERIFIER_INSTANCIA)"
VERIFIER_NOMBRE="$GYM_NOMBRE" VERIFIER_PROPOSITO="$PROPOSITO" WRPRC_VCTS="urn:eudi:pid:1,urn:co:gym:tarjeta-socio:1" CONSERVAR_PRESENTACIONES=PT30M VIGENCIA_SOLICITUD=PT5M ../_comun/levantar.sh "$VERIFIER_INSTANCIA" \
  | sed 's/^/  │ /'

cat <<EOF

  Gimnasio             $URL_PUBLICA/
  Control de acceso    $URL_PUBLICA/acceso   (pantallas de entrada y de salida por sede)
  Administración       $URL_PUBLICA/admin    (aforo, socios y accesos; contraseña: ADMIN_CLAVE en .env)
  Emisor (OpenID4VCI)  https://$DOMINIO/.well-known/openid-credential-issuer/$PREFIJO
  CA raíz              secretos/raiz.crt  (lista QEAA del tl-publisher)
  Su verifier          https://$DOMINIO/verifier-$VERIFIER_INSTANCIA/
  Socios y accesos     datos/socios.json  (los socios se afilian en el portal con su PID)

  Tras el primer registro:
    - el trust-validator tarda hasta 20 minutos en ver la lista nueva (verificar
      tarjetas falla mientras tanto);
    - en la wallet: Ajustes › Listas de confianza › Actualizar, o rechazará las
      tarjetas del gimnasio hasta su actualización diaria.

  Logs:                docker compose -p gym logs -f
EOF
