#!/usr/bin/env bash
#
# Definición de la jerarquía y de los perfiles de certificado.
#
# Se carga desde crear-pki.sh y desde emitir.sh para que ambos vean exactamente
# la misma verdad: qué sub-CAs existen, qué emite cada una y con qué extensiones.
# Separarlo evita el fallo clásico de que el script que crea la CA y el que
# emite hojas se desincronicen.

# ─── Identidad de la organización ────────────────────────────────────────────
ORG="Civica EUDI"
PAIS="CO"

# ─── Arco de OIDs propio ─────────────────────────────────────────────────────
#
# Las certificate policies necesitan un OID bajo un arco que nos pertenezca.
# NO se usa 1.3.6.1.4.1.<PEN> porque no tenemos un Private Enterprise Number de
# IANA, y apropiarse del de otro es incorrecto y detectable.
#
# 2.25 es el arco de UUIDs (ITU-T X.667 / RFC 4122): cualquiera puede derivar
# un OID del UUID que haya generado, sin registrarse en ningún sitio. Es la
# forma correcta de tener un arco propio sin trámite.
#
#   UUID: a91bd071-bb7b-4654-9591-f315710a0522
OID_ARCO="2.25.224783951054833873364394138650481919266"
OID_POLITICAS="$OID_ARCO.1"      # .1.<n> = una política por rol

# ─── Sub-CAs ─────────────────────────────────────────────────────────────────
#
# slug|Common Name|para qué
SUBCAS=(
    "tl|Civica TL Signing CA|Firma las Trusted Lists (TSL) y la LOTL"
    "wp|Civica Wallet Provider CA|Claves de attestation del wallet-provider"
    "pid|Civica PID Issuer CA|Document Signers del pid-issuer (perfil IACA para mdoc)"
    "qeaa|Civica QEAA CA|Emisores de atestaciones cualificadas (pseudónimos, doc-issuer)"
    "qtsp|Civica QTSP Signing CA|Certificados de firma del usuario (QES)"
    "rp|Civica RP Access CA|Access certificates de relying parties y el Registrar"
)

# ─── Perfiles de hoja ────────────────────────────────────────────────────────
#
# slug|sub-CA|días|keyUsage|extendedKeyUsage|política|descripción
#
# El keyUsage va SIEMPRE critical: es lo que impide que un certificado emitido
# para firmar se use para otra cosa.
PERFILES=(
    "tl-signer|tl|365|critical,digitalSignature,nonRepudiation||1|Firma XAdES de la Trusted List"
    "wallet-provider|wp|825|critical,digitalSignature||2|Firma de WIA/WUA del wallet-provider"
    "pid-ds|pid|457|critical,digitalSignature|1.0.18013.5.1.2|3|Document Signer del PID (mdoc + SD-JWT VC)"
    "qeaa-ds|qeaa|457|critical,digitalSignature|1.0.18013.5.1.2|4|Document Signer del emisor QEAA"
    "qtsp-user|qtsp|825|critical,nonRepudiation||5|Certificado de firma del usuario (QES)"
    "rp-access|rp|825|critical,digitalSignature|clientAuth, 1.0.18013.5.1.6|6|Access certificate de un relying party"
    "rp-registrar|rp|825|critical,digitalSignature,nonRepudiation||7|Firma de registration certificates"
    "status-list-ds|pid|457|critical,digitalSignature||8|Firmante de las Token Status Lists"
)

# `pid-ds` y `qeaa-ds` llevan el EKU 1.0.18013.5.1.2, que es el que ISO 18013-5
# define para el Document Signer de un mdoc. Sin él, un validador estricto
# rechaza la credencial aunque la firma sea correcta.

# `status-list-ds` cuelga de la MISMA sub-CA que el Document Signer del PID, y
# eso es deliberado: quien valida un PID ya tiene que confiar en la
# `Civica PID Issuer CA`, así que la Token Status List que dice si ese PID sigue
# vigente valida contra el mismo ancla, sin añadir nada al trust store.
#
# Pero es una CLAVE DISTINTA de la del emisor, al revés que en el despliegue de
# referencia de EUDI, donde el servicio de listas firma con el propio
# `PID-DS-0001_XX.pem`. Compartirla haría que comprometer el servicio de listas
# —que expone un endpoint público de lectura— entregase la clave con la que se
# emiten PIDs. Separarlas no cuesta nada y acota el daño.
#
# No lleva el EKU de mdoc: no firma una credencial, firma una lista de estado.

perfil_campo() {   # perfil_campo <slug> <n>
    local slug="$1" n="$2" linea
    for linea in "${PERFILES[@]}"; do
        [ "${linea%%|*}" = "$slug" ] || continue
        echo "$linea" | cut -d'|' -f"$n"
        return 0
    done
    return 1
}

subca_campo() {    # subca_campo <slug> <n>
    local slug="$1" n="$2" linea
    for linea in "${SUBCAS[@]}"; do
        [ "${linea%%|*}" = "$slug" ] || continue
        echo "$linea" | cut -d'|' -f"$n"
        return 0
    done
    return 1
}
