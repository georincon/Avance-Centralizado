#!/usr/bin/env bash
#
# Genera la PKI local del proxy nw-nginx: una CA de desarrollo y el certificado del servidor para
# civica-desarrollo.avance.org.co (con SAN para localhost y el dominio).
#
# El cliente (teléfono) NO ve este certificado: el proxy publico (190.29.100.206) termina TLS con
# el certificado valido *.avance.org.co y reenvia al puerto 443 de este equipo. Este certificado
# solo cubre el salto borde -> maquina, donde el borde no verifica la cadena.
#
# Uso: ./scripts/generar-certificados.sh [--force]

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CERTS_DIR="$SCRIPT_DIR/../certs"
DOMAIN="${NW_DOMAIN:-civica-desarrollo.avance.org.co}"
LIVE_DIR="$CERTS_DIR/live/$DOMAIN"
CA_DIR="$CERTS_DIR/ca"

STOREPASS="${NW_CA_PASSWORD:-changeit}"
KEYPASS="${NW_CERT_PASSWORD:-changeit}"

if [[ -f "$LIVE_DIR/privkey.pem" && "${1:-}" != "--force" ]]; then
  echo "Ya existe el certificado de $DOMAIN en $LIVE_DIR (usa --force para regenerar)"
  exit 0
fi

mkdir -p "$CA_DIR" "$LIVE_DIR"
cd "$CA_DIR"

if [[ ! -f ca.key ]]; then
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out ca.key 2>/dev/null
  openssl req -new -x509 -key ca.key -sha256 -days 3650 -out ca.crt \
    -subj "/C=CO/O=Avance/CN=Avance Dev Proxy CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign"
  echo "CA de desarrollo creada: $CA_DIR/ca.crt"
fi

openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$LIVE_DIR/privkey.pem" 2>/dev/null
openssl req -new -key "$LIVE_DIR/privkey.pem" -out "$LIVE_DIR/server.csr" \
  -subj "/C=CO/O=Avance/CN=$DOMAIN" 2>/dev/null

cat > "$LIVE_DIR/extensions.cnf" <<EOF
basicConstraints=CA:FALSE
keyUsage=critical,digitalSignature
extendedKeyUsage=serverAuth
subjectAltName=DNS:$DOMAIN,DNS:localhost,IP:127.0.0.1
EOF

openssl x509 -req -in "$LIVE_DIR/server.csr" -CA ca.crt -CAkey ca.key -CAcreateserial \
  -days 825 -sha256 -out "$LIVE_DIR/fullchain.pem" \
  -extfile "$LIVE_DIR/extensions.cnf" 2>/dev/null

rm -f "$LIVE_DIR/server.csr" "$LIVE_DIR/extensions.cnf"

echo "Certificado listo: $LIVE_DIR/fullchain.pem"
echo "CA (para confianza del cliente): $CA_DIR/ca.crt"
