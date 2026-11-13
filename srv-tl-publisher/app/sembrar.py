"""Registra en el TLP los anclas de confianza reales de este ecosistema.

POR QUE HACE FALTA

El TLP se alimenta desde su interfaz web, y para entrar en ella hay que
autenticarse presentando un PID en `mso_mdoc` desde la wallet. Eso significa
que, tal cual, no hay forma de tener una Trusted List sin haber completado
antes toda la cadena de emision. Y al reves: sin Trusted List no hay confianza
que validar. Un huevo y una gallina.

Este script rompe el ciclo dando de alta directamente lo que ya EXISTE de
verdad en el despliegue: las sub-CAs de la PKI. No inventa datos de prueba —
lee `pki/publico/ca/*.crt`, que son los certificados con los que este
ecosistema firma de verdad.

El TLP sigue siendo el editor: un operador puede entrar despues y modificar
cualquiera de estas entradas desde la interfaz. Esto es la semilla, no un
sustituto.

    python sembrar.py [--rehacer]
"""

import base64
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import serialization, hashes

import bd
import config

PKI = Path("/app/pki-publico")

ORG = "Civica EUDI"

# ─── Los cinco esquemas del art. 22a, mas la LOTL ────────────────────────────
#
# `PUBEAA` se crea aunque hoy no tenga ningun servicio. Publicar una lista
# vacia y firmada dice "no hay organismos del sector publico dados de alta";
# no publicarla deja a quien consulta sin distinguir eso de una averia.

ESQUEMAS = [
    ("WP",     "Civica Wallet Provider Trusted List",
     "Proveedores de wallet (art. 22a.1.a)"),
    ("PID",    "Civica PID Provider Trusted List",
     "Emisores de la credencial de identidad (art. 22a.1.b)"),
    ("QEAA",   "Civica QTSP Trusted List",
     "Prestadores cualificados que emiten QEAA (art. 22a.1.c)"),
    ("PUBEAA", "Civica Public Body EAA Trusted List",
     "Organismos del sector publico que emiten Pub-EAA (art. 22a.1.d)"),
    ("RPREG",  "Civica Relying Party Registrar Trusted List",
     "Registradores de partes usuarias (art. 22a.1.e)"),
]

TSL_TYPE_GENERIC = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric"
TSL_TYPE_LOTL = "http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUlistofthelists"

# ─── Tipos de servicio ───────────────────────────────────────────────────────
#
# LA COLUMNA `canonical_uri` ES EL PUNTO IMPORTANTE.
#
# El upstream trae en config.py tres URIs a los que les falta el segmento
# `TrstSvc`, y en la MISMA lista otros que si lo llevan. Es una errata suya:
#
#   http://uri.etsi.org/Svc/Svctype/Provider/Wallet       <- mal
#   http://uri.etsi.org/TrstSvc/Svctype/EAA/Q             <- bien
#
# Se conserva la forma del upstream como clave (para que las filas que ya haya
# dado de alta un operador desde la interfaz sigan resolviendo) y se publica la
# forma corregida. Los dos primeros no existen en el registro de ETSI: son la
# forma de los borradores del CIR y del ARF, que es la unica con alguna
# probabilidad de que un validador de terceros la reconozca. La desviacion se
# senala con AdditionalServiceInformation, que apunta a un URI nuestro con la
# definicion.

TIPOS = [
    dict(clave="http://uri.etsi.org/Svc/Svctype/Provider/Wallet",
         esquema="WP",
         canonico="http://uri.etsi.org/TrstSvc/Svctype/Provider/Wallet",
         nota="Corregido: al del upstream le falta el segmento TrstSvc"),
    dict(clave="http://uri.etsi.org/Svc/Svctype/Provider/PID",
         esquema="PID",
         canonico="http://uri.etsi.org/TrstSvc/Svctype/Provider/PID",
         nota="Corregido: al del upstream le falta el segmento TrstSvc"),
    dict(clave="http://uri.etsi.org/Svc/Svctype/CA/RPaccess",
         esquema="RPREG",
         canonico="http://uri.etsi.org/TrstSvc/Svctype/CA/RPaccess",
         nota="Corregido: al del upstream le falta el segmento TrstSvc"),
    dict(clave="http://uri.etsi.org/TrstSvc/Svctype/EAA/Q",
         esquema="QEAA", canonico=None,
         nota="URI de ETSI, se publica tal cual"),
    dict(clave="http://uri.etsi.org/TrstSvc/Svctype/CA/QC",
         esquema="QEAA", canonico=None,
         nota="URI de ETSI, se publica tal cual"),
    dict(clave="http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA",
         esquema="PUBEAA", canonico=None,
         nota="URI de ETSI, se publica tal cual"),
]

# ─── Que sub-CA de la PKI va en que lista ────────────────────────────────────
#
# slug del fichero en pki/publico/ca/ -> (tipo de servicio, nombre del servicio)

# ─── Qualifications (solo en la lista QEAA) ──────────────────────────────────
#
# Los `Qualifiers` dicen QUE cualifica un servicio, cosa que el
# ServiceTypeIdentifier por si solo no distingue. Un `CA/QC` puede emitir
# certificados para firma, para sello o para autenticacion de sitio web, y solo
# el qualifier lo aclara.
#
# Van SOLO en la lista QEAA, que es donde TS 119 612 los espera: en las listas
# de proveedores de wallet o de PID no tienen sentido porque ahi no hay
# certificados cualificados que cualificar.
#
# El `CriteriaList` que los acompana lo genera el propio generador con
# assert="all": el qualifier aplica a todos los certificados que emite ese
# servicio, no a un subconjunto. Un subconjunto exigiria criterios por KeyUsage
# o por PolicySet, y hoy no hay ninguna razon para partirlo.

QUAL_ESIG = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCForESig"
QUAL_QSCD = "http://uri.etsi.org/TrstSvc/TrustedList/SvcInfoExt/QCQSCDStatusAsInCert"

ANCLAS = [
    ("wp",   "http://uri.etsi.org/Svc/Svctype/Provider/Wallet",
     "Civica Wallet Provider - attestation de instancias de wallet", None),
    ("pid",  "http://uri.etsi.org/Svc/Svctype/Provider/PID",
     "Civica PID Issuer - document signers de la credencial de identidad", None),
    ("qeaa", "http://uri.etsi.org/TrstSvc/Svctype/EAA/Q",
     "Civica QEAA Issuer - atestaciones cualificadas de atributos", None),
    ("qtsp", "http://uri.etsi.org/TrstSvc/Svctype/CA/QC",
     "Civica QTSP - certificados cualificados de firma",
     # Emite certificados de firma electronica, y si el certificado dice que la
     # clave vive en un QSCD, hay que creerle (`...StatusAsInCert`). Es lo
     # correcto mientras la clave este en el SoftHSM del QTSP y no en un HSM
     # certificado: declarar QCWithSSCD a secas seria afirmar algo mas fuerte
     # de lo que se puede sostener.
     [QUAL_ESIG, QUAL_QSCD]),
    ("rp",   "http://uri.etsi.org/Svc/Svctype/CA/RPaccess",
     "Civica RP Access CA - access certificates de partes usuarias", None),
]

ESTADO_GRANTED = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted"


def _ml(texto, lang="en"):
    return json.dumps([dict(lang=lang, text=texto)])


def _uris(uris, lang="en"):
    return json.dumps([dict(lang=lang, URI=u) for u in uris])


def cargar_ancla(slug):
    """Lee un certificado de sub-CA de la PKI y devuelve su DigitalId."""
    ruta = PKI / "ca" / f"{slug}.crt"
    if not ruta.is_file():
        return None
    datos = ruta.read_bytes()
    try:
        cert = x509.load_der_x509_certificate(datos)
    except ValueError:
        cert = x509.load_pem_x509_certificate(datos)
    der = cert.public_bytes(serialization.Encoding.DER)

    # Se publican las tres formas que permite TS 119 612: el certificado
    # entero, el subject DN y el SKI. La wallet puede empatar por SKI sin
    # tener que parsear el certificado, que es mas barato y menos fragil.
    try:
        ski = cert.extensions.get_extension_for_class(
            x509.SubjectKeyIdentifier).value.digest
    except x509.ExtensionNotFound:
        ski = None

    ident = [dict(X509Certificate=base64.b64encode(der).decode()),
             dict(X509SubjectName=cert.subject.rfc4514_string())]
    if ski:
        ident.append(dict(X509SKI=base64.b64encode(ski).decode()))
    return cert, ident


def puntos_de_suministro(slug):
    """Donde se comprueba la revocacion de lo que emite esta CA.

    Es la extension que estaba comentada en el upstream y la que mas falta
    hacia: sin ella la wallet obtiene el ancla y no tiene forma de saber donde
    preguntar si ese certificado sigue vivo.
    """
    puntos = [
        f"{config.BASE_URL}/pki/crl/{slug}.crl",
        f"{config.BASE_URL}/pki/ocsp",
    ]
    # La sub-CA `rp` es también donde cuelga el registro de relying parties
    # (Fase 5.1): el mismo servicio que emite sus access certificates. Un
    # tercer supply point es cómo la wallet DESCUBRE `rp-registry` desde la
    # trusted list, en vez de llevar su URL hardcodeada — es lo que convierte
    # esto en infraestructura de confianza y no en configuración.
    if slug == "rp":
        puntos.append(f"{config.BASE_URL}/rp-registry/api/v1/")
    return json.dumps(puntos)


def anclas_importadas():
    """Anclas traidas de listas ya publicadas (manifiesto `anclas.json`).

    POR QUE EXISTEN

    `ANCLAS` es la PKI PROPIA del ecosistema: las sub-CAs que emite
    `pki/crear-pki.sh`. Pero el despliegue publica en sus listas las anclas de
    TODOS los proveedores que lo integran, y esos proveedores firmaban con CAs
    que no son de aqui: cada uno trae la suya.

    Importarlas (ver scripts/importar-anclas-remotas.py) es lo que permite que
    el publicador LOCAL sirva las mismas listas que servia el remoto, y que
    seguir validando los certificados de los proveedores que ya estan
    desplegados. Sin esto, publicar en local equivaldria a cambiar, en
    silencio, quien es de fiar.

    El manifiesto es un fichero, no una llamada a la red: una vez importado,
    el publicador no depende de nadie.
    """
    ruta = PKI / "anclas.json"
    if not ruta.is_file():
        return []
    try:
        datos = json.loads(ruta.read_text())
    except (json.JSONDecodeError, OSError) as exc:
        raise SystemExit(f"anclas.json no se puede leer: {exc}")
    return datos.get("anclas", [])


def sembrar(rehacer=False):
    ahora = datetime.now(timezone.utc).replace(microsecond=0, tzinfo=None)

    with bd.conexion() as con:
        with con.cursor() as c:
            if rehacer:
                # Orden inverso al de las claves ajenas.
                for tabla in ("published_lists", "tl_schemes", "trusted_list_updates",
                              "trust_services", "trust_service_providers",
                              "trusted_lists"):
                    c.execute(f"DELETE FROM {tabla}")
                c.execute("DELETE FROM scheme_operators")
                print("  estado anterior borrado")

            # ── Pais ────────────────────────────────────────────────────────
            c.execute("SELECT country_id FROM countries WHERE country_code = %s",
                      (config.TERRITORIO,))
            fila = c.fetchone()
            if not fila:
                raise SystemExit(
                    f"no existe el pais '{config.TERRITORIO}' en `countries`. "
                    "Lo siembra el propio TLP al arrancar (Parche 2)."
                )
            country_id = fila["country_id"]

            # ── Operador del esquema ────────────────────────────────────────
            c.execute("SELECT operator_id FROM scheme_operators LIMIT 1")
            fila = c.fetchone()
            if fila:
                operator_id = fila["operator_id"]
                print(f"  operador existente: {operator_id}")
            else:
                c.execute(
                    """INSERT INTO scheme_operators
                       (country_id, pid_hash, operator_role, operator_name,
                        EletronicAddress, country, postal_address)
                       VALUES (%s,%s,%s,%s,%s,%s,%s)""",
                    (country_id, None, "tsl_op", _ml(ORG),
                     _uris([f"{config.BASE_URL}/tlp/"]),
                     config.TERRITORIO,
                     json.dumps([dict(lang="en", StreetAddress="Calle 1",
                                      Locality="Bogota", StateOrProvince="Cundinamarca",
                                      PostalCode="110111",
                                      CountryName=config.TERRITORIO)])),
                )
                operator_id = c.lastrowid
                print(f"  operador creado: {operator_id}")

            # ── Registro de tipos de servicio ───────────────────────────────
            for t in TIPOS:
                c.execute(
                    """INSERT INTO service_type_registry
                       (service_type, scheme_slug, canonical_uri,
                        additional_info_uri, supply_points, notes)
                       VALUES (%s,%s,%s,%s,%s,%s)
                       ON DUPLICATE KEY UPDATE
                         scheme_slug=VALUES(scheme_slug),
                         canonical_uri=VALUES(canonical_uri),
                         additional_info_uri=VALUES(additional_info_uri),
                         notes=VALUES(notes)""",
                    (t["clave"], t["esquema"], t["canonico"],
                     f"{config.BASE_URL}/trust/Svctype/{t['esquema']}",
                     None, t["nota"]),
                )
            print(f"  {len(TIPOS)} tipos de servicio registrados")

            # ── Listas y esquemas ───────────────────────────────────────────
            todos = ESQUEMAS + [("LOTL", "Civica List of Trusted Lists",
                                 "Lista de listas: punteros a las cinco del art. 22a")]
            for slug, nombre, descripcion in todos:
                es_lotl = slug == "LOTL"
                c.execute("SELECT tsl_id FROM tl_schemes WHERE scheme_slug = %s", (slug,))
                fila = c.fetchone()
                if fila:
                    continue
                c.execute(
                    """INSERT INTO trusted_lists
                       (country_id, Version, SequenceNumber, TSLType, SchemeName_lang,
                        schemeTerritory, issue_date, next_update, status, operator_id)
                       VALUES (%s,6,0,%s,%s,%s,%s,%s,%s,%s)""",
                    (country_id,
                     TSL_TYPE_LOTL if es_lotl else TSL_TYPE_GENERIC,
                     _ml(nombre), config.TERRITORIO, ahora, ahora, "active", operator_id),
                )
                tsl_id = c.lastrowid
                c.execute(
                    """INSERT INTO tl_schemes
                       (scheme_slug, tsl_id, scheme_name, tsl_type, is_lotl, description)
                       VALUES (%s,%s,%s,%s,%s,%s)""",
                    (slug, tsl_id, nombre,
                     TSL_TYPE_LOTL if es_lotl else TSL_TYPE_GENERIC,
                     1 if es_lotl else 0, descripcion),
                )
                print(f"  esquema {slug} -> trusted_lists {tsl_id}")

            # ── Proveedor y servicios ───────────────────────────────────────
            c.execute("SELECT tsp_id FROM trust_service_providers WHERE operator_id = %s LIMIT 1",
                      (operator_id,))
            fila = c.fetchone()
            if fila:
                tsp_id = fila["tsp_id"]
            else:
                c.execute("SELECT tsl_id FROM tl_schemes WHERE scheme_slug='PID'")
                tsl_pid = c.fetchone()["tsl_id"]
                c.execute(
                    """INSERT INTO trust_service_providers
                       (tsl_id, name, postal_address, trade_name, EletronicAddress,
                        TSPInformationURI, operator_id)
                       VALUES (%s,%s,%s,%s,%s,%s,%s)""",
                    (tsl_pid, _ml(ORG),
                     json.dumps([dict(lang="en", StreetAddress="Calle 1",
                                      Locality="Bogota", CountryName=config.TERRITORIO)]),
                     _ml(ORG), _uris([f"{config.BASE_URL}/tlp/"]),
                     _uris([config.BASE_URL]), operator_id),
                )
                tsp_id = c.lastrowid
                print(f"  proveedor creado: {tsp_id}")

            registrados, ausentes = 0, []
            candidatas = [(slug, tipo, nombre, qualifiers, None)
                          for slug, tipo, nombre, qualifiers in ANCLAS]
            candidatas += [(a["slug"], a["tipo"], a["nombre"], None,
                            a.get("supply_points"))
                           for a in anclas_importadas()]
            for slug, tipo, nombre, qualifiers, supply in candidatas:
                cargado = cargar_ancla(slug)
                if cargado is None:
                    ausentes.append(slug)
                    continue
                cert, ident = cargado
                # Los puntos de suministro (CRL, OCSP, y desde la Fase 5.1 la
                # URL de rp-registry para la sub-CA `rp`) describen DONDE se
                # comprueba la revocacion de lo que emite esta CA. Hoy cada
                # tipo de servicio corresponde a exactamente una sub-CA, asi
                # que viven en el registro de tipos. Se actualizan SIEMPRE,
                # incluso si el servicio ya estaba sembrado: si sólo corriera
                # dentro del `if` de más abajo, un re-seed sin `--rehacer` (el
                # caso normal tras el primer arranque) nunca recogería un
                # supply point nuevo como éste. Si algun dia dos servicios
                # comparten tipo pero no CA, esto tiene que bajar a
                # `trust_services`.
                #
                # Las anclas importadas traen SUS puntos de suministro: los que
                # declara el propio certificado (CRLDP). Los de la PKI propia se
                # derivan del dominio local.
                puntos = (json.dumps(supply) if supply
                          else puntos_de_suministro(slug))
                c.execute(
                    "UPDATE service_type_registry SET supply_points = %s WHERE service_type = %s",
                    (puntos, tipo),
                )
                c.execute(
                    "SELECT service_id FROM trust_services WHERE ServiceName = %s",
                    (_ml(nombre),))
                if c.fetchone():
                    continue
                c.execute(
                    """INSERT INTO trust_services
                       (service_type, digital_identity, status, status_start_date,
                        qualifier, ServiceName, SchemeServiceDefinitionURI,
                        operator_id, tsp_id)
                       VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s)""",
                    (tipo, json.dumps(ident), ESTADO_GRANTED,
                     cert.not_valid_before_utc.replace(tzinfo=None),
                     json.dumps(qualifiers) if qualifiers else None, _ml(nombre),
                     _uris([f"{config.BASE_URL}/pki/cps"]),
                     operator_id, tsp_id),
                )
                registrados += 1

        con.commit()

    print(f"  {registrados} anclas registradas")
    if ausentes:
        print(f"  AVISO: no se encontraron en la PKI: {', '.join(ausentes)}")
    return registrados


if __name__ == "__main__":
    rehacer = "--rehacer" in sys.argv
    print("Sembrando la Trusted List desde la PKI...")
    sembrar(rehacer)
    print("Listo. Ahora se puede publicar.")
