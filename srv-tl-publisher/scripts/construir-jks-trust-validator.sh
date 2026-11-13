#!/usr/bin/env bash
#
# Construye el Java KeyStore con el que trust-validator verifica la firma
# XAdES de nuestras Trusted Lists (Fase 7.3 del plan).
#
# POR QUÉ EXISTE
#
# `eudi-srv-trust-validator` (DSS) no lee certificados PEM sueltos para
# validar la firma de una lista: necesita un JKS con el/los ancla(s) de
# confianza. No hay nada que "emitir" aquí —son sólo los certificados
# PÚBLICOS de nuestra propia PKI, ya versionados en pki/publico/ca/—, así
# que esto es una conversión de formato, no una ceremonia de claves. Por
# eso no vive en emitir-claves-firma.sh: ese script emite hojas nuevas;
# éste sólo empaqueta material público que ya existe.
#
# QUÉ LLEVA DENTRO, Y POR QUÉ LOS DOS
#
# La firma XAdES de cada lista la hace la hoja `tl-signer`, emitida por la
# `Civica TL Signing CA`. Se importan como ancla de confianza TANTO la raíz
# como esa sub-CA: DSS reconstruye la cadena desde el certificado que trae
# la propia firma XML hasta un ancla en este almacén, y no está garantizado
# que sólo con la raíz baste (depende de si DSS resuelve el salto
# intermedio por AIA o lo exige presente). Con las dos, sobra.
#
# La contraseña ("changeit", fija) no protege ningún secreto: un almacén de
# confianza no lleva claves privadas, sólo certificados que ya son
# públicos. No hay nada que "GENERAR" aquí.
#
#   ./construir-jks-trust-validator.sh
#
set -euo pipefail
cd "$(dirname "$0")/.."
COMPOSE_DIR="$PWD/docker-compose"
cd "$COMPOSE_DIR"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
paso() { printf '    %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

command -v keytool >/dev/null || fail "falta keytool (parte del JDK)"

DEST="${TRUST_VALIDATOR_DIR:-../eudi-srv-trust-validator/docker/claves}"
KS="$DEST/lotl-trust.jks"
PASS="changeit"

[ -f pki/publico/ca/root.crt ] || fail "no existe pki/publico/ca/root.crt. Ejecuta primero:
   cd pki && ./crear-pki.sh <tu-dominio>"
[ -f pki/publico/ca/tl.crt ] || fail "no existe pki/publico/ca/tl.crt"

mkdir -p "$DEST"
rm -f "$KS"

info "Construyendo $KS"
keytool -importcert -noprompt -alias civica-root \
    -file pki/publico/ca/root.crt -keystore "$KS" \
    -storepass "$PASS" -storetype JKS >/dev/null
keytool -importcert -noprompt -alias civica-tl-ca \
    -file pki/publico/ca/tl.crt -keystore "$KS" \
    -storepass "$PASS" -storetype JKS >/dev/null
chmod 644 "$KS"

paso "2 anclas instaladas: civica-root, civica-tl-ca"
paso "instalado  $KS"
echo
info "Listo. Para que el servicio lo tome:"
paso "cd ../eudi-srv-trust-validator/docker-compose && docker compose up -d --force-recreate trust-validator"
