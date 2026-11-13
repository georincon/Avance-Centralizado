"""Emisión y revocación bajo demanda de access certificates de relying parties.

POR QUÉ VIVE AQUÍ Y NO EN `rp-registry`
----------------------------------------
Este servicio YA carga en memoria la clave privada de la sub-CA `rp` — la
necesita para firmar la CRL (`regenerar_crl` en app.py). Añadir la emisión
aquí no mete esa clave en un segundo contenedor: reutiliza el mismo objeto
`Autoridad` que ya existe. Si esto viviera en `rp-registry` —que además recibe
CSRs de terceros y habla con una base de datos propia, es decir tiene más
superficie expuesta— la clave de la sub-CA tendría que montarse en DOS sitios
en vez de uno.

Es la única excepción al "no emite certificados" del resto de este servicio
(ver el docstring de app.py), y está acotada a un único perfil: `rp-access`.
No es un endpoint genérico de firma.

Se usa `cryptography` directamente, no `openssl ca` por subprocess: es la
misma librería con la que este servicio ya firma CRLs y respuestas OCSP, así
que no se añade una segunda vía de generar certificados que pudiera divergir.

El registro de lo emitido va al MISMO `index.txt` que ya leen
`estado_en_index()` y `cert_emitido()`, con el mismo formato de columnas que
escribe `openssl ca`. Es lo que hace que la CRL y OCSP vean estos certificados
sin ningún cambio en ese código.
"""

from __future__ import annotations

import datetime as dt
import fcntl
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.x509.oid import AuthorityInformationAccessOID, ExtendedKeyUsageOID

# Perfil `rp-access` de pki/perfiles.sh, reproducido aquí: este servicio no
# parsea bash. Si el perfil cambia allí, cambia aquí — son unas pocas líneas,
# y `scripts/probar-rp-registry.sh` lo detectaría si se desincronizan (el
# certificado emitido dejaría de llevar el mismo `certificatePolicies`).
DIAS_VALIDEZ = 825
OID_POLITICA = x509.ObjectIdentifier("2.25.224783951054833873364394138650481919266.1.6")
# mdocReaderAuth (ISO/IEC 18013-5): el SDK de la wallet lo exige en el certificado del
# verifier, también en OpenID4VP (ReaderTrustStore). Va junto a clientAuth.
OID_EKU_READER_AUTH = x509.ObjectIdentifier("1.0.18013.5.1.6")

MOTIVOS_VALIDOS = {
    "unspecified", "keyCompromise", "CACompromise", "affiliationChanged",
    "superseded", "cessationOfOperation", "certificateHold",
}


class EmisionError(Exception):
    """Error de solicitud (CSR inválido, motivo desconocido, serie inexistente).

    Se distingue de una excepción cualquiera para que el endpoint responda
    400 en vez de 500: es la petición la que está mal, no el servicio.
    """


def _bloqueo(dir_ca: Path):
    """Bloqueo de fichero sobre `serial`, para no repetir un número de serie
    si dos peticiones llegan a la vez. `openssl ca` no lo necesita porque es
    un proceso por invocación; un servicio HTTP concurrente sí.
    """
    return open(dir_ca / "serial", "r+")


def _siguiente_serial(dir_ca: Path) -> int:
    with _bloqueo(dir_ca) as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        actual = int(f.read().strip(), 16)
        f.seek(0)
        f.write(f"{actual + 1:X}\n")
        f.truncate()
        return actual


def _formato_index(fecha: dt.datetime) -> str:
    # openssl usa YYMMDDHHMMSSZ: dos dígitos de año, 'Z' literal (siempre UTC).
    return fecha.strftime("%y%m%d%H%M%S") + "Z"


def emitir_rp_access(ca, base_url: str, csr_pem: bytes, san_dns: str | None) -> tuple[bytes, bytes, str]:
    """Firma un access certificate a partir de un CSR ajeno.

    El RP genera su propia clave: en ningún punto de este código aparece una
    clave privada de un tercero, sólo la petición de firma que ya trae la
    clave pública dentro.

    Devuelve (cert_pem, fullchain_pem, serie_hex).
    """
    if not ca.puede_firmar:
        raise EmisionError("la sub-CA 'rp' no tiene su clave montada en este servicio")

    try:
        csr = x509.load_pem_x509_csr(csr_pem)
    except ValueError as e:
        raise EmisionError(f"CSR mal formado: {e}")
    if not csr.is_signature_valid:
        raise EmisionError("la firma del CSR no valida contra su propia clave pública")

    ahora = dt.datetime.now(dt.timezone.utc)
    caducidad = ahora + dt.timedelta(days=DIAS_VALIDEZ)
    serie = _siguiente_serial(ca.dir_ca)

    builder = (
        x509.CertificateBuilder()
        .subject_name(csr.subject)
        .issuer_name(ca.cert.subject)
        .public_key(csr.public_key())
        .serial_number(serie)
        .not_valid_before(ahora)
        .not_valid_after(caducidad)
        .add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.SubjectKeyIdentifier.from_public_key(csr.public_key()), critical=False)
        .add_extension(
            x509.AuthorityKeyIdentifier.from_issuer_public_key(ca.cert.public_key()),
            critical=False,
        )
        .add_extension(
            x509.KeyUsage(
                digital_signature=True, content_commitment=False, key_encipherment=False,
                data_encipherment=False, key_agreement=False, key_cert_sign=False,
                crl_sign=False, encipher_only=False, decipher_only=False,
            ),
            critical=True,
        )
        .add_extension(x509.ExtendedKeyUsage([ExtendedKeyUsageOID.CLIENT_AUTH, OID_EKU_READER_AUTH]), critical=True)
        .add_extension(
            x509.AuthorityInformationAccess([
                x509.AccessDescription(
                    AuthorityInformationAccessOID.OCSP,
                    x509.UniformResourceIdentifier(f"{base_url}/ocsp"),
                ),
                x509.AccessDescription(
                    AuthorityInformationAccessOID.CA_ISSUERS,
                    x509.UniformResourceIdentifier(f"{base_url}/ca/rp.crt"),
                ),
            ]),
            critical=False,
        )
        .add_extension(
            x509.CRLDistributionPoints([
                x509.DistributionPoint(
                    full_name=[x509.UniformResourceIdentifier(f"{base_url}/crl/rp.crl")],
                    relative_name=None, reasons=None, crl_issuer=None,
                )
            ]),
            critical=False,
        )
        .add_extension(
            x509.CertificatePolicies([x509.PolicyInformation(OID_POLITICA, None)]),
            critical=False,
        )
    )
    if san_dns:
        builder = builder.add_extension(
            x509.SubjectAlternativeName([x509.DNSName(san_dns)]), critical=False
        )

    cert = builder.sign(private_key=ca.clave, algorithm=hashes.SHA256())
    cert_pem = cert.public_bytes(serialization.Encoding.PEM)
    cadena_pem = cert_pem + ca.cert.public_bytes(serialization.Encoding.PEM)

    # El campo "sujeto" de index.txt NO lo lee ningún código de este servicio
    # (estado_en_index/cert_emitido sólo usan las columnas 0, 2 y 3): es sólo
    # para que un humano lea el fichero. No hace falta reproducir el formato
    # exacto de openssl para las abreviaturas de RDN.
    sujeto = "/" + csr.subject.rfc4514_string().replace(", ", "/").replace(",", "/")
    linea = f"V\t{_formato_index(caducidad)}\t\t{serie:X}\tunknown\t{sujeto}\n"
    with open(ca.dir_ca / "index.txt", "a") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        f.write(linea)

    nuevos = ca.dir_ca / "newcerts"
    nuevos.mkdir(exist_ok=True)
    (nuevos / f"{serie:X}.pem").write_bytes(cert_pem)

    return cert_pem, cadena_pem, f"{serie:X}"


def revocar_rp_access(ca, serie_hex: str, motivo: str) -> None:
    """Marca una serie como revocada en `index.txt`.

    Reescribe la línea correspondiente al formato `R\\t<caducidad>\\t
    <revocación>,<motivo>\\t<serie>\\t...` que ya lee `estado_en_index()`. No
    toca la CRL: quien llama es responsable de pedir su regeneración (en el
    servicio, `regenerar_crl(ca)` justo después).
    """
    if motivo not in MOTIVOS_VALIDOS:
        raise EmisionError(f"motivo desconocido: {motivo}")
    try:
        serie = int(serie_hex, 16)
    except ValueError:
        raise EmisionError(f"serie mal formada: {serie_hex}")

    index = ca.dir_ca / "index.txt"
    with open(index, "r+") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        lineas = f.read().splitlines(keepends=False)
        encontrada = False
        ahora = dt.datetime.now(dt.timezone.utc)
        nuevas = []
        for linea in lineas:
            campos = linea.split("\t")
            if len(campos) < 6:
                nuevas.append(linea)
                continue
            try:
                coincide = int(campos[3], 16) == serie
            except ValueError:
                coincide = False
            if not coincide:
                nuevas.append(linea)
                continue
            if campos[0].strip() == "R":
                raise EmisionError(f"la serie {serie_hex} ya está revocada")
            campos[0] = "R"
            campos[2] = f"{_formato_index(ahora)},{motivo}"
            nuevas.append("\t".join(campos))
            encontrada = True
        if not encontrada:
            raise EmisionError(f"no existe ningún certificado con serie {serie_hex}")
        f.seek(0)
        f.write("\n".join(nuevas) + "\n")
        f.truncate()
