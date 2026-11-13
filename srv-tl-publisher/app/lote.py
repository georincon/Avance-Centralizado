"""La lista como LoTE (ETSI TS 119 602), el formato que lee la wallet.

POR QUE EXISTE

La wallet Android (SDK EUDI, librería `etsi-119602`) no lee el XML de la TSL: descarga
`/TL/<lista>/current.json`, verifica su firma y espera en el payload la clave `LoTE` con
`ListAndSchemeInformation` y `TrustedEntitiesList`. Sin esa clave falla con
`FailedToParseJwt` y, para un verifier, el resultado es «Presentación bloqueada»
(`Untrusted x5c`): la wallet se queda sin ancla para el certificado de acceso.

El espejo JWS ya existía (`espejo.py`) con un JSON propio. Aquí se le AÑADE `LoTE`, sin quitar
nada: el parser de la wallet ignora las claves que no conoce (`ignoreUnknownKeys`), así que
los consumidores del formato anterior siguen funcionando.

DE DONDE SALE

Del mismo XML firmado que publica el TLP, no de la base de datos: es lo que hace que el
espejo no pueda describir otra cosa que el XML (ver `espejo.py`).

QUE TIENE QUE CUMPLIR PARA QUE LA WALLET CONFIE

La librería saca los anclas de los servicios cuyo `ServiceTypeIdentifier` es EXACTAMENTE el
URI que su perfil UE asigna al contexto (p. ej. `.../SvcType/WRPAC/Issuance` para el
certificado de acceso de una parte usuaria). Los tipos del XML (`.../Svctype/CA/RPaccess`)
son los de ETSI TS 119 612 y no coinciden, por eso se traducen aquí (`_TIPO_DE_SERVICIO`).
"""

from datetime import datetime

import base64

from cryptography import x509
from lxml import etree

TSL = "http://uri.etsi.org/02231/v2#"
XML = "http://www.w3.org/XML/1998/namespace"
DS = "http://www.w3.org/2000/09/xmldsig#"

_BASE = "http://uri.etsi.org/19602"

# Perfil UE de cada lista: (LoTEType, StatusDeterminationApproach, tipo de servicio de
# emision, tipo de servicio de revocacion/estado). QEAA no tiene perfil UE: se deja lo que
# trae el XML.
_PERFIL = {
    "PID": (f"{_BASE}/LoTEType/EUPIDProvidersList",
            f"{_BASE}/PIDProvidersList/StatusDetn/EU",
            f"{_BASE}/SvcType/PID/Issuance", f"{_BASE}/SvcType/PID/Revocation"),
    "WP": (f"{_BASE}/LoTEType/EUWalletProvidersList",
           f"{_BASE}/WalletProvidersList/StatusDetn/EU",
           f"{_BASE}/SvcType/WalletSolution/Issuance", f"{_BASE}/SvcType/WalletSolution/Revocation"),
    "RPREG": (f"{_BASE}/LoTEType/EUWRPACProvidersList",
              f"{_BASE}/WRPACProvidersList/StatusDetn/EU",
              f"{_BASE}/SvcType/WRPAC/Issuance", None),
    "PUBEAA": (f"{_BASE}/LoTEType/EUPubEAAProvidersList",
               f"{_BASE}/PubEAAProvidersList/StatusDetn/EU",
               f"{_BASE}/SvcType/PubEAA/Issuance", f"{_BASE}/SvcType/PubEAA/Revocation"),
}


def _q(*nombres):
    return "/".join(f"{{{TSL}}}{n}" for n in nombres)


def _lang(nodo):
    return nodo.get(f"{{{XML}}}lang", "en")


def _textos(nodo, *ruta):
    """[{lang, value}] de los `Name` bajo la ruta."""
    return [{"lang": _lang(e), "value": e.text}
            for e in nodo.findall(_q(*ruta)) if e.text]


def _uris(nodo, *ruta):
    """[{lang, uriValue}] de los `URI` bajo la ruta."""
    return [{"lang": _lang(e), "uriValue": e.text}
            for e in nodo.findall(_q(*ruta)) if e.text]


def _direccion_postal(nodo):
    salida = []
    for p in nodo.findall(_q("PostalAddresses", "PostalAddress")):
        d = {"lang": _lang(p),
             "StreetAddress": (p.findtext(_q("StreetAddress")) or "-"),
             "Country": (p.findtext(_q("CountryName")) or "CO")}
        for clave, tag in (("Locality", "Locality"), ("StateOrProvince", "StateOrProvince"),
                           ("PostalCode", "PostalCode")):
            valor = p.findtext(_q(tag))
            if valor:
                d[clave] = valor
        salida.append(d)
    return salida


def _fecha(texto):
    """LoTEDateTime: ISO 8601 con zona. El XML ya trae `...Z`."""
    if not texto:
        return None
    return datetime.fromisoformat(texto.replace("Z", "+00:00")).strftime("%Y-%m-%dT%H:%M:%SZ")


def _identidad(si):
    """Los `DigitalId` del XML (uno por certificado, nombre y SKI) en un solo ServiceDigitalIdentity."""
    ident = si.find(_q("ServiceDigitalIdentity"))
    certificados, nombres, skis = [], [], []
    if ident is not None:
        for d in ident.findall(_q("DigitalId")):
            for c in d.findall(_q("X509Certificate")):
                if c.text:
                    certificados.append({"val": c.text.strip()})
            for n in d.findall(_q("X509SubjectName")):
                if n.text:
                    nombres.append(n.text)
            for k in d.findall(_q("X509SKI")):
                if k.text:
                    skis.append(k.text.strip())
    salida = {}
    if certificados:
        salida["X509Certificates"] = certificados
    if nombres:
        salida["X509SubjectNames"] = nombres
    if skis:
        salida["X509SKIs"] = skis
    return salida


def _es_de_estado(nombre):
    """Los firmantes de listas de estado (status lists) son el servicio de revocacion."""
    n = (nombre or "").lower()
    return "estado" in n or "status" in n


# Las listas cuyo perfil UE dice que los servicios se identifican por CA (la de WRPAC): una hoja
# (el certificado de acceso de un verifier concreto, que `POST /verifiers` registra como servicio)
# no es un ancla y no debe ir en el LoTE. El XML la conserva: es el artefacto conforme.
_SOLO_CA = {"RPREG"}


def _es_ca(der_b64):
    try:
        cert = x509.load_der_x509_certificate(base64.b64decode(der_b64))
        return cert.extensions.get_extension_for_class(x509.BasicConstraints).value.ca
    except (ValueError, x509.ExtensionNotFound):
        return False


def _servicio(si, slug):
    perfil = _PERFIL.get(slug)
    tipo_xml = si.findtext(_q("ServiceTypeIdentifier"))
    nombres = _textos(si, "ServiceName", "Name")
    tipo = tipo_xml
    if perfil:
        emision, revocacion = perfil[2], perfil[3]
        nombre = nombres[0]["value"] if nombres else ""
        tipo = revocacion if (revocacion and _es_de_estado(nombre)) else emision

    info = {
        "ServiceName": nombres,
        "ServiceDigitalIdentity": _identidad(si),
        "ServiceTypeIdentifier": tipo,
        "ServiceStatus": si.findtext(_q("ServiceStatus")),
        "StatusStartingTime": _fecha(si.findtext(_q("StatusStartingTime"))),
    }
    definicion = _uris(si, "SchemeServiceDefinitionURI", "URI")
    if definicion:
        info["SchemeServiceDefinitionURI"] = definicion
    puntos = [{"uriValue": p.text} for p in si.findall(_q("ServiceSupplyPoints", "ServiceSupplyPoint")) if p.text]
    if puntos:
        info["ServiceSupplyPoints"] = puntos
    if slug in _SOLO_CA:
        certificados = [c for c in info["ServiceDigitalIdentity"].get("X509Certificates", []) if _es_ca(c["val"])]
        if not certificados:
            return None
        info["ServiceDigitalIdentity"] = {"X509Certificates": certificados}
    return {"ServiceInformation": {k: v for k, v in info.items() if v}}


def _entidad(tsp, slug):
    tspi = tsp.find(_q("TSPInformation"))
    nombre = _textos(tspi, "TSPName", "Name")
    direccion = tspi.find(_q("TSPAddress"))
    informacion = {
        "TEName": nombre,
        "TEAddress": {
            "TEPostalAddress": _direccion_postal(direccion),
            "TEElectronicAddress": _uris(direccion, "ElectronicAddress", "URI"),
        },
        "TEInformationURI": _uris(tspi, "TSPInformationURI", "URI"),
    }
    comercial = _textos(tspi, "TSPTradeName", "Name")
    if comercial:
        informacion["TETradeName"] = comercial
    servicios = [x for x in (_servicio(si, slug)
                             for si in tsp.findall(_q("TSPServices", "TSPService", "ServiceInformation")))
                 if x]
    if not servicios:
        return None
    return {"TrustedEntityInformation": informacion, "TrustedEntityServices": servicios}


def construir(xml: bytes, slug: str, secuencia: int) -> dict:
    """El objeto `LoTE` de la lista publicada en `xml` (la TSL firmada), para el payload del JWS."""
    raiz = etree.fromstring(xml)
    si = raiz.find(_q("SchemeInformation"))
    perfil = _PERFIL.get(slug)

    operador = si.find(_q("SchemeOperatorAddress"))
    notas = [{"LoTELegalNotice": e.text} for e in si.findall(_q("PolicyOrLegalNotice", "TSLLegalNotice")) if e.text]

    esquema = {
        "LoTEVersionIdentifier": 1,
        "LoTESequenceNumber": int(secuencia),
        "LoTEType": perfil[0] if perfil else si.findtext(_q("TSLType")),
        "SchemeOperatorName": _textos(si, "SchemeOperatorName", "Name"),
        "SchemeOperatorAddress": {
            "SchemeOperatorPostalAddress": _direccion_postal(operador),
            "SchemeOperatorElectronicAddress": _uris(operador, "ElectronicAddress", "URI"),
        },
        "SchemeName": _textos(si, "SchemeName", "Name"),
        "SchemeInformationURI": _uris(si, "SchemeInformationURI", "URI"),
        "StatusDeterminationApproach": perfil[1] if perfil else si.findtext(_q("StatusDeterminationApproach")),
        "SchemeTypeCommunityRules": _uris(si, "SchemeTypeCommunityRules", "URI"),
        "SchemeTerritory": si.findtext(_q("SchemeTerritory")),
        "PolicyOrLegalNotice": notas or [{"LoTELegalNotice": "Lista de confianza de pruebas"}],
        "ListIssueDateTime": _fecha(si.findtext(_q("ListIssueDateTime"))),
        "NextUpdate": _fecha(si.findtext(_q("NextUpdate", "dateTime"))),
    }
    puntos = [e.text for e in si.findall(_q("DistributionPoints", "URI")) if e.text]
    if puntos:
        esquema["DistributionPoints"] = puntos

    entidades = [e for e in (_entidad(tsp, slug)
                             for tsp in raiz.findall(_q("TrustServiceProviderList", "TrustServiceProvider")))
                 if e]
    lote = {"ListAndSchemeInformation": esquema}
    if entidades:
        lote["TrustedEntitiesList"] = entidades
    return lote
