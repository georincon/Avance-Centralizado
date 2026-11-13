#!/usr/bin/env bash
# PKI propia de la notaría. Corre dentro de eclipse-temurin:21-jre (openssl), lo
# lanza levantar.sh. Idempotente: solo crea lo que falta.
#
#   SECRETOS_DIR/raiz.crt   CA raíz de la notaría: es lo que se registra en el
#                           tl-publisher (lista PUBEAA). La wallet y los
#                           verifiers confían en lo que cuelgue de ella.
#   SECRETOS_DIR/raiz.key   su clave. Solo firma el certificado de abajo.
#   SECRETOS_DIR/ds.crt     certificado de firma (Document Signer): firma los
#   SECRETOS_DIR/ds.key     documentos notariales y la status list. Va en el x5c.
#
# Variables: SECRETOS_DIR, NOTARIA_NOMBRE, PAIS.

set -euo pipefail
DIR="${SECRETOS_DIR:?}"
cd "$DIR"

clave_ec() { openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$1"; }

if [[ ! -f raiz.crt ]]; then
  echo "· CA raíz de la notaría"
  clave_ec raiz.key
  openssl req -new -x509 -key raiz.key -days 3650 -sha256 -utf8 \
    -subj "/C=$PAIS/O=$NOTARIA_NOMBRE/CN=$NOTARIA_NOMBRE Root CA" \
    -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -addext "subjectKeyIdentifier=hash" -out raiz.crt
  rm -f ds.crt
fi

if [[ ! -f ds.crt ]]; then
  echo "· certificado de firma (DS)"
  clave_ec ds.key
  csr="$(mktemp)"
  openssl req -new -key ds.key -utf8 -subj "/C=$PAIS/O=$NOTARIA_NOMBRE/CN=$NOTARIA_NOMBRE Document Signer" -out "$csr"
  openssl x509 -req -in "$csr" -CA raiz.crt -CAkey raiz.key -CAcreateserial -days 825 -sha256 \
    -extfile <(printf '%b' "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature\nsubjectKeyIdentifier=hash\nauthorityKeyIdentifier=keyid\n") \
    -out ds.crt
  rm -f "$csr"
fi

# El contenedor corre con el usuario de quien levantó la notaría; las claves no
# tienen por qué ser legibles por nadie más.
chmod 644 raiz.crt ds.crt
chmod 600 raiz.key ds.key
echo "PKI de la notaría lista en $DIR/"
