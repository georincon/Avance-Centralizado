#!/usr/bin/env bash
#
# Genera una CA raiz propia + certificado de firma para el servicio status-list
# compartido (usado tanto por uis-issuer como por Spartans para firmar los
# tokens de estado/revocacion). Reemplaza el certificado autofirmado de
# desarrollo "CN=localhost" que trae por defecto el proyecto de referencia
# eudi-srv-statuslist-py, el cual nunca fue pensado para pasar por un
# validador de confianza externo.
#
# El certificado de firma (hoja) queda en docker-compose/status-list/certs/
# (cert.der + cert.crt + key.pem), que es lo que status_list_format.py lee en
# tiempo de ejecucion. La CA raiz (raiz.crt) es la que hay que entregar a
# cualquier validador de confianza externo (p. ej. el tl-publisher de otro
# equipo) para que reconozca como confiable al emisor de nuestros status list
# tokens ("ClientStatusIssuerNotTrusted" si no esta registrada).
#
# Revertir: restaurar con git los archivos en docker-compose/status-list/certs/
# y reiniciar el contenedor status-list.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SECRETS_DIR="$REPO_ROOT/docker-compose/status-list/secretos/pki"
CERTS_DIR="$REPO_ROOT/docker-compose/status-list/certs"

SUBJECT_ROOT="/C=CO/O=Avance/CN=Avance Status List Root CA"
SUBJECT_DS="/C=CO/O=Avance/CN=Avance Status List Service"

ROOT_DAYS=3650
DS_DAYS=825

mkdir -p "$SECRETS_DIR"
cd "$SECRETS_DIR"

echo "==> Generando CA raiz del status-list (EC P-256, $ROOT_DAYS dias)"
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out raiz.key
openssl req -new -x509 -key raiz.key -sha256 -days "$ROOT_DAYS" -out raiz.crt \
  -subj "$SUBJECT_ROOT" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "keyUsage=critical,keyCertSign,cRLSign"

echo "==> Generando certificado de firma del status-list, firmado por la raiz (EC P-256, $DS_DAYS dias)"
openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out ds.key
openssl req -new -key ds.key -out ds.csr -subj "$SUBJECT_DS"
openssl x509 -req -in ds.csr -CA raiz.crt -CAkey raiz.key -CAcreateserial \
  -days "$DS_DAYS" -sha256 -out ds.crt \
  -extfile <(printf "basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature")
rm -f ds.csr raiz.srl

echo "==> Instalando el certificado de firma en $CERTS_DIR"
mkdir -p "$CERTS_DIR"
cp ds.key "$CERTS_DIR/key.pem"
cp ds.crt "$CERTS_DIR/cert.crt"
openssl x509 -in ds.crt -outform der -out "$CERTS_DIR/cert.der"

echo
echo "Listo. Reinicia el contenedor status-list para que tome el nuevo certificado:"
echo "  cd $REPO_ROOT/docker-compose && docker compose restart status-list"
echo
echo "raiz.crt (lo unico que se entrega a una lista de confianza externa) esta en:"
echo "  $SECRETS_DIR/raiz.crt"
echo
openssl x509 -in raiz.crt -noout -fingerprint -sha256
