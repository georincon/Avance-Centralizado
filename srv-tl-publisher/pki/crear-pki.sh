#!/usr/bin/env bash
#
# Ceremonia de creación de la PKI del ecosistema.
#
# POR QUÉ EXISTE
#
# Hoy no hay PKI: los certificados del stack son autofirmados sueltos, sin
# authorityInfoAccess ni crlDistributionPoints. Eso significa que NO SE PUEDE
# REVOCAR NADA — un validador no tiene dónde preguntar si un certificado sigue
# vigente, y esas dos extensiones se hornean AL EMITIR: no se pueden añadir
# después. Por eso la PKI va antes de reemitir cualquier cosa.
#
# QUÉ CREA
#
#   Civica EUDI Root CA            EC P-384, 20 años, autofirmada
#     ├─ Civica TL Signing CA      EC P-256, 10 años
#     ├─ Civica Wallet Provider CA
#     ├─ Civica PID Issuer CA
#     ├─ Civica QEAA CA
#     ├─ Civica QTSP Signing CA
#     └─ Civica RP Access CA
#
# Y la CRL inicial de cada una. Una CRL vacía no es opcional: un validador
# estricto que no puede descargar la CRL falla igual que si el certificado
# estuviera revocado.
#
# POR QUÉ `openssl ca` Y NO `openssl x509 -req`
#
# `x509 -req` firma certificados pero no lleva registro de lo emitido, y sin
# índice no se puede generar una CRL. Como el objetivo de esta fase es
# precisamente poder revocar, cada CA se monta con su index.txt, su serial y su
# crlnumber.
#
# USO
#
#   ./crear-pki.sh                        # dominio desde ../../.env
#   ./crear-pki.sh eudi.miempresa.com
#   PKI_ROOT_PASSPHRASE=... ./crear-pki.sh   # cifra la clave de la raíz
#
# OJO: el dominio queda EMPOTRADO en las URLs de AIA y CRLDP de cada
# certificado. Cambiarlo después obliga a reemitir toda la PKI.

set -euo pipefail
cd "$(dirname "$0")"
source ./perfiles.sh

RAIZ_DIR="root"
SUBCA_DIR="subca"
ENV_FILE="../.env"

info()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
paso()  { printf '    %s\n' "$*"; }
warn()  { printf '\033[1;33m /!\\\033[0m %s\n' "$*"; }
fail()  { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

DOMINIO=""
FORZAR=0
while [ $# -gt 0 ]; do
    case "$1" in
        --forzar)  FORZAR=1; shift ;;
        -h|--help) sed -n '2,45p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        -*)        fail "opción desconocida: $1" ;;
        *)         [ -z "$DOMINIO" ] || fail "más de un dominio: '$DOMINIO' y '$1'"
                   DOMINIO="$1"; shift ;;
    esac
done

if [ -z "$DOMINIO" ]; then
    [ -f "$ENV_FILE" ] || fail "no hay $ENV_FILE; pasa el dominio como argumento"
    DOMINIO="$(sed -n 's/^PUBLIC_DOMAIN=//p' "$ENV_FILE" | head -1)"
    [ -n "$DOMINIO" ] || fail "PUBLIC_DOMAIN no está en $ENV_FILE"
fi
case "$DOMINIO" in
    http://*|https://*|*/*) fail "pasa sólo el nombre de host, sin esquema ni rutas" ;;
esac

BASE_URL="https://$DOMINIO/pki"

if [ -f "$RAIZ_DIR/certs/root.cert.pem" ] && [ "$FORZAR" != "1" ]; then
    fail "ya existe una PKI en $PWD/$RAIZ_DIR.
       Rehacerla invalida TODO lo emitido con ella, incluida la CA que la app
       lleva empotrada. Si de verdad quieres empezar de cero:
         rm -rf $RAIZ_DIR $SUBCA_DIR && ./crear-pki.sh $DOMINIO"
fi

command -v openssl >/dev/null || fail "hace falta openssl"

info "Dominio de la PKI: $DOMINIO"
warn "Queda empotrado en las URLs de AIA y CRLDP de cada certificado."
warn "    Cambiarlo después obliga a reemitir toda la PKI."
echo

# ─── Estructura de una CA ────────────────────────────────────────────────────
preparar_ca() {   # preparar_ca <directorio>
    local d="$1"
    mkdir -p "$d"/{certs,crl,newcerts,private,csr}
    chmod 700 "$d/private"
    : > "$d/index.txt"
    echo 1000 > "$d/serial"
    echo 1000 > "$d/crlnumber"
}

# ─── openssl.cnf de una CA ───────────────────────────────────────────────────
#
# `openssl ca` necesita un fichero de configuración; se genera uno por CA con
# sus rutas y sus URLs ya resueltas, para que ni el script ni nadie tenga que
# pasar veinte flags por línea de comandos.
# La validez de la CRL va aparte de la de los certificados, y no es un detalle:
# la raíz está OFFLINE —su clave no vive en ningún contenedor— así que el
# servicio de PKI no puede refrescar su CRL como hace con las de las sub-CA.
# Con el valor de las sub-CA, la CRL de la raíz caduca al día siguiente de la
# ceremonia y se queda caducada para siempre, y una CRL vencida hace fallar una
# validación estricta igual que una revocación.
escribir_cnf() {  # escribir_cnf <dir> <nombre-crl> <dias-cert> <dias-crl>
    local d="$1" crl="$2" dias="$3" dias_crl="$4"
    cat > "$d/openssl.cnf" <<CNF
# Generado por crear-pki.sh — no editar a mano.
[ ca ]
default_ca = CA_default

[ CA_default ]
dir               = $PWD/$d
certs             = \$dir/certs
crl_dir           = \$dir/crl
new_certs_dir     = \$dir/newcerts
database          = \$dir/index.txt
serial            = \$dir/serial
crlnumber         = \$dir/crlnumber
RANDFILE          = \$dir/private/.rand

private_key       = \$dir/private/ca.key.pem
certificate       = \$dir/certs/ca.cert.pem

crl               = \$dir/crl/$crl
default_crl_days  = $dias_crl
default_md        = sha256
default_days      = $dias

name_opt          = ca_default
cert_opt          = ca_default
preserve          = no
policy            = politica_flexible
unique_subject    = no
copy_extensions   = none

[ politica_flexible ]
countryName             = optional
stateOrProvinceName     = optional
organizationName        = optional
organizationalUnitName  = optional
commonName              = supplied
emailAddress            = optional

[ req ]
default_bits        = 2048
distinguished_name  = req_dn
string_mask         = utf8only
default_md          = sha256
prompt              = no

[ req_dn ]
C  = $PAIS
O  = $ORG
CN = placeholder

# ─── Extensiones ────────────────────────────────────────────────────────────
[ v3_raiz ]
subjectKeyIdentifier   = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints       = critical, CA:true, pathlen:1
keyUsage               = critical, keyCertSign, cRLSign

[ v3_subca ]
subjectKeyIdentifier   = hash
authorityKeyIdentifier = keyid:always,issuer
basicConstraints       = critical, CA:true, pathlen:0
keyUsage               = critical, keyCertSign, cRLSign
authorityInfoAccess    = OCSP;URI:$BASE_URL/ocsp, caIssuers;URI:$BASE_URL/ca/root.crt
crlDistributionPoints  = URI:$BASE_URL/crl/root.crl

[ v3_crl ]
authorityKeyIdentifier = keyid:always
CNF
}

# ─── 1. Raíz ─────────────────────────────────────────────────────────────────
info "Raíz — Civica EUDI Root CA (EC P-384, 20 años)"
preparar_ca "$RAIZ_DIR"
escribir_cnf "$RAIZ_DIR" "root.crl" 3650 365

# secp384r1 = P-384. Sólo la raíz: da margen para 20 años de vigencia.
openssl ecparam -name secp384r1 -genkey -noout -out "$RAIZ_DIR/private/ca.key.pem" 2>/dev/null
if [ -n "${PKI_ROOT_PASSPHRASE:-}" ]; then
    openssl pkcs8 -topk8 -in "$RAIZ_DIR/private/ca.key.pem" \
        -out "$RAIZ_DIR/private/ca.key.pem.enc" -passout env:PKI_ROOT_PASSPHRASE 2>/dev/null
    mv "$RAIZ_DIR/private/ca.key.pem.enc" "$RAIZ_DIR/private/ca.key.pem"
    paso "clave de la raíz cifrada con PKI_ROOT_PASSPHRASE"
fi
chmod 400 "$RAIZ_DIR/private/ca.key.pem"

RAIZ_PASSIN=()
[ -n "${PKI_ROOT_PASSPHRASE:-}" ] && RAIZ_PASSIN=(-passin env:PKI_ROOT_PASSPHRASE)

openssl req -config "$RAIZ_DIR/openssl.cnf" -new -x509 -sha384 -days 7300 \
    -key "$RAIZ_DIR/private/ca.key.pem" "${RAIZ_PASSIN[@]}" \
    -extensions v3_raiz \
    -subj "/C=$PAIS/O=$ORG/CN=Civica EUDI Root CA" \
    -out "$RAIZ_DIR/certs/ca.cert.pem" 2>/dev/null
chmod 444 "$RAIZ_DIR/certs/ca.cert.pem"
paso "root/certs/ca.cert.pem"

# ─── 2. Sub-CAs ──────────────────────────────────────────────────────────────
for entrada in "${SUBCAS[@]}"; do
    slug="$(echo "$entrada" | cut -d'|' -f1)"
    cn="$(echo   "$entrada" | cut -d'|' -f2)"
    para="$(echo "$entrada" | cut -d'|' -f3)"
    d="$SUBCA_DIR/$slug"

    info "Sub-CA $slug — $cn"
    paso "$para"
    preparar_ca "$d"
    escribir_cnf "$d" "$slug.crl" 825 1

    openssl ecparam -name prime256v1 -genkey -noout -out "$d/private/ca.key.pem" 2>/dev/null
    chmod 400 "$d/private/ca.key.pem"

    openssl req -config "$d/openssl.cnf" -new -sha256 \
        -key "$d/private/ca.key.pem" \
        -subj "/C=$PAIS/O=$ORG/CN=$cn" \
        -out "$d/csr/ca.csr.pem" 2>/dev/null

    # La firma la hace la RAÍZ, con su configuración y sus extensiones de sub-CA
    openssl ca -batch -config "$RAIZ_DIR/openssl.cnf" \
        -extensions v3_subca -days 3650 -notext -md sha384 \
        "${RAIZ_PASSIN[@]}" \
        -in "$d/csr/ca.csr.pem" -out "$d/certs/ca.cert.pem" 2>/dev/null
    chmod 444 "$d/certs/ca.cert.pem"
    paso "$d/certs/ca.cert.pem"
done

# ─── 3. CRLs iniciales ───────────────────────────────────────────────────────
#
# Vacías, pero imprescindibles: un validador estricto que no puede descargar la
# CRL que anuncia el CRLDP falla igual que ante una revocación.
info "CRLs iniciales"
openssl ca -batch -config "$RAIZ_DIR/openssl.cnf" -gencrl \
    "${RAIZ_PASSIN[@]}" -out "$RAIZ_DIR/crl/root.crl" 2>/dev/null
paso "root/crl/root.crl"
for entrada in "${SUBCAS[@]}"; do
    slug="$(echo "$entrada" | cut -d'|' -f1)"
    openssl ca -batch -config "$SUBCA_DIR/$slug/openssl.cnf" -gencrl \
        -out "$SUBCA_DIR/$slug/crl/$slug.crl" 2>/dev/null
    paso "$SUBCA_DIR/$slug/crl/$slug.crl"
done

# ─── 4. Cadena y directorio de publicación ───────────────────────────────────
info "Material público (lo que servirá /pki)"
# El dominio queda registrado: emitir.sh tiene que usar EXACTAMENTE el mismo,
# o las URLs de AIA/CRLDP de las hojas apuntarían a otro sitio que las de sus CAs.
echo "$DOMINIO" > dominio.txt
mkdir -p publico/{ca,crl}
cp "$RAIZ_DIR/certs/ca.cert.pem" publico/ca/root.crt
openssl x509 -in "$RAIZ_DIR/certs/ca.cert.pem" -outform DER -out publico/ca/root.der
cp "$RAIZ_DIR/crl/root.crl" publico/crl/root.crl
for entrada in "${SUBCAS[@]}"; do
    slug="$(echo "$entrada" | cut -d'|' -f1)"
    cp "$SUBCA_DIR/$slug/certs/ca.cert.pem" "publico/ca/$slug.crt"
    cp "$SUBCA_DIR/$slug/crl/$slug.crl"     "publico/crl/$slug.crl"
done
paso "publico/ca/ y publico/crl/"

# ─── 5. Verificación ─────────────────────────────────────────────────────────
echo
info "Verificación"
fallos=0
for entrada in "${SUBCAS[@]}"; do
    slug="$(echo "$entrada" | cut -d'|' -f1)"
    if openssl verify -CAfile "$RAIZ_DIR/certs/ca.cert.pem" \
         "$SUBCA_DIR/$slug/certs/ca.cert.pem" >/dev/null 2>&1; then
        paso "cadena $slug -> raíz: OK"
    else
        warn "cadena $slug -> raíz: FALLA"; fallos=$((fallos+1))
    fi
done
[ "$fallos" -eq 0 ] || fail "la jerarquía no valida"

echo
info "Resumen"
printf '    %-28s %s\n' "$(openssl x509 -in "$RAIZ_DIR/certs/ca.cert.pem" -noout -subject | sed 's/^subject=//')" \
    "$(openssl x509 -in "$RAIZ_DIR/certs/ca.cert.pem" -noout -enddate | sed 's/^notAfter=//')"
for entrada in "${SUBCAS[@]}"; do
    slug="$(echo "$entrada" | cut -d'|' -f1)"
    printf '    %-28s %s\n' "  $slug" \
        "$(openssl x509 -in "$SUBCA_DIR/$slug/certs/ca.cert.pem" -noout -enddate | sed 's/^notAfter=//')"
done

echo
warn "LA CLAVE DE LA RAÍZ ES EL ACTIVO MÁS CRÍTICO DEL ECOSISTEMA."
warn "    Está en $PWD/$RAIZ_DIR/private/ca.key.pem"
warn "    Sólo hace falta para emitir una sub-CA nueva o su CRL. Muévela a un"
warn "    soporte offline y bórrala de aquí: con ella se puede emitir cualquier"
warn "    certificado que la app considere de confianza."
echo
info "Siguiente: emitir las hojas con  ./emitir.sh --listar"
