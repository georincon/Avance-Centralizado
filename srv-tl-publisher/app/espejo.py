"""Espejo JWS de una Trusted List publicada (Fase 1.8).

QUE ES Y QUE NO ES

El artefacto **conforme** es el XML con firma XAdES: es lo que valida un
verificador externo como DSS, y lo que exige ETSI TS 119 612. El espejo JWS no
lo sustituye ni compite con él: es una **vista derivada** para el dispositivo.

Por qué hace falta: la wallet corre en un teléfono y tendría que traerse un
parser de XML, canonicalización c14n y verificación XAdES para leer un XML
firmado. Todo eso ya lo tiene resuelto para JWS, porque es lo que usa en
OpenID4VCI y OpenID4VP. Pedirle además una pila de XMLDSig es superficie de
ataque y peso de APK a cambio de nada.

LO QUE HACE QUE ESTO NO SEA UNA SEGUNDA FUENTE DE VERDAD

Tres cosas, y las tres importan:

  1. Se genera en el MISMO acto de publicación que el XML, de la misma fila de
     base de datos. No hay un proceso aparte que pueda quedarse atrás.
  2. Lleva el `xml_sha256` DENTRO del payload firmado. Cualquiera puede
     descargar el XML, calcular su sha256 y comprobar que los dos artefactos
     describen el mismo estado. Si alguien manipulara uno de los dos, la
     comparación lo delata.
  3. Se firma con LA MISMA CLAVE que el XAdES, así que no aparece una segunda
     identidad firmante en el ecosistema.

Aquí la cadena SI va dentro, en el `x5c` de la cabecera. A diferencia de XAdES
—donde meterla rompe la verificación de `SigningCertificateV2`— en JWS el `x5c`
es exactamente el sitio previsto para la cadena, y que el artefacto sea
autocontenido le ahorra a la wallet una petición de red para resolver AIA.
"""

import base64
import hashlib
import json
from datetime import datetime, timezone

import jwt
from cryptography import x509
from cryptography.hazmat.primitives import serialization

import anclas
import config
import firma
import lote

TIPO = "tl+jwt"


def _cadena_x5c():
    """`x5c` = hoja primero, luego la sub-CA. La raíz NO va.

    Es el ancla de confianza: tiene que estar ya instalada en quien valida, no
    viajar dentro de lo que se valida. Un `x5c` que incluya la raíz invita a
    confiar en la raíz que viene en el propio mensaje, que es exactamente el
    error que hace inútil una cadena.
    """
    hoja = firma._cargar_cert().public_bytes(serialization.Encoding.DER)
    cadena = [hoja]

    fichero = config.CERT_FIRMA.parent / (config.CERT_FIRMA.stem + "-chain.pem")
    if fichero.is_file():
        for cert in x509.load_pem_x509_certificates(fichero.read_bytes()):
            der = cert.public_bytes(serialization.Encoding.DER)
            # El fichero de cadena empieza por la hoja: se compara en DER para
            # no repetirla.
            if der not in cadena:
                cadena.append(der)

    return [base64.b64encode(der).decode() for der in cadena]


def construir(*, esquema, secuencia, emision, proxima_actualizacion,
              xml_bytes, xml_sha256):
    """Devuelve el JWS compacto (bytes) del espejo de esta publicación."""
    servicios = anclas.extraer(xml_bytes)
    info = anclas.informacion_del_esquema(xml_bytes)

    payload = {
        "iss": f"{config.BASE_URL}/tl-api/v1",
        "sub": esquema["scheme_slug"],
        "iat": int(emision.replace(tzinfo=timezone.utc).timestamp()),
        # El JWS caduca exactamente cuando caduca la lista. Que un consumidor
        # pueda seguir usando el espejo despues de que el XML haya vencido seria
        # abrir una via para trabajar con datos de confianza caducados.
        "exp": int(proxima_actualizacion.replace(tzinfo=timezone.utc).timestamp()),
        "scheme": esquema["scheme_slug"],
        "scheme_name": esquema["scheme_name"],
        "tsl_type": info["tsl_type"],
        "sequence_number": int(secuencia),
        "version": int(info["version"]),
        "territory": info["territory"],
        "issue_date": info["issue_date"],
        "next_update": info["next_update"],
        # El vinculo con el artefacto conforme.
        "xml_uri": f"{config.BASE_URL}/TL/{esquema['scheme_slug']}/{secuencia}.xml",
        "xml_sha256": xml_sha256,
        "services": servicios,
    }

    # El formato que lee la wallet (ETSI TS 119 602): se AÑADE `LoTE`, sin quitar nada de lo
    # anterior (la wallet ignora las claves que no conoce). La LOTL no lo lleva: la wallet no
    # la usa, lee cada lista por su URL. Ver lote.py.
    if esquema["scheme_slug"] != "LOTL":
        payload["LoTE"] = lote.construir(xml_bytes, esquema["scheme_slug"], secuencia)

    # Sólo la LOTL lleva punteros. El XML ya los tiene (PointersToOtherTSL);
    # esto es la MISMA información en el espejo JSON, para que un consumidor
    # (la wallet) pueda seguir LOTL -> esquema sin un parser de XML, que es
    # justo lo que el espejo existe para evitar (Fase 6.1).
    if esquema["scheme_slug"] == "LOTL":
        payload["pointers"] = [
            dict(scheme=p["xml_uri"].rstrip("/").split("/")[-2],
                 json_uri=p["xml_uri"][:-len(".xml")] + ".json")
            for p in anclas.punteros(xml_bytes)
        ]

    cabecera = {"typ": TIPO, "x5c": _cadena_x5c()}
    clave = firma._cargar_clave()
    alg = "ES256" if firma.metodo(clave).name.startswith("ECDSA") else "RS256"

    return jwt.encode(payload, clave, algorithm=alg, headers=cabecera).encode()


def ruta_de(ruta_xml):
    """El espejo vive junto a su XML, con la misma secuencia.

    No se guarda su ruta en `published_lists` a propósito: es una vista
    derivada, y una columna más sería estado que puede quedarse desincronizado
    del fichero real. Su integridad la garantizan su propia firma y el
    `xml_sha256` que lleva dentro, no una fila.
    """
    return ruta_xml.with_suffix(".jws")


def verificar(jws: bytes, xml: bytes):
    """Comprueba el espejo como lo haría un consumidor. Para las pruebas.

    Devuelve el payload si todo cuadra; lanza si no.
    """
    cabecera = jwt.get_unverified_header(jws)
    hoja = x509.load_der_x509_certificate(base64.b64decode(cabecera["x5c"][0]))
    payload = jwt.decode(
        jws, hoja.public_key(), algorithms=["ES256", "RS256"],
        options={"verify_aud": False},
    )
    esperado = hashlib.sha256(xml).hexdigest()
    if payload["xml_sha256"] != esperado:
        raise ValueError(
            "el espejo JWS y el XML NO describen el mismo estado: "
            f"el JWS dice {payload['xml_sha256']}, el XML es {esperado}"
        )
    return payload
