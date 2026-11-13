#!/usr/bin/env bash
#
# Genera una CA raiz propia para uis-issuer (Universidad Industrial de Santander) y un
# Document Signer (DS) firmado por ella, y reemplaza con eso el alias "issuance" del
# keystore que usa la app para firmar TODAS sus credenciales (academic, pid, mdl,
# learningCredential comparten el mismo alias "issuance" via application*.properties).
#
# La cadena generada es de 2 niveles: raiz.crt (CA) -> ds.crt (DS, CA:FALSE), firmado
# directamente por la raiz, tal como se describe para emisores propios fuera de este repo.
#
# Solo raiz.crt (parte publica) se sube a la lista de confianza. raiz.key nunca sale de
# docker-compose/uis-issuer/secretos/pki/ (gitignored).
#
# Revertir: el keystore anterior (CA demo del proyecto EUDI de referencia) sigue
# commiteado en git, asi que para volver atras basta con:
#   git checkout -- src/main/resources/issuer-keys.jks docker-compose/uis-issuer/issuer-keys.jks
# (o, si este cambio ya se commiteo, "git revert <commit>"). Este script tambien deja una
# copia de respaldo en docker-compose/uis-issuer/issuer-keys.jks.demo-backup por si acaso.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS_DIR="$REPO_ROOT/docker-compose/uis-issuer/secretos/pki"
KEYSTORE_APP="$REPO_ROOT/src/main/resources/issuer-keys.jks"
KEYSTORE_DOCKER="$REPO_ROOT/docker-compose/uis-issuer/issuer-keys.jks"

STOREPASS="keys"
KEYPASS="issuance"

SUBJECT_ROOT="/C=CO/O=Universidad Industrial de Santander/CN=UIS Issuer Root CA"
SUBJECT_DS="/C=CO/O=Universidad Industrial de Santander/CN=UIS Issuer Document Signer"

ROOT_DAYS=3650
DS_DAYS=365

mkdir -p "$SECRETS_DIR"
cd "$SECRETS_DIR"

echo "==> Generando CA raiz de uis-issuer (EC P-256, $ROOT_DAYS dias)"
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out raiz.key
openssl req -new -x509 -key raiz.key -sha256 -days "$ROOT_DAYS" -out raiz.crt \
  -subj "$SUBJECT_ROOT" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"

echo "==> Generando Document Signer firmado por la raiz (EC P-256, $DS_DAYS dias)"
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out ds.key
openssl req -new -key ds.key -out ds.csr -subj "$SUBJECT_DS"
openssl x509 -req -in ds.csr -CA raiz.crt -CAkey raiz.key -CAcreateserial \
  -days "$DS_DAYS" -sha256 -out ds.crt \
  -extfile <(printf "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature")
rm -f ds.csr raiz.srl

echo "==> Empaquetando DS + cadena en PKCS12 (alias issuance)"
openssl pkcs12 -export \
  -inkey ds.key -in ds.crt -certfile raiz.crt \
  -name issuance -out issuance.p12 -passout "pass:${KEYPASS}"

echo "==> Verificando que raiz.crt califica como CA"
openssl x509 -in raiz.crt -noout -ext basicConstraints

for KEYSTORE in "$KEYSTORE_APP" "$KEYSTORE_DOCKER"; do
  echo "==> Actualizando $KEYSTORE"
  cp "$KEYSTORE" "${KEYSTORE}.demo-backup"

  for ALIAS in "issuance" "issuance intermediate" "issuance root"; do
    keytool -delete -alias "$ALIAS" -keystore "$KEYSTORE" -storepass "$STOREPASS" 2>/dev/null || true
  done

  keytool -importkeystore \
    -srckeystore issuance.p12 -srcstoretype PKCS12 -srcstorepass "$KEYPASS" -srcalias issuance \
    -destkeystore "$KEYSTORE" -deststoretype JKS -deststorepass "$STOREPASS" \
    -destalias issuance -destkeypass "$KEYPASS"
done

echo
echo "Listo. raiz.crt (lo unico que se sube a la lista de confianza) esta en:"
echo "  $SECRETS_DIR/raiz.crt"
echo
echo "Para revertir al keystore demo original:"
echo "  git checkout -- src/main/resources/issuer-keys.jks docker-compose/uis-issuer/issuer-keys.jks"
