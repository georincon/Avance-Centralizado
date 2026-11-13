#!/usr/bin/env bash
#
# Emite el certificado con el que el TLP firma la Trusted List y lo instala
# donde el contenedor lo espera.
#
# POR QUÉ EXISTE
#
# El TLP venía con un par autofirmado RSA-4096 (`cert.der` + `privkey.der`)
# metido a mano dentro del clon del upstream. Tenía tres problemas, y los tres
# importan:
#
#   1. Autofirmado. No colgaba de ninguna CA, así que la firma de la Trusted
#      List no se podía validar contra nada. Una TSL cuya firma no encadena a
#      un ancla conocida no sirve para lo único que sirve una TSL.
#   2. RSA con el código firmando ECDSA. `xmlGen.py` pide
#      `SignatureMethod.ECDSA_SHA256`; con una clave RSA eso no firma, revienta.
#   3. En DER, cuando signxml sólo sabe leer PEM: hace
#      `load_pem_private_key()` sobre lo que le pases, así que los bytes DER
#      fallaban antes incluso de llegar al punto anterior.
#
# Ahora el firmante sale de la `Civica TL Signing CA`, que es la sub-CA que la
# jerarquía reserva justo para esto, y se instala FUERA del clon de `src/`:
# ese directorio se borra y se vuelve a clonar, y con él se llevaba el material
# de firma por delante.
#
# USO
#
#   ./emitir-firmante-tl.sh            # emite si no hay nada instalado
#   ./emitir-firmante-tl.sh --rotar    # emite uno nuevo aunque ya haya
#
# OJO al rotar: las Trusted Lists ya publicadas siguen firmadas con el anterior.
# Rotar sin republicar deja firmas que nadie puede validar.

set -euo pipefail

RAIZ="$(cd "$(dirname "$0")/.." && pwd)"   # raíz de srv-tl-publisher/

PKI="$RAIZ/pki"
DESTINO="$RAIZ/firma"
NOMBRE="tl-signer"
CN="Civica Trusted List Signer"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
paso() { printf '    %s\n' "$*"; }
warn() { printf '\033[1;33m /!\\\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

ROTAR=0
[ "${1:-}" = "--rotar" ] && ROTAR=1
[ $# -gt 1 ] && fail "sólo se acepta --rotar"
[ $# -eq 1 ] && [ "$ROTAR" = "0" ] && fail "opción desconocida: $1"

# ── 1. La PKI tiene que existir ──────────────────────────────────────────────
[ -d "$PKI/subca/tl" ] || fail "no existe la sub-CA 'tl'. Ejecuta primero:
       cd pki && ./crear-pki.sh <tu-dominio>"

# ── 2. ¿Ya hay un firmante instalado? ────────────────────────────────────────
if [ -f "$DESTINO/$NOMBRE.key" ] && [ "$ROTAR" = "0" ]; then
    info "Ya hay un firmante instalado en $DESTINO/"
    openssl x509 -in "$DESTINO/$NOMBRE.crt" -noout -subject -dates | sed 's/^/    /'
    echo
    paso "Para emitir uno nuevo:  ./scripts/emitir-firmante-tl.sh --rotar"
    exit 0
fi

# ── 3. Emitir desde la sub-CA de trusted lists ───────────────────────────────
info "Emitiendo el firmante desde la Civica TL Signing CA…"
"$PKI/emitir.sh" tl-signer --cn "$CN" --nombre "$NOMBRE" >/dev/null \
    || fail "la emisión falló; ejecuta '$PKI/emitir.sh tl-signer --cn \"$CN\"' para ver el error"

ORIGEN="$PKI/issued/$NOMBRE"
[ -f "$ORIGEN/cert.pem" ] || fail "emitir.sh no dejó $ORIGEN/cert.pem"

# ── 4. Instalar fuera del clon ───────────────────────────────────────────────
#
# Tres ficheros, y cada uno cumple una función distinta:
#
#   .crt        la hoja sola. Es la que va como <X509Certificate> del operador
#               dentro del cuerpo de la TSL y de la que sale el thumbprint.
#   -chain.pem  hoja + sub-CA. Es la que se mete en el <KeyInfo> de la firma,
#               para que quien valide pueda construir el camino hasta la raíz
#               sin tener que conocer de antemano la sub-CA.
#   .key        la clave privada.
#
# La raíz NO va en la cadena, a propósito: es el ancla de confianza. Tiene que
# estar ya instalada en quien valida, no viajar dentro de lo que se valida.
mkdir -p "$DESTINO"
cp "$ORIGEN/cert.pem"      "$DESTINO/$NOMBRE.crt"
cp "$ORIGEN/fullchain.pem" "$DESTINO/$NOMBRE-chain.pem"
cp "$ORIGEN/key.pem"       "$DESTINO/$NOMBRE.key"
chmod 644 "$DESTINO/$NOMBRE.crt" "$DESTINO/$NOMBRE-chain.pem"
chmod 600 "$DESTINO/$NOMBRE.key"

# ── 5. Comprobar lo que de verdad importa ────────────────────────────────────
echo
info "Verificación"

CURVA="$(openssl x509 -in "$DESTINO/$NOMBRE.crt" -noout -text | grep -o 'prime256v1\|secp384r1' | head -1)"
[ "$CURVA" = "prime256v1" ] \
    || fail "la clave no es P-256 (es '$CURVA'). El TLP construye EC2Key(crv=1),
       que es P-256 y nada más: cualquier otra curva se ignora EN SILENCIO."
paso "curva      P-256, la única que el TLP sabe leer"

openssl verify -CAfile "$PKI/root/certs/ca.cert.pem" \
    -untrusted "$ORIGEN/ca.pem" "$DESTINO/$NOMBRE.crt" >/dev/null \
    || fail "la cadena no valida contra la raíz"
paso "cadena     valida contra la raíz"

# El par tiene que casar: un certificado y una clave que no se corresponden
# firman igual y fallan al verificar, que es el peor momento para enterarse.
PUB_CERT="$(openssl x509 -in "$DESTINO/$NOMBRE.crt" -noout -pubkey | openssl sha256)"
PUB_CLAVE="$(openssl pkey -in "$DESTINO/$NOMBRE.key" -pubout | openssl sha256)"
[ "$PUB_CERT" = "$PUB_CLAVE" ] || fail "el certificado y la clave no son el mismo par"
paso "par        el certificado y la clave se corresponden"

echo
info "Instalado en $DESTINO/"
ls -1 "$DESTINO" | sed 's/^/    /'
echo
paso "El compose lo monta en /app/firma. Para que el TLP lo tome:"
paso "  cd $RAIZ/docker-compose && docker compose up -d --force-recreate tl-publisher"
