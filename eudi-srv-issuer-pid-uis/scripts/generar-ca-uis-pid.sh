#!/usr/bin/env bash
#
# Genera una SEGUNDA CA raiz propia para uis-issuer, separada de la que ya usan
# academic/mdl/learningCredential (alias "issuance"), destinada solo al rol PID
# en la lista de confianza. Crea un alias nuevo "pid-issuance" en el keystore con
# su propio root+DS, sin tocar "issuance".
#
# Requiere que luego se apunten las propiedades de firma de PID
# (issuer.pid.mso_mdoc.signing-key.* / issuer.pid.sd_jwt_vc.signing-key.*) al
# nuevo alias "pid-issuance" (ver docker-compose/docker-compose.yaml).
#
# Revertir: restaurar docker-compose.yaml y los .jks con git, por ejemplo:
#   git checkout -- docker-compose/docker-compose.yaml \
#     src/main/resources/issuer-keys.jks docker-compose/uis-issuer/issuer-keys.jks

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS_DIR="$REPO_ROOT/docker-compose/uis-issuer/secretos/pki-pid"
KEYSTORE_APP="$REPO_ROOT/src/main/resources/issuer-keys.jks"
KEYSTORE_DOCKER="$REPO_ROOT/docker-compose/uis-issuer/issuer-keys.jks"

STOREPASS="keys"
ALIAS="pid-issuance"
KEYPASS="pid-issuance"

SUBJECT_ROOT="/C=CO/O=Universidad Industrial de Santander/CN=UIS Issuer PID Root CA"
SUBJECT_DS="/C=CO/O=Universidad Industrial de Santander/CN=UIS Issuer PID Document Signer"

ROOT_DAYS=3650
DS_DAYS=365

mkdir -p "$SECRETS_DIR"
cd "$SECRETS_DIR"

echo "==> Generando CA raiz PID de uis-issuer (EC P-256, $ROOT_DAYS dias)"
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out raiz.key
openssl req -new -x509 -key raiz.key -sha256 -days "$ROOT_DAYS" -out raiz.crt \
  -subj "$SUBJECT_ROOT" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"

echo "==> Generando Document Signer PID firmado por la raiz (EC P-256, $DS_DAYS dias)"
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out ds.key
openssl req -new -key ds.key -out ds.csr -subj "$SUBJECT_DS"
openssl x509 -req -in ds.csr -CA raiz.crt -CAkey raiz.key -CAcreateserial \
  -days "$DS_DAYS" -sha256 -out ds.crt \
  -extfile <(printf "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature")
rm -f ds.csr raiz.srl

echo "==> Empaquetando DS + cadena en PKCS12 (alias $ALIAS)"
openssl pkcs12 -export \
  -inkey ds.key -in ds.crt -certfile raiz.crt \
  -name "$ALIAS" -out "${ALIAS}.p12" -passout "pass:${KEYPASS}"

echo "==> Verificando que raiz.crt califica como CA"
openssl x509 -in raiz.crt -noout -ext basicConstraints

for KEYSTORE in "$KEYSTORE_APP" "$KEYSTORE_DOCKER"; do
  echo "==> Actualizando $KEYSTORE"
  cp "$KEYSTORE" "${KEYSTORE}.pre-pid-backup"

  keytool -delete -alias "$ALIAS" -keystore "$KEYSTORE" -storepass "$STOREPASS" 2>/dev/null || true

  keytool -importkeystore \
    -srckeystore "${ALIAS}.p12" -srcstoretype PKCS12 -srcstorepass "$KEYPASS" -srcalias "$ALIAS" \
    -destkeystore "$KEYSTORE" -deststoretype JKS -deststorepass "$STOREPASS" \
    -destalias "$ALIAS" -destkeypass "$KEYPASS"
done

echo
echo "Listo. raiz.crt (lo unico que se sube a la lista de confianza, rol PID) esta en:"
echo "  $SECRETS_DIR/raiz.crt"
echo
echo "Falta: apuntar ISSUER_PID_MSO_MDOC_SIGNING_KEY_ALIAS / ISSUER_PID_SD_JWT_VC_SIGNING_KEY_ALIAS"
echo "a '$ALIAS' (password '$KEYPASS') en docker-compose/docker-compose.yaml y reiniciar uis-issuer."
