"""Emision de certificados con la sub-CA QTSP del ecosistema (`pki/subca/qtsp`).

Reutiliza el mismo patron de `srv-tl-publisher/pki-servicio/emision.py`: `cryptography` firma el
certificado y la emision se anota en el `index.txt` de la sub-CA con el formato de `openssl ca`,
de modo que la CRL y OCSP del servicio de la PKI la ven sin ningun cambio.

Las claves de los usuarios NO salen de aqui: se generan en este servicio, se guardan cifradas y solo
firman cuando una autorizacion valida lo pide (firma remota: la clave nunca esta en el telefono).
"""

from __future__ import annotations

import base64
import datetime as dt
import fcntl
import os
from dataclasses import dataclass
from pathlib import Path

from cryptography import x509
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from cryptography.x509.oid import AuthorityInformationAccessOID, ExtendedKeyUsageOID, NameOID

from . import config

DIAS_USUARIO = 825
DIAS_TSA = 825


class CAError(Exception):
    pass


@dataclass
class Autoridad:
    dir_ca: Path
    cert: x509.Certificate
    clave: object

    @property
    def cadena_pem(self) -> bytes:
        """Sub-CA + raiz, para que quien valide una firma reconstruya la cadena sin ir a buscarla."""
        cadena = self.cert.public_bytes(serialization.Encoding.PEM)
        raiz = config.DIR_PKI_PUBLICO / "ca" / "root.crt"
        if raiz.exists():
            cadena += raiz.read_bytes()
        return cadena


def cargar_autoridad() -> Autoridad:
    cert_pem = (config.DIR_CA / "certs" / "ca.cert.pem").read_bytes()
    clave_pem = (config.DIR_CA / "private" / "ca.key.pem").read_bytes()
    return Autoridad(
        dir_ca=config.DIR_CA,
        cert=x509.load_pem_x509_certificate(cert_pem),
        clave=serialization.load_pem_private_key(clave_pem, password=None),
    )


# ── Custodia de claves ────────────────────────────────────────────────────────

def _fernet() -> Fernet:
    """Llave maestra del almacen: se crea una vez en el volumen de datos (0600).

    En produccion esto seria un HSM; para el simulador basta con que las claves de los usuarios no
    queden en claro en la base de datos.
    """
    ruta = config.DIR_DATOS / "llave-maestra.key"
    if not ruta.exists():
        ruta.parent.mkdir(parents=True, exist_ok=True)
        fd = os.open(ruta, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, "wb") as f:
            f.write(Fernet.generate_key())
    return Fernet(ruta.read_bytes())


def cifrar_clave(clave) -> bytes:
    pem = clave.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    )
    return _fernet().encrypt(pem)


def descifrar_clave(blob: bytes):
    return serialization.load_pem_private_key(_fernet().decrypt(blob), password=None)


# ── Numeracion e index.txt ────────────────────────────────────────────────────

def _siguiente_serial(dir_ca: Path) -> int:
    with open(dir_ca / "serial", "r+") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        actual = int(f.read().strip(), 16)
        f.seek(0)
        f.write(f"{actual + 1:X}\n")
        f.truncate()
        return actual


def _formato_index(fecha: dt.datetime) -> str:
    return fecha.strftime("%y%m%d%H%M%S") + "Z"


def _anotar(ca: Autoridad, serie: int, caducidad: dt.datetime, sujeto: x509.Name, cert_pem: bytes) -> None:
    sujeto_txt = "/" + sujeto.rfc4514_string().replace(", ", "/").replace(",", "/")
    linea = f"V\t{_formato_index(caducidad)}\t\t{serie:X}\tunknown\t{sujeto_txt}\n"
    with open(ca.dir_ca / "index.txt", "a") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        f.write(linea)
    nuevos = ca.dir_ca / "newcerts"
    nuevos.mkdir(exist_ok=True)
    (nuevos / f"{serie:X}.pem").write_bytes(cert_pem)


def _extensiones_comunes(builder, ca: Autoridad, clave_publica):
    return (
        builder.add_extension(x509.BasicConstraints(ca=False, path_length=None), critical=True)
        .add_extension(x509.SubjectKeyIdentifier.from_public_key(clave_publica), critical=False)
        .add_extension(
            x509.AuthorityKeyIdentifier.from_issuer_public_key(ca.cert.public_key()), critical=False
        )
        .add_extension(
            x509.AuthorityInformationAccess([
                x509.AccessDescription(
                    AuthorityInformationAccessOID.OCSP,
                    x509.UniformResourceIdentifier(f"{config.URL_PKI_REVOCACION}/ocsp"),
                ),
                x509.AccessDescription(
                    AuthorityInformationAccessOID.CA_ISSUERS,
                    x509.UniformResourceIdentifier(f"{config.URL_PKI_REVOCACION}/ca/qtsp.crt"),
                ),
            ]),
            critical=False,
        )
        .add_extension(
            x509.CRLDistributionPoints([
                x509.DistributionPoint(
                    full_name=[x509.UniformResourceIdentifier(f"{config.URL_PKI_REVOCACION}/crl/qtsp.crl")],
                    relative_name=None, reasons=None, crl_issuer=None,
                )
            ]),
            critical=False,
        )
    )


def _key_usage(**activos) -> x509.KeyUsage:
    base = dict(
        digital_signature=False, content_commitment=False, key_encipherment=False,
        data_encipherment=False, key_agreement=False, key_cert_sign=False,
        crl_sign=False, encipher_only=False, decipher_only=False,
    )
    base.update(activos)
    return x509.KeyUsage(**base)


# ── Certificados ──────────────────────────────────────────────────────────────

def generar_clave_usuario(tipo: str | None = None):
    """Clave de firma del usuario: ECDSA P-256 (la del resto del ecosistema) o RSA 2048.

    RSA 2048 es lo que cubre el alcance acreditado de Andes SCD (ver PLAN-AUTENTICAR-Y-FIRMAR §4), pero la
    libreria nativa de la wallet recorta sus firmas a 128 bytes al incrustarlas en el PDF: ver config.TIPO_CLAVE.
    """
    if (tipo or config.TIPO_CLAVE) == "rsa":
        return rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return ec.generate_private_key(ec.SECP256R1())


def revocacion_por_http(cert: x509.Certificate) -> bool:
    """True si el certificado anuncia su CRL con la direccion de revocacion actual (config.URL_PKI_REVOCACION)."""
    try:
        puntos = cert.extensions.get_extension_for_class(x509.CRLDistributionPoints).value
    except x509.ExtensionNotFound:
        return False
    uris = [n.value for p in puntos for n in (p.full_name or []) if isinstance(n, x509.UniformResourceIdentifier)]
    return any(u.startswith(config.URL_PKI_REVOCACION) for u in uris)


def tipo_de_clave(clave) -> str:
    return "ec" if isinstance(clave, ec.EllipticCurvePrivateKey | ec.EllipticCurvePublicKey) else "rsa"


def revocar_certificado(ca: Autoridad, serie_hex: str, motivo: str = "superseded") -> None:
    """Marca una serie como revocada en `index.txt` (mismo formato que `openssl ca`).

    No regenera la CRL: el servicio de la PKI la rehace desde `index.txt` cada pocas horas.
    """
    serie = int(serie_hex, 16)
    index = ca.dir_ca / "index.txt"
    with open(index, "r+") as f:
        fcntl.flock(f, fcntl.LOCK_EX)
        ahora = dt.datetime.now(dt.timezone.utc)
        nuevas = []
        for linea in f.read().splitlines():
            campos = linea.split("\t")
            if len(campos) >= 6 and campos[0].strip() == "V" and int(campos[3], 16) == serie:
                campos[0] = "R"
                campos[2] = f"{_formato_index(ahora)},{motivo}"
                linea = "\t".join(campos)
            nuevas.append(linea)
        f.seek(0)
        f.write("\n".join(nuevas) + "\n")
        f.truncate()


def emitir_certificado_usuario(ca: Autoridad, clave_publica, nombre: str, documento: str) -> tuple[bytes, str]:
    """Perfil `qtsp-user` (pki/perfiles.sh): keyUsage critical nonRepudiation, 825 dias.

    Devuelve (cert_pem, serie_hex).
    """
    ahora = dt.datetime.now(dt.timezone.utc)
    caducidad = ahora + dt.timedelta(days=DIAS_USUARIO)
    serie = _siguiente_serial(ca.dir_ca)
    sujeto = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "CO"),
        x509.NameAttribute(NameOID.SERIAL_NUMBER, f"CC{documento}"),
        x509.NameAttribute(NameOID.COMMON_NAME, nombre),
    ])
    builder = (
        x509.CertificateBuilder()
        .subject_name(sujeto)
        .issuer_name(ca.cert.subject)
        .public_key(clave_publica)
        .serial_number(serie)
        .not_valid_before(ahora - dt.timedelta(hours=config.RETROCESO_CERT_HORAS))
        .not_valid_after(caducidad)
    )
    builder = _extensiones_comunes(builder, ca, clave_publica)
    builder = builder.add_extension(_key_usage(content_commitment=True), critical=True)
    builder = builder.add_extension(
        x509.CertificatePolicies([
            x509.PolicyInformation(x509.ObjectIdentifier(config.OID_POLITICA_USUARIO), None)
        ]),
        critical=False,
    )
    cert = builder.sign(private_key=ca.clave, algorithm=hashes.SHA256())
    cert_pem = cert.public_bytes(serialization.Encoding.PEM)
    _anotar(ca, serie, caducidad, sujeto, cert_pem)
    return cert_pem, f"{serie:X}"


def emitir_certificado_tsa(ca: Autoridad, clave_publica) -> tuple[bytes, str]:
    """Certificado de la autoridad de sellado de tiempo: EKU timeStamping critical (RFC 3161 §2.3)."""
    ahora = dt.datetime.now(dt.timezone.utc)
    caducidad = ahora + dt.timedelta(days=DIAS_TSA)
    serie = _siguiente_serial(ca.dir_ca)
    sujeto = x509.Name([
        x509.NameAttribute(NameOID.COUNTRY_NAME, "CO"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, config.NOMBRE_PRESTADOR),
        x509.NameAttribute(NameOID.COMMON_NAME, "Estampado cronologico (simulador)"),
    ])
    builder = (
        x509.CertificateBuilder()
        .subject_name(sujeto)
        .issuer_name(ca.cert.subject)
        .public_key(clave_publica)
        .serial_number(serie)
        .not_valid_before(ahora - dt.timedelta(hours=config.RETROCESO_CERT_HORAS))
        .not_valid_after(caducidad)
    )
    builder = _extensiones_comunes(builder, ca, clave_publica)
    builder = builder.add_extension(_key_usage(digital_signature=True), critical=True)
    builder = builder.add_extension(
        x509.ExtendedKeyUsage([ExtendedKeyUsageOID.TIME_STAMPING]), critical=True
    )
    cert = builder.sign(private_key=ca.clave, algorithm=hashes.SHA256())
    cert_pem = cert.public_bytes(serialization.Encoding.PEM)
    _anotar(ca, serie, caducidad, sujeto, cert_pem)
    return cert_pem, f"{serie:X}"


def clave_tsa_nueva():
    return ec.generate_private_key(ec.SECP256R1())


def b64_der(cert_pem: bytes) -> str:
    return base64.b64encode(
        x509.load_pem_x509_certificate(cert_pem).public_bytes(serialization.Encoding.DER)
    ).decode()
