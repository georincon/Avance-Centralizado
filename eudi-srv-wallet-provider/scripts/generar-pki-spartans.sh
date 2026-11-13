#!/usr/bin/env bash
#
# Genera la CA raiz de Spartans Wallet Provider y el certificado de firma (EC P-256) con el que
# se firman las Wallet Instance Attestations y Key Attestations. Crea el keystore JKS que
# consume el contenedor (docker/spartans/keystore.jks, alias "spartans-wallet-provider").
#
# Ademas genera las claves del servicio de listas de estado del provider
# (docker/status-list/certs/{key.pem,cert.crt,cert.der}), que firma los Token Status List.
#
# Solo raiz.crt (parte publica) se sube a la lista de confianza. Las llaves privadas viven en
# docker/secretos/pki/ (gitignored).

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS_DIR="$REPO_ROOT/docker/secretos/pki"
KEYSTORE="$REPO_ROOT/docker/spartans/keystore.jks"
STOREPASS="${SPARTANS_KEYSTORE_PASSWORD:-spartans-keys}"
KEYPASS="${SPARTANS_KEY_PASSWORD:-spartans-signing}"
ALIAS="spartans-wallet-provider"

SUBJECT_ROOT="/C=CO/O=Avance/CN=Spartans Wallet Provider Root CA"
SUBJECT_SIGN="/C=CO/O=Avance/CN=Spartans Wallet Provider"

mkdir -p "$SECRETS_DIR" "$(dirname "$KEYSTORE")"
cd "$SECRETS_DIR"

if [[ -f raiz.key && "${1:-}" != "--force" ]]; then
  echo "Ya existe PKI en $SECRETS_DIR (usa --force para regenerar)"; exit 1
fi

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out raiz.key
openssl req -new -x509 -key raiz.key -sha256 -days 3650 -out raiz.crt -subj "$SUBJECT_ROOT" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out firma.key
openssl req -new -key firma.key -out firma.csr -subj "$SUBJECT_SIGN"
openssl x509 -req -in firma.csr -CA raiz.crt -CAkey raiz.key -CAcreateserial -days 730 -sha256 \
  -out firma.crt \
  -extfile <(printf "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature")
rm -f firma.csr raiz.srl

openssl pkcs12 -export -inkey firma.key -in firma.crt -certfile raiz.crt \
  -name "$ALIAS" -out firma.p12 -passout "pass:${KEYPASS}"

rm -f "$KEYSTORE"
keytool -importkeystore -srckeystore firma.p12 -srcstoretype PKCS12 -srcstorepass "$KEYPASS" \
  -srcalias "$ALIAS" -destkeystore "$KEYSTORE" -deststoretype JKS -deststorepass "$STOREPASS" \
  -destalias "$ALIAS" -destkeypass "$KEYPASS"

# --- Claves de la lista de estado del provider (firman los Token Status List)
STATUS_CERTS="$REPO_ROOT/docker/status-list/certs"
mkdir -p "$STATUS_CERTS"
if [[ ! -f "$STATUS_CERTS/key.pem" || "${1:-}" == "--force" ]]; then
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$STATUS_CERTS/key.pem" 2>/dev/null
  openssl req -new -x509 -key "$STATUS_CERTS/key.pem" -sha256 -days 730 \
    -subj "/C=CO/O=Avance/CN=Spartans Status List" -out "$STATUS_CERTS/cert.crt"
  openssl x509 -in "$STATUS_CERTS/cert.crt" -outform der -out "$STATUS_CERTS/cert.der"
fi

echo "Listo. Keystore: $KEYSTORE"
echo "Raiz publica (para la lista de confianza): $SECRETS_DIR/raiz.crt"
echo "Certificado de la lista de estado: $STATUS_CERTS/cert.crt"
