#!/usr/bin/env bash
#
# Revoca un certificado y regenera la CRL de su sub-CA.
#
# POR QUÉ EXISTE
#
# Es la razón de ser de toda la Fase 1. Hasta ahora los certificados del stack
# eran autofirmados sin CRLDP: no había dónde publicar que uno dejó de valer, así
# que revocar era literalmente imposible. Con la PKI, revocar es esto.
#
# USO
#
#   ./revocar.sh issued/verifier/cert.pem
#   ./revocar.sh issued/verifier/cert.pem --motivo keyCompromise
#   ./revocar.sh --regenerar-crl rp        # sólo refrescar la CRL, sin revocar
#
# MOTIVOS (RFC 5280): unspecified, keyCompromise, CACompromise,
# affiliationChanged, superseded, cessationOfOperation, certificateHold
#
# LA CRL CADUCA. Se emite con nextUpdate a 24 h, así que hay que regenerarla
# aunque no haya revocaciones nuevas: para un validador estricto una CRL vencida
# es tan mala como una revocación. Eso es trabajo de un cron, no de este script.

set -euo pipefail
cd "$(dirname "$0")"
source ./perfiles.sh

info() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
paso() { printf '    %s\n' "$*"; }
warn() { printf '\033[1;33m /!\\\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mERROR:\033[0m %s\n' "$*" >&2; exit 1; }

CERT=""; MOTIVO="unspecified"; SOLO_CRL=""
while [ $# -gt 0 ]; do
    case "$1" in
        --motivo)         MOTIVO="${2:?}"; shift 2 ;;
        --regenerar-crl)  SOLO_CRL="${2:?--regenerar-crl necesita el slug de la sub-CA}"; shift 2 ;;
        -h|--help)        sed -n '2,25p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        -*)               fail "opción desconocida: $1" ;;
        *)                CERT="$1"; shift ;;
    esac
done

regenerar_crl() {   # regenerar_crl <slug>
    local slug="$1" cnf="subca/$slug/openssl.cnf"
    [ -f "$cnf" ] || fail "no existe la sub-CA '$slug'"
    openssl ca -batch -config "$cnf" -gencrl -out "subca/$slug/crl/$slug.crl" 2>/dev/null
    cp "subca/$slug/crl/$slug.crl" "publico/crl/$slug.crl"
    paso "publico/crl/$slug.crl  (nextUpdate $(openssl crl -in "subca/$slug/crl/$slug.crl" -noout -nextupdate | sed 's/^nextUpdate=//'))"
}

if [ -n "$SOLO_CRL" ]; then
    info "Regenerando la CRL de $SOLO_CRL"
    regenerar_crl "$SOLO_CRL"
    exit 0
fi

[ -n "$CERT" ] || fail "falta la ruta del certificado a revocar"
[ -f "$CERT" ] || fail "no existe $CERT"

# ¿Qué sub-CA lo emitió? Se deduce del Authority Key Identifier, no del nombre
# del directorio: el fichero puede haberse movido.
AKI="$(openssl x509 -in "$CERT" -noout -ext authorityKeyIdentifier 2>/dev/null | tail -1 | tr -d ' ')"
EMISOR=""
for entrada in "${SUBCAS[@]}"; do
    slug="${entrada%%|*}"
    SKI="$(openssl x509 -in "subca/$slug/certs/ca.cert.pem" -noout -ext subjectKeyIdentifier 2>/dev/null | tail -1 | tr -d ' ')"
    [ "$SKI" = "$AKI" ] && { EMISOR="$slug"; break; }
done
[ -n "$EMISOR" ] || fail "ninguna sub-CA de esta PKI emitió $CERT"

info "Revocando"
paso "certificado  $CERT"
paso "serie        $(openssl x509 -in "$CERT" -noout -serial | sed 's/^serial=//')"
paso "emisor       $EMISOR ($(subca_campo "$EMISOR" 2))"
paso "motivo       $MOTIVO"

openssl ca -config "subca/$EMISOR/openssl.cnf" -revoke "$CERT" \
    -crl_reason "$MOTIVO" 2>&1 | grep -iE "revok|already" | sed 's/^/    /' || true

info "Regenerando la CRL"
regenerar_crl "$EMISOR"

echo
info "Comprobación"
cat "publico/crl/$EMISOR.crl" root/certs/ca.cert.pem "subca/$EMISOR/certs/ca.cert.pem" > /tmp/.cadena.$$.pem
if openssl verify -crl_check -CAfile /tmp/.cadena.$$.pem "$CERT" >/dev/null 2>&1; then
    rm -f /tmp/.cadena.$$.pem
    fail "el certificado SIGUE validando: la revocación no surtió efecto"
fi
paso "el certificado ya no valida con -crl_check"
rm -f /tmp/.cadena.$$.pem
echo
warn "La CRL sólo surte efecto donde se pueda descargar."
warn "    Publícala en $(cat dominio.txt 2>/dev/null || echo '<dominio>')/pki/crl/$EMISOR.crl (Fase 1.3)."
