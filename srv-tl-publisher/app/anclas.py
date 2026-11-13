"""Extracción de trust anchors a partir de una Trusted List YA PUBLICADA.

POR QUE SE LEE EL XML Y NO LA BASE DE DATOS

Es la decisión que define este módulo. La base de datos tiene el estado que el
operador está editando ahora mismo; el XML publicado tiene el estado que se
firmó. Pueden diferir, y de hecho difieren durante toda la ventana entre un
cambio en el TLP y la siguiente publicación.

Una API de trust anchors que responda desde la base de datos estaría diciendo
"confía en esto" sobre algo que NADIE HA FIRMADO. Leyendo el XML publicado, lo
que devuelve esta API es exactamente lo que devuelve la lista firmada, y quien
quiera comprobarlo puede descargar el XML y verificar la firma.
"""

import base64
import hashlib

from cryptography import x509
from cryptography.hazmat.primitives import serialization
from lxml import etree

TSL = "http://uri.etsi.org/02231/v2#"
XML = "http://www.w3.org/XML/1998/namespace"


def _q(*nombres):
    """Ruta con el namespace de TSL en cada segmento."""
    return "/".join(f"{{{TSL}}}{n}" for n in nombres)


def _texto(nodo, *nombres):
    e = nodo.find(_q(*nombres))
    return e.text if e is not None else None


def _multilang(nodo, *nombres):
    return [dict(lang=e.get(f"{{{XML}}}lang", "en"), text=e.text)
            for e in nodo.findall(_q(*nombres))]


def _url_de_extension(cert, clase, oid=None):
    """Saca la CRL o el OCSP de las extensiones del propio certificado.

    Se leen del certificado y no de la lista a propósito: son las URLs que ese
    certificado promete, y por tanto las únicas que un validador estricto va a
    usar. Si la lista dijera otra cosa, mandaría el certificado.
    """
    try:
        ext = cert.extensions.get_extension_for_class(clase).value
    except x509.ExtensionNotFound:
        return None
    if clase is x509.CRLDistributionPoints:
        for punto in ext:
            for nombre in punto.full_name or []:
                if isinstance(nombre, x509.UniformResourceIdentifier):
                    return nombre.value
        return None
    for descripcion in ext:
        if descripcion.access_method == oid:
            return descripcion.access_location.value
    return None


def _certificado(der_b64):
    der = base64.b64decode(der_b64)
    cert = x509.load_der_x509_certificate(der)
    try:
        ski = base64.b64encode(
            cert.extensions.get_extension_for_class(x509.SubjectKeyIdentifier).value.digest
        ).decode()
    except x509.ExtensionNotFound:
        ski = None
    return dict(
        der_b64=der_b64,
        subject=cert.subject.rfc4514_string(),
        issuer=cert.issuer.rfc4514_string(),
        serial=format(cert.serial_number, "x"),
        sha256=hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest(),
        ski=ski,
        not_before=cert.not_valid_before_utc.isoformat(),
        not_after=cert.not_valid_after_utc.isoformat(),
        crl_url=_url_de_extension(cert, x509.CRLDistributionPoints),
        ocsp_url=_url_de_extension(
            cert, x509.AuthorityInformationAccess, x509.oid.AuthorityInformationAccessOID.OCSP
        ),
    )


def extraer(xml: bytes, estado=None):
    """Los servicios de una TSL publicada, en JSON.

    `estado` filtra por el sufijo del `ServiceStatus` (p. ej. `granted`).
    """
    raiz = etree.fromstring(xml)
    servicios = []

    for si in raiz.iter(f"{{{TSL}}}ServiceInformation"):
        service_status = _texto(si, "ServiceStatus")
        if estado and not (service_status or "").endswith("/" + estado):
            continue

        certificados = []
        for nodo in si.findall(
            _q("ServiceDigitalIdentity", "DigitalId", "X509Certificate")
        ):
            if nodo.text:
                certificados.append(_certificado(nodo.text.strip()))

        puntos = [p.text for p in si.findall(
            _q("ServiceSupplyPoints", "ServiceSupplyPoint"))]

        servicios.append(dict(
            service_type=_texto(si, "ServiceTypeIdentifier"),
            service_name=_multilang(si, "ServiceName", "Name"),
            status=service_status,
            status_start=_texto(si, "StatusStartingTime"),
            supply_points=puntos,
            certificates=certificados,
        ))
    return servicios


def punteros(xml: bytes):
    """Los `OtherTSLPointer` de una LOTL publicada — a qué otras listas apunta.

    Sólo tiene sentido llamarlo sobre el XML de la LOTL: es la única lista que
    lleva `PointersToOtherTSL`. Devuelve la URL del `.xml` tal como está en el
    puntero (es el artefacto conforme); quien construya el espejo JSON decide
    si la traduce a `.json`.
    """
    raiz = etree.fromstring(xml)
    si = raiz.find(f"{{{TSL}}}SchemeInformation")
    if si is None:
        return []
    salida = []
    for op in si.findall(_q("PointersToOtherTSL", "OtherTSLPointer")):
        location = _texto(op, "TSLLocation")
        if location:
            salida.append(dict(xml_uri=location))
    return salida


def informacion_del_esquema(xml: bytes):
    raiz = etree.fromstring(xml)
    si = raiz.find(f"{{{TSL}}}SchemeInformation")
    return dict(
        version=_texto(si, "TSLVersionIdentifier"),
        sequence_number=_texto(si, "TSLSequenceNumber"),
        tsl_type=_texto(si, "TSLType"),
        territory=_texto(si, "SchemeTerritory"),
        issue_date=_texto(si, "ListIssueDateTime"),
        next_update=_texto(si, "NextUpdate", "dateTime"),
    )
