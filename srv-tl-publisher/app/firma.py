"""Firma XAdES de las Trusted Lists.

Usa el MISMO firmante que el TLP: la hoja que emite la `Civica TL Signing CA`.
Que ambos firmen con la misma clave es lo correcto — quien valide ve un único
firmante de trusted lists para este esquema, no dos identidades distintas
diciendo lo mismo.
"""

import hashlib

from cryptography import x509
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec, rsa
from lxml import etree
from signxml import algorithms, methods
from signxml.xades import XAdESDataObjectFormat, XAdESSigner

import config


def _cargar_cert():
    datos = config.CERT_FIRMA.read_bytes()
    try:
        return x509.load_pem_x509_certificate(datos)
    except ValueError:
        return x509.load_der_x509_certificate(datos)


def _cargar_clave():
    datos = config.CLAVE_FIRMA.read_bytes()
    try:
        return serialization.load_pem_private_key(datos, password=None)
    except ValueError:
        return serialization.load_der_private_key(datos, password=None)


def metodo(clave):
    """El algoritmo se deduce de la clave, nunca se fija.

    Es la misma decisión que en el Parche 8 del TLP, y por la misma razón: tener
    dos sitios que puedan discrepar sobre qué clave hay instalada era
    exactamente el bug que impedía firmar.
    """
    if isinstance(clave, ec.EllipticCurvePrivateKey):
        return algorithms.SignatureMethod.ECDSA_SHA256
    if isinstance(clave, rsa.RSAPrivateKey):
        return algorithms.SignatureMethod.RSA_SHA256
    raise RuntimeError(f"tipo de clave no soportado: {type(clave).__name__}")


def identidad_firmante():
    """Huella y número de serie del firmante, para el registro de publicación.

    Se guardan en `published_lists` para poder responder más tarde a "¿con qué
    certificado se firmó la secuencia 7?" sin tener que abrir el XML.
    """
    cert = _cargar_cert()
    return (
        hashlib.sha256(cert.public_bytes(serialization.Encoding.DER)).hexdigest(),
        format(cert.serial_number, "x"),
    )


def firmar(raiz):
    """Devuelve los bytes del XML firmado con XAdES, envuelto (enveloped).

    LA CADENA NO VA DENTRO DE LA FIRMA, y no es un olvido. `signxml` escribe una
    entrada `<xades:Cert>` en `SigningCertificateV2` por CADA certificado que
    reciba, pero al verificar compara todas esas entradas contra el primer
    certificado del `KeyInfo`. Metiendo la cadena, la firma que produce este
    código la rechaza su propio verificador con `InvalidDigest`.

    Quien valide necesita la sub-CA, claro. La obtiene por la extensión AIA
    `caIssuers` de la propia hoja, que apunta a /pki/ca/tl.crt y responde. Para
    eso existe AIA.
    """
    clave = _cargar_clave()
    cert_pem = _cargar_cert().public_bytes(serialization.Encoding.PEM)

    firmante = XAdESSigner(
        claimed_roles=["scheme operator"],
        data_object_format=XAdESDataObjectFormat(
            Description="Trusted List", MimeType="application/vnd.etsi.tsl+xml"
        ),
        c14n_algorithm="http://www.w3.org/TR/2001/REC-xml-c14n-20010315",
        signature_algorithm=metodo(clave),
        method=methods.enveloped,
    )
    firmado = firmante.sign(data=raiz, key=clave, cert=cert_pem)
    return etree.tostring(
        etree.ElementTree(firmado), encoding="utf-8", xml_declaration=True
    )
