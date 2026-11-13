#!/usr/bin/env bash
#
# Emite una hoja desde la sub-CA que le corresponde.
#
# POR QUÉ EXISTE
#
# Cada certificado del ecosistema tiene un perfil distinto —qué keyUsage lleva,
# qué extendedKeyUsage, cuánto dura— y equivocarse es silencioso: el
# certificado se genera igual y falla más tarde, en producción y de forma rara.
# Un ejemplo real: el Document Signer de un mdoc necesita el EKU
# 1.0.18013.5.1.2 de ISO 18013-5; sin él un validador estricto rechaza la
# credencial aunque la firma sea perfecta.
#
# Los perfiles están en perfiles.sh, que es el mismo fichero que lee
# crear-pki.sh. Así la CA que se crea y la hoja que se emite no pueden
# desincronizarse.
#
# TODA hoja sale con authorityInfoAccess y crlDistributionPoints apuntando a su
# sub-CA. Son las dos extensiones que hacen posible revocar, y no se pueden
# añadir después: o están al emitir, o ese certificado no es revocable nunca.
#
# USO
#
#   ./emitir.sh --listar
#   ./emitir.sh rp-access   --cn eudi.miempresa.com --san eudi.miempresa.com --p12
#   ./emitir.sh tl-signer   --cn "Civica TL Signer"
#   ./emitir.sh pid-ds      --cn "Civica PID Document Signer"

set -euo pipefail
cd "$(dirname "$0")"
source ./perfiles.sh

SUBCA_DIR="subca"
SALIDA="issued"

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
paso() { printf '    %s\n' "$*"; }
warn() { printf '\033[1;33m /!\\\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

listar() {
    printf '\n%-16s %-6s %-6s %s\n' PERFIL SUB-CA DÍAS DESCRIPCIÓN
    printf '%.0s─' {1..78}; echo
    local l
    for l in "${PERFILES[@]}"; do
        printf '%-16s %-6s %-6s %s\n' \
            "$(echo "$l" | cut -d'|' -f1)" "$(echo "$l" | cut -d'|' -f2)" \
            "$(echo "$l" | cut -d'|' -f3)" "$(echo "$l" | cut -d'|' -f7)"
    done
    echo
}

[ $# -eq 0 ] && { listar; exit 0; }

PERFIL=""; CN=""; SAN=""; NOMBRE=""; P12=0
while [ $# -gt 0 ]; do
    case "$1" in
        --listar)  listar; exit 0 ;;
        --cn)      CN="${2:?--cn necesita un valor}"; shift 2 ;;
        --san)     SAN="${2:?--san necesita un valor}"; shift 2 ;;
        --nombre)  NOMBRE="${2:?--nombre necesita un valor}"; shift 2 ;;
        --p12)     P12=1; shift ;;
        -h|--help) sed -n '2,30p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        -*)        fail "opción desconocida: $1" ;;
        *)         [ -z "$PERFIL" ] || fail "más de un perfil: '$PERFIL' y '$1'"
                   PERFIL="$1"; shift ;;
    esac
done

[ -n "$PERFIL" ] || { listar; fail "falta el perfil"; }
SUBCA="$(perfil_campo "$PERFIL" 2)" || fail "perfil desconocido: $PERFIL (usa --listar)"
DIAS="$(perfil_campo   "$PERFIL" 3)"
KU="$(perfil_campo     "$PERFIL" 4)"
EKU="$(perfil_campo    "$PERFIL" 5)"
POL="$(perfil_campo    "$PERFIL" 6)"
DESC="$(perfil_campo   "$PERFIL" 7)"

[ -d "$SUBCA_DIR/$SUBCA" ] || fail "no existe la sub-CA '$SUBCA'. ¿Ejecutaste ./crear-pki.sh?"
[ -f dominio.txt ] || fail "falta dominio.txt: la PKI no está creada"
DOMINIO="$(cat dominio.txt)"
BASE_URL="https://$DOMINIO/pki"

[ -n "$CN" ] || fail "falta --cn"
[ -n "$NOMBRE" ] || NOMBRE="$PERFIL"

DEST="$SALIDA/$NOMBRE"
mkdir -p "$DEST"

info "Emitiendo '$NOMBRE'"
paso "perfil     $PERFIL — $DESC"
paso "sub-CA     $SUBCA ($(subca_campo "$SUBCA" 2))"
paso "CN         $CN"
[ -n "$SAN" ] && paso "SAN        DNS:$SAN"
paso "vigencia   $DIAS días"

# ─── Extensiones de esta hoja ────────────────────────────────────────────────
EXT="$DEST/.ext.cnf"
{
    echo "basicConstraints       = critical, CA:false"
    echo "subjectKeyIdentifier   = hash"
    echo "authorityKeyIdentifier = keyid:always"
    echo "keyUsage               = $KU"
    [ -n "$EKU" ] && echo "extendedKeyUsage       = critical, $EKU"
    # AIA y CRLDP apuntan a la SUB-CA que firma, no a la raíz.
    echo "authorityInfoAccess    = OCSP;URI:$BASE_URL/ocsp, caIssuers;URI:$BASE_URL/ca/$SUBCA.crt"
    echo "crlDistributionPoints  = URI:$BASE_URL/crl/$SUBCA.crl"
    echo "certificatePolicies    = $OID_POLITICAS.$POL"
    [ -n "$SAN" ] && echo "subjectAltName         = DNS:$SAN"
} > "$EXT"

# ─── Clave y CSR ─────────────────────────────────────────────────────────────
# P-256 siempre en las hojas: el verifier firma con ES256 y setup_trusted_CAs()
# del TLP construye EC2Key(crv=1), que es P-256 y nada más. Cualquier otra curva
# se ignora EN SILENCIO.
openssl ecparam -name prime256v1 -genkey -noout -out "$DEST/key.pem" 2>/dev/null
chmod 600 "$DEST/key.pem"
openssl req -new -sha256 -key "$DEST/key.pem" \
    -subj "/C=$PAIS/O=$ORG/CN=$CN" -out "$DEST/csr.pem" 2>/dev/null

# ─── Firma ───────────────────────────────────────────────────────────────────
openssl ca -batch -config "$SUBCA_DIR/$SUBCA/openssl.cnf" \
    -extfile "$EXT" -days "$DIAS" -notext -md sha256 \
    -in "$DEST/csr.pem" -out "$DEST/cert.pem" 2>/dev/null \
    || fail "la sub-CA rechazó la petición"
rm -f "$EXT" "$DEST/csr.pem"

# Cadena completa: hoja + sub-CA. La raíz NO va en la cadena — es el ancla de
# confianza y tiene que estar ya instalada en quien valida, no viajar con ella.
cat "$DEST/cert.pem" "$SUBCA_DIR/$SUBCA/certs/ca.cert.pem" > "$DEST/fullchain.pem"
cp "$SUBCA_DIR/$SUBCA/certs/ca.cert.pem" "$DEST/ca.pem"
chmod 644 "$DEST/cert.pem" "$DEST/fullchain.pem" "$DEST/ca.pem"

# ─── PKCS#12 opcional ────────────────────────────────────────────────────────
if [ "$P12" = "1" ]; then
    CLAVE_P12="$(openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 32)"
    openssl pkcs12 -export -in "$DEST/cert.pem" -inkey "$DEST/key.pem" \
        -certfile "$DEST/ca.pem" -name "$NOMBRE" \
        -out "$DEST/keystore.p12" -passout pass:"$CLAVE_P12" 2>/dev/null
    # Lo monta un contenedor que corre como usuario no root
    chmod 644 "$DEST/keystore.p12"
    echo "$CLAVE_P12" > "$DEST/keystore.pass"
    chmod 600 "$DEST/keystore.pass"
    paso "keystore.p12 (contraseña en keystore.pass)"
fi

# ─── Verificación ────────────────────────────────────────────────────────────
echo
info "Verificación"
openssl verify -CAfile root/certs/ca.cert.pem -untrusted "$DEST/ca.pem" "$DEST/cert.pem" \
    | sed 's/^/    /' || fail "la cadena no valida contra la raíz"

for ext in "Authority Information Access" "CRL Distribution Points" "Certificate Policies"; do
    if openssl x509 -in "$DEST/cert.pem" -noout -text | grep -q "$ext"; then
        paso "$ext: presente"
    else
        fail "falta la extensión '$ext' — sin ella este certificado no sería revocable"
    fi
done

echo
info "En $DEST/"
ls -1 "$DEST" | sed 's/^/    /'
