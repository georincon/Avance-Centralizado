#!/usr/bin/env bash
# Material criptográfico de una instancia del verifier. Corre dentro de
# eclipse-temurin:21-jre (openssl), lo lanza levantar.sh. Idempotente: solo hace
# lo que falta.
#
#   SECRETOS_DIR/acceso.key     clave EC P-256 del verifier. Nunca sale de aquí.
#   SECRETOS_DIR/acceso.csr     petición de certificado (lo que se manda al tl-publisher)
#   SECRETOS_DIR/cadena.pem     certificado de acceso + CA de acceso (lo devuelve el tl-publisher)
#   SECRETOS_DIR/verifier.p12   clave + certificado de acceso, alias "verifier": firma las solicitudes (JAR)
#   SECRETOS_DIR/wrprc.jws      certificado de registro (intended use)
#   SECRETOS_DIR/generado.env   client_id y certificado de registro para el compose
#   SECRETOS_DIR/estado         ok | falta | renovar: <motivo>
#
# Si falta cadena.pem, o la que hay ya no sirve, escribe el estado y para:
# levantar.sh pide el certificado (o explica cómo) y vuelve a llamarlo.

set -euo pipefail
DIR="${SECRETOS_DIR:?}"
cd "$DIR"

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
estado() { echo "$1" > estado; echo "· estado: $1"; exit 0; }

# --- 1. Clave y CSR -------------------------------------------------------------
if [[ ! -f acceso.key ]]; then
  echo "· clave de acceso (EC P-256)"
  openssl ecparam -name prime256v1 -genkey -noout -out acceso.tmp
  openssl pkcs8 -topk8 -nocrypt -in acceso.tmp -out acceso.key
  rm -f acceso.tmp acceso.csr
fi
if [[ ! -f acceso.csr ]]; then
  echo "· CSR"
  # El SAN no va aquí: lo pone la PKI con el dominio que se declara al registrarse.
  openssl req -new -key acceso.key -out acceso.csr -utf8 \
    -subj "/C=$VERIFIER_PAIS/O=$VERIFIER_NOMBRE/CN=$VERIFIER_NOMBRE"
fi

# --- 2. ¿Hay un certificado de acceso que sirva? -----------------------------------
[[ -s cadena.pem ]] || estado falta

awk '/BEGIN CERT/{n++} n==1' cadena.pem > hoja.pem
awk '/BEGIN CERT/{n++} n>=2' cadena.pem > ca.pem
[[ -s ca.pem ]] || estado "renovar: cadena.pem trae solo un certificado; hace falta el de acceso y su CA"
[[ "$(openssl pkey -in acceso.key -pubout)" == "$(openssl x509 -in hoja.pem -noout -pubkey)" ]] \
  || estado "renovar: el certificado no es de esta clave (¿se regeneró acceso.key?)"
# ISO 18013-5 mdocReaderAuth: el SDK de la wallet rechaza el certificado sin él.
openssl x509 -in hoja.pem -noout -ext extendedKeyUsage 2>/dev/null | grep -q '1\.0\.18013\.5\.1\.6' \
  || estado "renovar: el certificado no tiene el EKU 1.0.18013.5.1.6 (emitido antes de que la PKI lo pusiera)"
openssl x509 -in hoja.pem -noout -checkend $((30 * 86400)) >/dev/null \
  || estado "renovar: vence en menos de 30 días"

# --- 3. Keystore -----------------------------------------------------------------
# Solo el certificado de acceso, SIN la CA: el verifier manda en el x5c lo que
# hay en el keystore, y la wallet valida con PKIX (Java) usando la CA de acceso
# como ancla. Si el x5c trae también el ancla, PKIX intenta subir desde ella
# hasta la raíz, que no es ancla, y rechaza: "Path does not chain with any of
# the trust anchors" (InvalidJarJwt: Untrusted x5c).
certs_en_p12() { openssl pkcs12 -in verifier.p12 -nokeys -passin "pass:$KEYSTORE_PASSWORD" 2>/dev/null | grep -c 'BEGIN CERT'; }
if [[ ! -f verifier.p12 || cadena.pem -nt verifier.p12 || "$(certs_en_p12)" != 1 ]]; then
  echo "· keystore verifier.p12"
  openssl pkcs12 -export -inkey acceso.key -in hoja.pem -name verifier \
    -passout "pass:$KEYSTORE_PASSWORD" -out verifier.p12
fi

# --- 4. client_id ------------------------------------------------------------------
case "$ESQUEMA_CLIENT_ID" in
  x509_hash)
    # SHA-256 del DER del certificado de acceso, en base64url sin relleno.
    client_id="$(openssl x509 -in hoja.pem -outform DER | openssl dgst -sha256 -binary | b64url)" ;;
  x509_san_dns)
    client_id="$(openssl x509 -in hoja.pem -noout -ext subjectAltName | grep -o 'DNS:[^,]*' | head -1 | cut -d: -f2)"
    [[ -n "$client_id" ]] || estado "renovar: el certificado no tiene SAN DNS" ;;
  *) echo "ESQUEMA_CLIENT_ID debe ser x509_hash o x509_san_dns" >&2; exit 1 ;;
esac

# --- 5. Certificado de registro (WRPRC) ------------------------------------------------
# JWS compacto ES256, typ=rc-wrp+jwt, con x5c. El verifier oficial exige uno por
# cada intended use y solo comprueba que lo firme la hoja de su x5c; en
# producción lo emite el registro nacional. Aquí lo firma el propio certificado
# de acceso (ETSI TS 119 475).
if [[ ! -f wrprc.jws || verifier.p12 -nt wrprc.jws ]]; then
  echo "· certificado de registro (WRPRC)"
  x5c_hoja="$(openssl x509 -in hoja.pem -outform DER | openssl base64 -A)"
  # Como en el JAR: solo la hoja, sin el ancla.
  cabecera="{\"typ\":\"rc-wrp+jwt\",\"alg\":\"ES256\",\"x5c\":[\"$x5c_hoja\"]}"
  json() { printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'; }
  # Qué credenciales declara el verifier en su certificado de registro (intended use). Por defecto el PID;
  # con WRPRC_VCTS="urn:a,urn:b" declara SD-JWT VC de esos tipos (p. ej. la credencial académica).
  if [[ -n "${WRPRC_VCTS:-}" ]]; then
    vcts=""
    for v in ${WRPRC_VCTS//,/ }; do vcts+="${vcts:+,}\"$v\""; done
    credenciales="[{\"format\":\"dc+sd-jwt\",\"meta\":{\"vct_values\":[$vcts]}}]"
  else
    credenciales='[{"format":"dc+sd-jwt","meta":{"vct_values":["urn:eudi:pid:1"]}},{"format":"mso_mdoc","meta":{"doctype_value":"eu.europa.ec.eudi.pid.1"}}]'
  fi
  carga="{\"name\":\"$(json "$VERIFIER_NOMBRE")\",\"sub\":\"$ESQUEMA_CLIENT_ID:$client_id\",\
\"country\":\"$VERIFIER_PAIS\",\"iat\":$(date +%s),\
\"entitlements\":[\"https://uri.etsi.org/19475/Entitlement/Service_Provider\"],\
\"policy_id\":[\"0.4.0.19475.3.1\"],\
\"purpose\":[{\"lang\":\"es\",\"value\":\"$(json "$VERIFIER_PROPOSITO")\"}],\
\"info_uri\":\"https://$DOMINIO/$PREFIJO/\",\
\"credentials\":$credenciales}"
  entrada="$(printf '%s' "$cabecera" | b64url).$(printf '%s' "$carga" | b64url)"

  # openssl firma ECDSA en DER (SEQUENCE{r,s}); JWS exige r||s de 32 bytes cada uno.
  firma_der="$(mktemp)"
  printf '%s' "$entrada" | openssl dgst -sha256 -sign acceso.key -out "$firma_der"
  rs=""
  for entero in $(openssl asn1parse -inform DER -in "$firma_der" | awk -F: '/INTEGER/{print $NF}'); do
    rs+="$(printf '%064s' "$entero" | tr ' ' 0 | tail -c 64)"
  done
  rm -f "$firma_der"
  firma="$(printf "$(printf '%s' "$rs" | sed 's/../\\x&/g')" | b64url)"
  printf '%s.%s' "$entrada" "$firma" > wrprc.jws
fi

# --- 6. Variables para el compose ------------------------------------------------------
cat > generado.env <<EOF
# Generado por scripts/generar-claves.sh: no editar.
VERIFIER_CLIENTIDPREFIX=$ESQUEMA_CLIENT_ID
VERIFIER_ORIGINALCLIENTID=$client_id
VERIFIER_INTENDEDUSES_0_REGISTRATIONCERTIFICATE=$(cat wrprc.jws)
EOF

rm -f hoja.pem ca.pem
# El verifier corre con otro usuario: que pueda leer el keystore. La clave suelta no.
chmod -R a+rX "$DIR"
chmod 600 acceso.key
estado ok
