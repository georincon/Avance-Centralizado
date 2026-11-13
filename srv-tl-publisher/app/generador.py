"""Generación del XML de una Trusted List conforme a ETSI TS 119 612 v2.1.1.

POR QUÉ NO SE REUTILIZA `xmlGen.py` NI SUS CLASES GENERADAS

El plan preveía reutilizar `trustedlists_api.py`, las clases que generateDS
produce a partir del XSD. Se descartó al implementarlo, por tres razones:

  1. Son 520 KB de código generado que vive dentro del clon del TLP, un
     directorio que no se versiona aquí. Meterlo en este servicio significaría
     o vendorizarlo entero o montar el clon dentro de otro contenedor.
  2. Su API obliga a rodeos que el propio upstream tuvo que hacer
     (`original_tagname_`, `set_valueOf_` sobre objetos anidados a mano). Las
     extensiones ETSI que hacen falta aquí están COMENTADAS en `xmlGen.py`
     precisamente porque construirlas con esas clases es incómodo.
  3. Lo que de verdad garantiza conformidad no es construir con clases
     derivadas del XSD, sino VALIDAR EL RESULTADO contra el XSD. Eso se hace
     abajo, y es una garantía más fuerte: comprueba el XML que se publica, no
     el camino por el que se construyó.

Construir con lxml son ~250 líneas y da control total sobre el orden de los
elementos, que en un XSD con `xsd:sequence` es la diferencia entre válido y no.
"""

import base64
from datetime import datetime, timezone

from lxml import etree

import config

TSL = "http://uri.etsi.org/02231/v2#"
ADDTYPES = "http://uri.etsi.org/02231/v2/additionaltypes#"
DS = "http://www.w3.org/2000/09/xmldsig#"
XADES = "http://uri.etsi.org/01903/v1.3.2#"
XML = "http://www.w3.org/XML/1998/namespace"

NSMAP = {None: TSL, "ds": DS, "xades": XADES}

# HistoricalInformationPeriod en días. 65535 es lo que usan las TL de la UE:
# en la práctica significa "no se purga el histórico".
PERIODO_HISTORICO = 65535

STATUS_DETERMINATION = "http://uri.etsi.org/TrstSvc/TrustedList/StatusDetn/EUappropriate"


def _t(padre, nombre, texto=None, ns=TSL, **attrs):
    e = etree.SubElement(padre, f"{{{ns}}}{nombre}", **attrs)
    if texto is not None:
        e.text = str(texto)
    return e


def _multilang(padre, contenedor, entradas, hijo, clave="text"):
    """Bloque multiidioma: `<Contenedor><Hijo xml:lang="es">…</Hijo>…</Contenedor>`.

    Es el patrón más repetido del formato. Si no hay entradas no se emite el
    contenedor: un `<SchemeName/>` vacío no valida contra `NonEmptyString`.
    """
    if not entradas:
        return None
    c = _t(padre, contenedor)
    for e in entradas:
        n = _t(c, hijo, e.get(clave))
        n.set(f"{{{XML}}}lang", e.get("lang", "en"))
    return c


def _fecha(dt):
    """`xsd:dateTime` en UTC con 'Z'.

    MySQL devuelve datetimes ingenuos. Marcarlos como UTC explícitamente evita
    que el `NextUpdate` de la lista quede desplazado por la zona horaria del
    contenedor, que es un fallo silencioso: la lista caduca antes o después de
    lo que dice el registro.
    """
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt.astimezone(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _direccion(padre, etiqueta, postales, electronicas):
    """`AddressType`: direcciones postales y electrónicas, en ese orden.

    El XSD las exige a las dos. Si la base de datos no trae ninguna se pone un
    marcador mínimo, porque el hueco haría fallar la validación entera y una
    lista sin dirección de contacto sigue siendo útil.
    """
    dir_ = _t(padre, etiqueta)
    lista = _t(dir_, "PostalAddresses")
    if not postales:
        postales = [dict(lang="en", StreetAddress="-", Locality="-",
                         CountryName=config.TERRITORIO)]
    for p in postales:
        pa = _t(lista, "PostalAddress")
        pa.set(f"{{{XML}}}lang", p.get("lang", "en"))
        _t(pa, "StreetAddress", p.get("StreetAddress") or "-")
        _t(pa, "Locality", p.get("Locality") or "-")
        if p.get("StateOrProvince"):
            _t(pa, "StateOrProvince", p["StateOrProvince"])
        if p.get("PostalCode"):
            _t(pa, "PostalCode", p["PostalCode"])
        _t(pa, "CountryName", p.get("CountryName") or config.TERRITORIO)

    ea = _t(dir_, "ElectronicAddress")
    if not electronicas:
        electronicas = [dict(lang="en", URI=f"{config.BASE_URL}/tlp/")]
    for e in electronicas:
        u = _t(ea, "URI", e.get("URI"))
        u.set(f"{{{XML}}}lang", e.get("lang", "en"))
    return dir_


def _servicio(padre, sv):
    """Un `TSPService`.

    El orden de los hijos de `ServiceInformation` lo fija el XSD y no es
    negociable: ServiceTypeIdentifier, ServiceName, ServiceDigitalIdentity,
    ServiceStatus, StatusStartingTime, SchemeServiceDefinitionURI?,
    ServiceSupplyPoints?, TSPServiceDefinitionURI?, ServiceInformationExtensions?
    """
    s = _t(padre, "TSPService")
    info = _t(s, "ServiceInformation")

    _t(info, "ServiceTypeIdentifier", sv["service_type"])
    _multilang(info, "ServiceName", sv["name"], "Name")

    # ── Identidad digital: el trust anchor ───────────────────────────────────
    #
    # Es LA razón de ser de la entrada. Sin esto la lista dice que existe un
    # servicio de confianza pero no con qué clave reconocerlo.
    ident = _t(info, "ServiceDigitalIdentity")
    for di in sv["digital_identity"]:
        d = _t(ident, "DigitalId")
        if di.get("X509Certificate"):
            _t(d, "X509Certificate", di["X509Certificate"])
        if di.get("X509SubjectName"):
            _t(d, "X509SubjectName", di["X509SubjectName"])
        if di.get("X509SKI"):
            _t(d, "X509SKI", di["X509SKI"])

    _t(info, "ServiceStatus", sv["status"])
    _t(info, "StatusStartingTime", _fecha(sv["status_start_date"]))

    if sv.get("definition_uri"):
        c = _t(info, "SchemeServiceDefinitionURI")
        for u in sv["definition_uri"]:
            n = _t(c, "URI", u.get("URI"))
            n.set(f"{{{XML}}}lang", u.get("lang", "en"))

    # ── ServiceSupplyPoints: dónde se comprueba la revocación ────────────────
    #
    # Estaba comentado en el upstream, y es la extensión que más falta hacía.
    # Sin ella la wallet lee la lista, obtiene el ancla y no tiene forma de
    # saber dónde preguntar si ese certificado sigue vivo. Aquí van la CRL y el
    # OCSP de la CA correspondiente.
    if sv.get("supply_points"):
        ssp = _t(info, "ServiceSupplyPoints")
        for punto in sv["supply_points"]:
            _t(ssp, "ServiceSupplyPoint", punto)

    # ── Extensiones ─────────────────────────────────────────────────────────
    extensiones = []
    if sv.get("additional_info_uri"):
        extensiones.append(("additional", sv["additional_info_uri"]))
    if sv.get("qualifier"):
        extensiones.append(("qualifications", sv["qualifier"]))

    if extensiones:
        cont = _t(info, "ServiceInformationExtensions")
        for tipo, valor in extensiones:
            # Critical="false": un validador que no entienda la extensión debe
            # poder seguir usando la entrada. Marcarlas críticas haría que
            # cualquier validador estricto rechazara la lista entera.
            ext = _t(cont, "Extension", Critical="false")
            if tipo == "additional":
                asi = _t(ext, "AdditionalServiceInformation")
                u = _t(asi, "URI", valor)
                u.set(f"{{{XML}}}lang", "en")
            else:
                quals = _t(ext, "Qualifications")
                for q in valor:
                    qe = _t(quals, "QualificationElement")
                    qs = _t(qe, "Qualifiers")
                    _t(qs, "Qualifier", uri=q["uri"] if isinstance(q, dict) else q)
                    crit = _t(qe, "CriteriaList")
                    _t(crit, "Description",
                       "Aplica a los certificados emitidos por este servicio")
                    # El atributo se llama `assert`, que es palabra reservada en
                    # Python: no puede pasarse como argumento con nombre.
                    crit.set("assert", "all")
    return s


def construir(*, esquema, operador, proveedores, secuencia, emision,
              proxima_actualizacion, punteros=None):
    """Devuelve el elemento raíz de la TSL, sin firmar."""
    raiz = etree.Element(f"{{{TSL}}}TrustServiceStatusList", nsmap=NSMAP)
    raiz.set("TSLTag", "http://uri.etsi.org/19612/TSLTag")
    raiz.set("Id", "TrustServiceStatusList")

    si = _t(raiz, "SchemeInformation")
    _t(si, "TSLVersionIdentifier", 6)
    _t(si, "TSLSequenceNumber", secuencia)
    _t(si, "TSLType", esquema["tsl_type"])

    _multilang(si, "SchemeOperatorName", operador["operator_name"], "Name")
    _direccion(si, "SchemeOperatorAddress",
               operador["postal_address"], operador["EletronicAddress"])

    _multilang(si, "SchemeName",
               [dict(lang="en", text=esquema["scheme_name"])], "Name")

    siu = _t(si, "SchemeInformationURI")
    u = _t(siu, "URI", f"{config.BASE_URL}/TL/{esquema['scheme_slug']}/")
    u.set(f"{{{XML}}}lang", "en")

    _t(si, "StatusDeterminationApproach", STATUS_DETERMINATION)

    # Reglas de comunidad propias, NO `EUcommon`. Ver config.REGLAS_COMUNIDAD.
    scr = _t(si, "SchemeTypeCommunityRules")
    u = _t(scr, "URI", config.REGLAS_COMUNIDAD)
    u.set(f"{{{XML}}}lang", "en")

    _t(si, "SchemeTerritory", esquema.get("schemeTerritory") or config.TERRITORIO)

    poln = _t(si, "PolicyOrLegalNotice")
    n = _t(poln, "TSLLegalNotice", esquema.get("description") or esquema["scheme_name"])
    n.set(f"{{{XML}}}lang", "en")

    _t(si, "HistoricalInformationPeriod", PERIODO_HISTORICO)

    if punteros:
        p = _t(si, "PointersToOtherTSL")
        for destino in punteros:
            op = _t(p, "OtherTSLPointer")
            sdis = _t(op, "ServiceDigitalIdentities")
            sdi = _t(sdis, "ServiceDigitalIdentity")
            d = _t(sdi, "DigitalId")
            _t(d, "X509Certificate", destino["cert_b64"])
            _t(op, "TSLLocation", destino["location"])

            # Sin esto, el puntero es sintácticamente válido pero un
            # consumidor conforme a TS 119 612 §5.3.7 (DSS, entre otros) no
            # lo trata como una lista real: no sabe de qué tipo es la lista
            # apuntada, ni que el XML es máquina-legible. Comprobado contra
            # un validador DSS real (Fase 7.3): sin AdditionalInformation,
            # "LOTL pointers: 0" — el puntero se ignoraba en silencio pese a
            # validar contra el XSD (AdditionalInformation es xsd:anyType,
            # así que un puntero sin él sigue siendo "válido" y sigue sin
            # servir para nada).
            ai = _t(op, "AdditionalInformation")

            # MimeType va en el namespace "additionaltypes", NO en el TSL base
            # (a diferencia de TSLType/SchemeTerritory, que sí van en TSL).
            # Confirmado por bytecode: DSS busca la clave de mapa exacta
            # "{http://uri.etsi.org/02231/v2/additionaltypes#}MimeType"
            # (MimetypeOtherTSLPointer.test); con el namespace equivocado el
            # predicado falla en silencio y el puntero se descarta.
            oi_mime = _t(ai, "OtherInformation")
            _t(oi_mime, "MimeType", "application/vnd.etsi.tsl+xml", ns=ADDTYPES)

            oi_tipo = _t(ai, "OtherInformation")
            _t(oi_tipo, "TSLType", destino["tsl_type"])

            oi_territorio = _t(ai, "OtherInformation")
            _t(oi_territorio, "SchemeTerritory", destino["schemeTerritory"] or config.TERRITORIO)

            oi_operador = _t(ai, "OtherInformation")
            _multilang(oi_operador, "SchemeOperatorName", operador["operator_name"], "Name")

    _t(si, "ListIssueDateTime", _fecha(emision))
    nu = _t(si, "NextUpdate")
    _t(nu, "dateTime", _fecha(proxima_actualizacion))

    dp = _t(si, "DistributionPoints")
    _t(dp, "URI", f"{config.BASE_URL}/TL/{esquema['scheme_slug']}/current.xml")

    # ── Proveedores ─────────────────────────────────────────────────────────
    #
    # `TrustServiceProviderList` es opcional en el XSD (minOccurs=0), y aquí eso
    # importa: una lista de un esquema que todavía no tiene proveedores es
    # VÁLIDA y hay que publicarla igual. Publicar una lista vacía y firmada dice
    # "no hay servicios de confianza de este tipo"; no publicar nada deja a
    # quien consulta sin poder distinguir eso de una avería.
    if proveedores:
        tspl = _t(raiz, "TrustServiceProviderList")
        for p in proveedores:
            tsp = _t(tspl, "TrustServiceProvider")
            info = _t(tsp, "TSPInformation")
            _multilang(info, "TSPName", p["name"], "Name")
            _multilang(info, "TSPTradeName", p["trade_name"], "Name")
            _direccion(info, "TSPAddress", p["postal_address"], p["electronic_address"])
            iu = _t(info, "TSPInformationURI")
            for entrada in (p["information_uri"] or [dict(lang="en", URI=config.BASE_URL)]):
                n = _t(iu, "URI", entrada.get("URI"))
                n.set(f"{{{XML}}}lang", entrada.get("lang", "en"))

            servicios = _t(tsp, "TSPServices")
            for sv in p["servicios"]:
                _servicio(servicios, sv)

    return raiz


_esquema_xsd = None


def validar(raiz):
    """Valida contra el XSD de TS 119 612 y lanza si no cuadra.

    Se ejecuta SIEMPRE antes de firmar, no como comprobación opcional. Una TSL
    inválida firmada es peor que ninguna: el consumidor la acepta como auténtica
    y luego falla al interpretarla, y el fallo aparece en su lado.
    """
    global _esquema_xsd
    if _esquema_xsd is None:
        _esquema_xsd = etree.XMLSchema(etree.parse(str(config.XSD)))
    doc = etree.fromstring(etree.tostring(raiz))
    if not _esquema_xsd.validate(doc):
        errores = "; ".join(str(e) for e in _esquema_xsd.error_log)
        raise ValueError(f"la TSL generada no valida contra TS 119 612: {errores}")
    return True


def der_a_b64(der: bytes) -> str:
    return base64.b64encode(der).decode()
