"""API de administración del publicador de listas.

QUÉ ES

El back-office del TLP: dar de alta actores (emisores, wallet providers,
verifiers), anadir y rotar sus certificados, declarar sus supply points y
republicar las listas. Es la contraparte de `sembrar.py`, que hace lo mismo
por consola pero solo de una vez.

EL MODELO

Un ACTOR es un SERVICIO publicado (`trust_services.service_id`), no un
proveedor. En el esquema de TS 119 612 un proveedor puede prestar servicios de
varios roles a la vez ("Civica EUDI" sale en la lista de wallet providers, en
la de PID y en la de QEAA); en la interfaz cada uno de esos es un actor
distinto, con su nombre, sus certificados y su estado. De ahi que
`admin_actores` cuelgue de `service_id`.

`trust_service_providers` y `trust_services` son la FORMA PUBLICADA: lo que
acaba dentro de los XML y los JWS firmados. El back-office necesita mas cosas
que el esquema publicado no guarda: quien creo el actor, su correo, su pais,
cuando se emitio cada certificado, cuando se retiro, y si el actor lo pone el
propio servicio (la CA de acceso de la PKI) o una persona.

Esas cosas viven en `admin_actores`, `admin_certificados` y `admin_puntos`. Al
publicar, `sincronizar()` proyecta las tres sobre las tablas publicadas, de
modo que el modelo publicado y el de administracion nunca divergen. Y
`adoptar()` hace el camino de vuelta: lo que ya esta publicado y no tiene
ficha de administracion la recibe, para que la interfaz lo muestre entero.

LA INTERFAZ

`/admin` sirve una SPA de un solo fichero (HTML + CSS + JS). La SPA calcula
su propia base a partir de la URL (`location.pathname.replace(/\\/admin\\/?$/, '')`)
y llama a `${base}/api/v1/...`. Por eso el mismo fichero sirve bajo
`/tl-publisher/admin` y bajo cualquier otro prefijo, y por eso la API vive
en `/api/v1` y no en una ruta fija.
"""

from __future__ import annotations

import base64
import json
import re
from datetime import datetime, timezone

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel

import bd
import config
import publicacion

router = APIRouter(prefix="/api/v1", tags=["administracion"])

# ── Roles y su tipo de servicio DECLARADO ──────────────────────────────────
#
# El `service_type` DECLARADO es el que usa el registro (`service_type_registry`);
# el canonico (con el segmento `TrstSvc`) lo pone el publicador al publicar.
# Ver la nota de `sembrar.py` sobre la errata del upstream.
ROLES = {
    "wallet-provider": "http://uri.etsi.org/Svc/Svctype/Provider/Wallet",
    "pid": "http://uri.etsi.org/Svc/Svctype/Provider/PID",
    "qeaa": "http://uri.etsi.org/TrstSvc/Svctype/EAA/Q",
    "pub-eaa": "http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA",
    "ca-acceso": "http://uri.etsi.org/Svc/Svctype/CA/RPaccess",
    "verifier": "http://uri.etsi.org/Svc/Svctype/CA/RPaccess",
    "qtsp": "http://uri.etsi.org/TrstSvc/Svctype/CA/QC",
}

# El rol decide en que lista se publica. `verifier` y `ca-acceso` van a la
# misma (la de acceso de partes usuarias).
LISTA_DE_ROL = {
    "wallet-provider": "WP",
    "pid": "PID",
    "qeaa": "QEAA",
    "pub-eaa": "PUBEAA",
    "ca-acceso": "RPREG",
    "verifier": "RPREG",
    "qtsp": "QEAA",
}

# Y a la inversa, para leer el rol de un servicio ya publicado.
ROL_POR_TIPO = {
    "http://uri.etsi.org/Svc/Svctype/Provider/Wallet": "wallet-provider",
    "http://uri.etsi.org/TrstSvc/Svctype/Provider/Wallet": "wallet-provider",
    "http://uri.etsi.org/Svc/Svctype/Provider/PID": "pid",
    "http://uri.etsi.org/TrstSvc/Svctype/Provider/PID": "pid",
    "http://uri.etsi.org/TrstSvc/Svctype/EAA/Q": "qeaa",
    "http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA": "pub-eaa",
    "http://uri.etsi.org/Svc/Svctype/CA/RPaccess": "ca-acceso",
    "http://uri.etsi.org/TrstSvc/Svctype/CA/RPaccess": "ca-acceso",
    "http://uri.etsi.org/TrstSvc/Svctype/CA/QC": "qtsp",
    "http://uri.etsi.org/Svc/Svctype/CA/QC": "qtsp",
}

def _direccion_postal_tsp(pais) -> str:
    """Direccion postal del proveedor con la forma que lee `generador.py`.

    El generador espera `{lang, StreetAddress, Locality, CountryName}` y los URIs como `{lang, URI}`:
    escribirlos como `{lang, text}` deja elementos `URI` vacios y la TSL no valida contra el XSD.
    """
    return json.dumps([{"lang": "en", "StreetAddress": "-", "Locality": "-",
                        "CountryName": (pais or "CO")[:2].upper()}])


def _uri_multilang(url) -> str:
    """Un URI multiidioma con la forma `{lang, URI}` (si no hay URL, la del dominio publico)."""
    return json.dumps([{"lang": "en", "URI": url or config.BASE_URL}])


ESTADO_ACTIVO = "activo"
ESTADO_RETIRADO = "retirado"


# ── Utilidades ──────────────────────────────────────────────────────────────

def _ahora() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None, microsecond=0)


def _clave(x_api_key: str | None) -> None:
    """La clave del operador. Sin ella, la administracion no se abre."""
    esperada = config.CLAVE_OPERADOR
    if not esperada:
        raise HTTPException(503, "La administracion esta deshabilitada: no hay TL_OPERATOR_KEY configurada.")
    if not x_api_key or x_api_key != esperada:
        raise HTTPException(401, "La clave del operador no es correcta")


def _pems(texto: str) -> list[x509.Certificate]:
    """Parte un texto PEM en los certificados que contenga."""
    if not texto:
        return []
    bloques = re.findall(
        r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", texto, re.S)
    if not bloques:
        raise HTTPException(400, "No hay ningun certificado PEM en el texto.")
    salida = []
    for bloque in bloques:
        try:
            salida.append(x509.load_pem_x509_certificate(bloque.encode()))
        except Exception as exc:
            raise HTTPException(400, f"Certificado PEM invalido: {exc}")
    return salida


def _info_cert(cert: x509.Certificate) -> dict:
    """La ficha de un certificado, tal como la espera la interfaz."""
    try:
        ca = cert.extensions.get_extension_for_class(x509.BasicConstraints).value
        es_ca = bool(ca.ca)
    except x509.ExtensionNotFound:
        es_ca = False
    emisor = cert.issuer.rfc4514_string()
    autofirmado = emisor == cert.subject.rfc4514_string()
    try:
        ski = cert.extensions.get_extension_for_class(
            x509.SubjectKeyIdentifier).value.digest
    except x509.ExtensionNotFound:
        ski = None
    return {
        "sujeto": cert.subject.rfc4514_string(),
        "emisor": emisor,
        "autofirmado": autofirmado,
        "es_ca": es_ca,
        "no_antes": cert.not_valid_before_utc.replace(tzinfo=None).isoformat(),
        "no_despues": cert.not_valid_after_utc.replace(tzinfo=None).isoformat(),
        "sha256": cert.fingerprint(hashes.SHA256()).hex(),
        "ski": base64.b64encode(ski).decode() if ski else None,
    }


def _avisos_cert(cert: x509.Certificate) -> list[str]:
    """Lo que hay que mirar antes de dar de alta un certificado."""
    avisos = []
    try:
        ca = cert.extensions.get_extension_for_class(x509.BasicConstraints).value
        if not ca.ca:
            avisos.append("El certificado no es una CA: no puede ser un ancla de confianza.")
    except x509.ExtensionNotFound:
        avisos.append("El certificado no declara BasicConstraints: no se puede comprobar si es una CA.")
    ahora = datetime.now(timezone.utc).replace(tzinfo=None)
    if cert.not_valid_after_utc.replace(tzinfo=None) < ahora:
        avisos.append("El certificado ya ha caducado.")
    if cert.not_valid_before_utc.replace(tzinfo=None) > ahora:
        avisos.append("El certificado aun no es valido.")
    try:
        cert.extensions.get_extension_for_class(x509.SubjectKeyIdentifier)
    except x509.ExtensionNotFound:
        avisos.append("El certificado no tiene SubjectKeyIdentifier: la wallet no podra empatar por SKI.")
    return avisos


def _lista_json(valor) -> list:
    """Normaliza una columna TEXT con JSON dentro a una lista de python.

    En la base de datos `digital_identity` y `supply_points` son TEXT con un
    JSON: `[{"X509Certificate": "..."}]` y `["https://..."]`. El lector puede
    devolverlo ya deserializado o como cadena, segun de donde se lea.
    """
    if valor is None:
        return []
    if isinstance(valor, list):
        return valor
    if isinstance(valor, str):
        texto = valor.strip()
        if not texto:
            return []
        try:
            datos = json.loads(texto)
        except Exception:
            return []
        return datos if isinstance(datos, list) else [datos]
    return []


def _texto(valor) -> str:
    """El nombre de un actor, sea texto plano o la lista multilenguaje del TSL.

    En la base de datos las columnas multilenguaje son TEXT con un JSON
    dentro: `[{"lang": "en", "text": "..."}]`. Puede venir ya deserializado
    (el lector de JSON de `bd.py` lo hace) o como cadena, segun de donde se
    lea.
    """
    if valor is None:
        return ""
    if isinstance(valor, str):
        texto = valor.strip()
        if texto.startswith("["):
            try:
                valor = json.loads(texto)
            except Exception:
                return texto
        else:
            return texto
    if isinstance(valor, list) and valor:
        primero = valor[0]
        if isinstance(primero, dict):
            return primero.get("text", "")
    return ""


def _actor_desde_fila(fila: dict) -> dict:
    """La ficha de un actor tal como la espera la interfaz."""
    certificados = []
    for c in fila.get("certificados", []):
        certificados.append({
            "id": c["cert_id"],
            "sujeto": c["sujeto"],
            "estado": c["estado"],
            "no_antes": c["no_antes"].isoformat() if c.get("no_antes") else None,
            "no_despues": c["no_despues"].isoformat() if c.get("no_despues") else None,
            "sha256": c["sha256"],
            "origen": c["origen"],
            "serie_pki": c.get("serie_pki"),
            "desde": c["desde"].isoformat() if c.get("desde") else None,
            "autofirmado": bool(c.get("autofirmado")),
            "es_ca": bool(c.get("es_ca")),
        })
    return {
        "id": fila["actor_id"],
        "nombre": fila["nombre"],
        "rol": fila["rol"],
        "estado": fila["estado"],
        "retirado_en": fila["retirado_en"].isoformat() if fila.get("retirado_en") else None,
        "creado_en": fila["creado_en"].isoformat() if fila.get("creado_en") else None,
        "pais": fila.get("pais"),
        "correo": fila.get("correo"),
        "url_info": fila.get("url_info"),
        "sistema": bool(fila.get("sistema")),
        "certificados": certificados,
        "puntos": fila.get("puntos", []),
    }


def _cargar_actores(con) -> list[dict]:
    with con.cursor() as c:
        c.execute(
            """
            SELECT a.actor_id, a.service_id, a.rol, a.nombre, a.pais, a.correo,
                   a.url_info, a.creado_en, a.sistema,
                   (SELECT MAX(r.retirado_en) FROM admin_certificados r
                     WHERE r.actor_id = a.actor_id AND r.estado = 'retirado') AS retirado_en
              FROM admin_actores a
             ORDER BY a.actor_id
            """
        )
        actores = c.fetchall()

    ids = [a["actor_id"] for a in actores]
    certificados = {}
    if ids:
        marcadores = ",".join(["%s"] * len(ids))
        with con.cursor() as c:
            c.execute(
                f"""SELECT * FROM admin_certificados
                     WHERE actor_id IN ({marcadores}) ORDER BY cert_id""",
                tuple(ids),
            )
            for fila in c.fetchall():
                certificados.setdefault(fila["actor_id"], []).append(fila)

    puntos = {}
    if ids:
        marcadores = ",".join(["%s"] * len(ids))
        with con.cursor() as c:
            c.execute(
                f"""SELECT actor_id, uri FROM admin_puntos
                     WHERE actor_id IN ({marcadores}) ORDER BY orden, punto_id""",
                tuple(ids),
            )
            for fila in c.fetchall():
                puntos.setdefault(fila["actor_id"], []).append(fila["uri"])

    salida = []
    for a in actores:
        fila = dict(a)
        fila["certificados"] = certificados.get(a["actor_id"], [])
        fila["puntos"] = puntos.get(a["actor_id"], [])
        vigente = any(c["estado"] == "otorgado" for c in fila["certificados"])
        fila["estado"] = ESTADO_ACTIVO if vigente else ESTADO_RETIRADO
        salida.append(_actor_desde_fila(fila))
    return salida


def _existe_actor(con, actor_id: int) -> dict:
    with con.cursor() as c:
        c.execute("SELECT * FROM admin_actores WHERE actor_id = %s", (actor_id,))
        fila = c.fetchone()
    if not fila:
        raise HTTPException(404, "No existe ese actor.")
    return dict(fila)


def _operador(con) -> int:
    with con.cursor() as c:
        c.execute("SELECT operator_id FROM scheme_operators LIMIT 1")
        fila = c.fetchone()
    if not fila:
        raise HTTPException(409, "No hay operador de esquema en la base de datos.")
    return fila["operator_id"]


# ── El puente entre el modelo publicado y el de administracion ───────────────

def _rol_de(service_type: str, nombre: str) -> str:
    """El rol de un actor, deducido del tipo de servicio que publica."""
    rol = ROL_POR_TIPO.get(service_type)
    if rol:
        return rol
    if "verifier" in (nombre or "").lower():
        return "verifier"
    return "ca-acceso"


def adoptar(con) -> int:
    """Da de alta en la administracion los actores que ya estan publicados.

    Hay dos caminos para que un actor exista: la interfaz (que escribe en
    `admin_actores`) y la siembra (que escribe en las tablas publicadas). Un
    actor sembrado no aparece en la interfaz si no se le adopta, y un actor
    editado en la interfaz no se publicaria si `sincronizar()` no lo leyera.

    Esta funcion es el puente: toma lo que hay publicado y le da ficha de
    administracion. Es idempotente y NO pisa lo que el operador ya haya
    editado: si el actor ya esta en `admin_actores`, no se toca.
    """
    adoptados = 0
    with con.cursor() as c:
        c.execute(
            """
            SELECT sv.service_id, sv.service_type, sv.digital_identity,
                   sv.ServiceName, r.supply_points
              FROM trust_services sv
              LEFT JOIN service_type_registry r ON r.service_type = sv.service_type
             WHERE sv.service_id NOT IN (SELECT service_id FROM admin_actores)
             ORDER BY sv.service_id
            """
        )
        filas = c.fetchall()

    for fila in filas:
        nombre = _texto(fila["ServiceName"]) or _texto(fila["digital_identity"]) or "?"
        rol = _rol_de(fila["service_type"], nombre)
        with con.cursor() as c:
            c.execute(
                """INSERT INTO admin_actores
                     (actor_id, service_id, rol, nombre, creado_en, sistema)
                   VALUES (%s, %s, %s, %s, %s, 1)""",
                (fila["service_id"], fila["service_id"], rol, nombre, _ahora()),
            )

            # Los certificados que el modelo publicado ya tenia.
            for ident in _lista_json(fila["digital_identity"]):
                if not isinstance(ident, dict):
                    continue
                b64 = ident.get("X509Certificate")
                if not b64:
                    continue
                try:
                    cert = x509.load_der_x509_certificate(base64.b64decode(b64))
                except Exception:
                    continue
                info = _info_cert(cert)
                c.execute(
                    """INSERT INTO admin_certificados
                         (actor_id, pem, sha256, sujeto, emisor, no_antes,
                          no_despues, es_ca, autofirmado, origen, estado, desde)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s,
                               'actor', 'otorgado', %s)
                       ON DUPLICATE KEY UPDATE pem = VALUES(pem)""",
                    (
                        fila["service_id"],
                        cert.public_bytes(serialization.Encoding.PEM).decode(),
                        info["sha256"], info["sujeto"], info["emisor"],
                        info["no_antes"], info["no_despues"],
                        1 if info["es_ca"] else 0, 1 if info["autofirmado"] else 0,
                        _ahora(),
                    ),
                )

            # Los supply points del tipo de servicio, si el actor no tiene.
            puntos = [u for u in _lista_json(fila["supply_points"]) if u]
            for orden, uri in enumerate(puntos):
                if not uri:
                    continue
                c.execute(
                    """INSERT INTO admin_puntos (actor_id, uri, orden)
                       SELECT %s, %s, %s FROM DUAL
                        WHERE NOT EXISTS (SELECT 1 FROM admin_puntos
                                           WHERE actor_id = %s AND uri = %s)""",
                    (fila["service_id"], uri, orden, fila["service_id"], uri),
                )
        adoptados += 1
    if adoptados:
        con.commit()
    return adoptados


def sincronizar(con) -> None:
    """Proyecta lo de administracion sobre las tablas que se publican.

    Los certificados de `admin_certificados` pasan a ser el `digital_identity`
    del servicio, y los puntos propios del actor, sus supply points. Sin esto,
    lo que se registra en la interfaz no apareceria en las listas.
    """
    with con.cursor() as c:
        c.execute(
            """SELECT actor_id, service_id FROM admin_actores
                WHERE rol IN ('wallet-provider','pid','qeaa','pub-eaa','ca-acceso')"""
        )
        actores = c.fetchall()

    for actor in actores:
        with con.cursor() as c:
            c.execute(
                "SELECT cert_id, pem FROM admin_certificados "
                "WHERE actor_id = %s AND estado = 'otorgado' ORDER BY cert_id",
                (actor["actor_id"],),
            )
            certificados = c.fetchall()

        identidades = []
        for cert in certificados:
            try:
                cert_obj = x509.load_pem_x509_certificate(cert["pem"].encode())
            except Exception:
                continue
            der = cert_obj.public_bytes(serialization.Encoding.DER)
            # TS 119 612: cada DigitalId lleva UN solo elemento (certificado, nombre o SKI).
            # Juntarlos en uno no valida contra el XSD (es lo que hace `sembrar.py` bien).
            identidades.append({"X509Certificate": base64.b64encode(der).decode()})
            try:
                identidades.append({"X509SubjectName": cert_obj.subject.rfc4514_string()})
            except Exception:
                pass
            try:
                ski = cert_obj.extensions.get_extension_for_class(
                    x509.SubjectKeyIdentifier).value.digest
                identidades.append({"X509SKI": base64.b64encode(ski).decode()})
            except x509.ExtensionNotFound:
                pass

        with con.cursor() as c:
            c.execute(
                "UPDATE trust_services SET digital_identity = %s WHERE service_id = %s",
                (json.dumps(identidades), actor["service_id"]),
            )

    # Los supply points NO se copian al registro del tipo: en TS 119 612 van en
    # cada servicio (`ServiceSupplyPoints`), y el generador ya los escribe ahi
    # desde `bd.proveedores_con_servicios`, que los lee de `admin_puntos`. El
    # registro por tipo queda como el valor por defecto de los actores que no
    # declaran ninguno.


# ── Los endpoints ────────────────────────────────────────────────────────────

class CertificadosBody(BaseModel):
    certificados_pem: str = ""


class ActorBody(BaseModel):
    rol: str
    nombre: str
    certificados_pem: str = ""
    pais: str | None = None
    correo: str | None = None
    url_info: str | None = None
    puntos: list[str] = []


class VerifierBody(BaseModel):
    nombre: str
    pais: str | None = None
    csr_pem: str = ""
    dominios: list[str] = []


class PuntosBody(BaseModel):
    puntos: list[str] = []


@router.get("/actores")
def listar_actores(x_api_key: str = Header(default=None)):
    _clave(x_api_key)
    with bd.conexion() as con:
        adoptar(con)
        con.commit()
        return _cargar_actores(con)


@router.post("/actores")
def crear_actor(cuerpo: ActorBody, x_api_key: str = Header(default=None)):
    _clave(x_api_key)
    if cuerpo.rol not in ROLES:
        raise HTTPException(400, f"Rol desconocido: {cuerpo.rol}")
    if not cuerpo.nombre.strip():
        raise HTTPException(400, "Falta el nombre.")

    certs = _pems(cuerpo.certificados_pem)
    for cert in certs:
        graves = [a for a in _avisos_cert(cert)
                  if "caducado" in a or "no es una CA" in a]
        if graves:
            raise HTTPException(400, " ".join(graves))

    tipo = ROLES[cuerpo.rol]
    # El servicio no puede estar «concedido» desde DESPUÉS de que sus certificados empiezan a valer: el
    # trust-validator descarta el ancla (p. ej. una CA creada segundos antes del registro).
    desde = min([_ahora()] + [c.not_valid_before_utc.replace(tzinfo=None) for c in certs])
    with bd.conexion() as con:
        with con.cursor() as c:
            c.execute(
                """INSERT INTO trust_service_providers
                     (name, trade_name, postal_address, EletronicAddress,
                      TSPInformationURI, operator_id)
                   VALUES (%s, %s, %s, %s, %s, %s)""",
                (
                    json.dumps([{"lang": "en", "text": cuerpo.nombre}]),
                    json.dumps([{"lang": "en", "text": cuerpo.nombre}]),
                    _direccion_postal_tsp(cuerpo.pais),
                    _uri_multilang(cuerpo.url_info),
                    _uri_multilang(cuerpo.url_info),
                    _operador(con),
                ),
            )
            tsp_id = c.lastrowid

            c.execute(
                """INSERT INTO trust_services
                     (service_type, digital_identity, status, status_start_date,
                      ServiceName, SchemeServiceDefinitionURI, operator_id, tsp_id)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
                (
                    tipo,
                    json.dumps([]),
                    "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
                    desde,
                    json.dumps([{"lang": "en", "text": cuerpo.nombre}]),
                    _uri_multilang(f"{config.BASE_URL}/pki/cps"),
                    _operador(con),
                    tsp_id,
                ),
            )
            service_id = c.lastrowid

            c.execute(
                """INSERT INTO admin_actores
                     (actor_id, service_id, rol, nombre, pais, correo, url_info,
                      creado_en, sistema)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, 0)""",
                (
                    service_id, service_id, cuerpo.rol, cuerpo.nombre, cuerpo.pais,
                    cuerpo.correo, cuerpo.url_info, _ahora(),
                ),
            )
            # actor_id no es autoincremental: es el service_id que se acaba de insertar.
            actor_id = service_id

            for cert in certs:
                info = _info_cert(cert)
                c.execute(
                    """INSERT INTO admin_certificados
                         (actor_id, pem, sha256, sujeto, emisor, no_antes,
                          no_despues, es_ca, autofirmado, origen, estado, desde)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'actor',
                               'otorgado', %s)""",
                    (
                        actor_id,
                        cert.public_bytes(serialization.Encoding.PEM).decode(),
                        info["sha256"], info["sujeto"], info["emisor"],
                        info["no_antes"], info["no_despues"],
                        1 if info["es_ca"] else 0, 1 if info["autofirmado"] else 0,
                        _ahora(),
                    ),
                )

            for orden, uri in enumerate(cuerpo.puntos or []):
                if not uri.strip():
                    continue
                c.execute(
                    "INSERT INTO admin_puntos (actor_id, uri, orden) VALUES (%s, %s, %s)",
                    (actor_id, uri.strip().rstrip("/"), orden),
                )

        con.commit()
        sincronizar(con)
        con.commit()

    return {"id": actor_id, "nombre": cuerpo.nombre, "rol": cuerpo.rol}


@router.post("/actores/{actor_id}/retirar")
def retirar_actor(actor_id: int, x_api_key: str = Header(default=None)):
    _clave(x_api_key)
    with bd.conexion() as con:
        actor = _existe_actor(con, actor_id)
        with con.cursor() as c:
            c.execute(
                """UPDATE trust_services
                      SET status = %s, status_start_date = %s
                    WHERE service_id = %s AND status <> %s""",
                (
                    "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/withdrawn",
                    _ahora(), actor["service_id"],
                    "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/withdrawn",
                ),
            )
            c.execute(
                """UPDATE admin_certificados
                      SET estado = 'retirado', retirado_en = %s
                    WHERE actor_id = %s AND estado = 'otorgado'""",
                (_ahora(), actor_id),
            )
        con.commit()
        sincronizar(con)
        con.commit()
    return {"id": actor_id, "estado": ESTADO_RETIRADO}


@router.post("/actores/{actor_id}/certificados")
def agregar_certificado(actor_id: int, cuerpo: CertificadosBody,
                         x_api_key: str = Header(default=None)):
    _clave(x_api_key)
    certs = _pems(cuerpo.certificados_pem)
    with bd.conexion() as con:
        _existe_actor(con, actor_id)
        with con.cursor() as c:
            for cert in certs:
                info = _info_cert(cert)
                c.execute(
                    """INSERT INTO admin_certificados
                         (actor_id, pem, sha256, sujeto, emisor, no_antes,
                          no_despues, es_ca, autofirmado, origen, estado, desde)
                       VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'actor',
                               'otorgado', %s)
                       ON DUPLICATE KEY UPDATE pem = VALUES(pem),
                                               estado = 'otorgado',
                                               retirado_en = NULL""",
                    (
                        actor_id,
                        cert.public_bytes(serialization.Encoding.PEM).decode(),
                        info["sha256"], info["sujeto"], info["emisor"],
                        info["no_antes"], info["no_despues"],
                        1 if info["es_ca"] else 0, 1 if info["autofirmado"] else 0,
                        _ahora(),
                    ),
                )
        con.commit()
        sincronizar(con)
        con.commit()
    return {"id": actor_id, "certificados": len(certs)}


@router.post("/actores/{actor_id}/certificados/{cert_id}/retirar")
def retirar_certificado(actor_id: int, cert_id: int,
                         x_api_key: str = Header(default=None)):
    _clave(x_api_key)
    with bd.conexion() as con:
        _existe_actor(con, actor_id)
        with con.cursor() as c:
            c.execute(
                """UPDATE admin_certificados
                      SET estado = 'retirado', retirado_en = %s
                    WHERE cert_id = %s AND actor_id = %s""",
                (_ahora(), cert_id, actor_id),
            )
        con.commit()
        sincronizar(con)
        con.commit()
    return {"id": actor_id, "certificado": cert_id, "estado": ESTADO_RETIRADO}


@router.put("/actores/{actor_id}/puntos")
def guardar_puntos(actor_id: int, cuerpo: PuntosBody,
                   x_api_key: str = Header(default=None)):
    _clave(x_api_key)
    with bd.conexion() as con:
        _existe_actor(con, actor_id)
        with con.cursor() as c:
            c.execute("DELETE FROM admin_puntos WHERE actor_id = %s", (actor_id,))
            for orden, uri in enumerate(cuerpo.puntos or []):
                if not uri.strip():
                    continue
                c.execute(
                    "INSERT INTO admin_puntos (actor_id, uri, orden) VALUES (%s, %s, %s)",
                    (actor_id, uri.strip().rstrip("/"), orden),
                )
        con.commit()
        sincronizar(con)
        con.commit()
    return {"id": actor_id, "puntos": cuerpo.puntos}


@router.get("/emisores/comprobar")
def comprobar_emisor(url: str, x_api_key: str = Header(default=None)):
    """Consulta la metadata de un emisor para ver que emite."""
    _clave(x_api_key)
    return leer_emisor(url)


def leer_emisor(url: str) -> dict:
    """Lee la metadata OpenID4VCI de un emisor (sin autenticacion: la usa tambien
    `integracion.py` para armar las clases de documentos del verifier)."""
    if not url.startswith("http"):
        raise HTTPException(400, "La URL debe empezar por http.")
    import urllib.request
    for candidato in (
        f"{url.rstrip('/')}/.well-known/openid-credential-issuer",
        f"{url.rstrip('/')}/.well-known/oauth-protected-resource",
    ):
        try:
            peticion = urllib.request.Request(
                candidato, headers={"Accept": "application/json"})
            with urllib.request.urlopen(peticion, timeout=15) as respuesta:
                cuerpo = respuesta.read()
        except Exception:
            continue
        try:
            datos = json.loads(cuerpo)
        except Exception:
            continue
        if not isinstance(datos, dict):
            continue
        # La metadata OID4VCI viene en un JWS: se descifra la carga util.
        if "payload" in datos and "protected" in datos:
            try:
                parte = datos["payload"].split(".")[1]
                datos = json.loads(base64.urlsafe_b64decode(
                    parte + "=" * (-len(parte) % 4)))
            except Exception:
                pass
        configuraciones = []
        for clave, valor in (datos.get("credential_configurations_supported") or {}).items():
            if isinstance(valor, dict):
                configuraciones.append({
                    "id": clave,
                    "nombre": valor.get("name") or valor.get("display") or clave,
                    "formato": valor.get("format") or "",
                    # SD-JWT VC: vct; mdoc: doctype
                    "tipo": valor.get("vct") or valor.get("doctype") or "",
                })
            else:
                configuraciones.append({"id": clave, "nombre": clave,
                                        "formato": str(valor), "tipo": ""})
        return {
            "ok": True,
            "nombre": datos.get("issuer") or url,
            "detalle": "Metadata OID4VCI disponible.",
            "configuraciones": configuraciones,
        }
    return {"ok": False,
            "detalle": "No se ha podido leer la metadata OID4VCI en esa URL.",
            "configuraciones": []}


@router.post("/certificados/inspeccionar")
def inspeccionar(cuerpo: CertificadosBody, x_api_key: str = Header(default=None)):
    """Mira un PEM y dice si sirve como ancla de confianza."""
    _clave(x_api_key)
    try:
        certs = _pems(cuerpo.certificados_pem)
    except HTTPException:
        return []
    salida = []
    for cert in certs:
        info = _info_cert(cert)
        info["avisos"] = _avisos_cert(cert)
        salida.append(info)
    return salida


@router.post("/verifiers")
def registrar_verifier(cuerpo: VerifierBody, x_api_key: str = Header(default=None)):
    """Registra un verifier y le emite su certificado de acceso.

    La emision la hace la CA de acceso de la PKI, que es el unico servicio
    autorizado a firmar hojas: el publicador no guarda ninguna clave privada.
    """
    _clave(x_api_key)
    if not cuerpo.nombre.strip():
        raise HTTPException(400, "Falta el nombre.")
    try:
        csr = x509.load_pem_x509_csr(cuerpo.csr_pem.encode())
    except Exception as exc:
        raise HTTPException(400, f"CSR invalido: {exc}")
    if not csr.is_signature_valid:
        raise HTTPException(400, "La firma del CSR no es valida.")

    import urllib.error
    import urllib.request
    if not config.PKI_EMITIR_KEY:
        raise HTTPException(503, "La emision de certificados no esta habilitada: "
                                 "falta PKI_ISSUE_API_KEY.")
    peticion = urllib.request.Request(
        config.PKI_EMITIR_URL,
        data=json.dumps({
            "csr_pem": cuerpo.csr_pem,
            "san_dns": (cuerpo.dominios or [None])[0],
        }).encode(),
        headers={"Content-Type": "application/json",
                 "X-API-Key": config.PKI_EMITIR_KEY},
    )
    try:
        with urllib.request.urlopen(peticion, timeout=30) as respuesta:
            emitido = json.loads(respuesta.read())
    except urllib.error.HTTPError as exc:
        detalle = exc.read().decode("utf-8", "replace")[:300]
        raise HTTPException(exc.code, f"La CA de acceso no ha podido emitir: {detalle}")
    except Exception as exc:
        raise HTTPException(503, f"La CA de acceso no esta disponible: {exc}")

    certificado_pem = emitido["cert_pem"]
    cadena_pem = emitido["chain_pem"]
    serie = emitido.get("serial")

    cert = x509.load_pem_x509_certificate(certificado_pem.encode())

    with bd.conexion() as con:
        with con.cursor() as c:
            c.execute(
                """INSERT INTO trust_service_providers
                     (name, trade_name, postal_address, EletronicAddress,
                      TSPInformationURI, operator_id)
                   VALUES (%s, %s, %s, %s, %s, %s)""",
                (
                    json.dumps([{"lang": "en", "text": cuerpo.nombre}]),
                    json.dumps([{"lang": "en", "text": cuerpo.nombre}]),
                    _direccion_postal_tsp(cuerpo.pais),
                    _uri_multilang((cuerpo.dominios or [None])[0] and f"https://{cuerpo.dominios[0]}"),
                    _uri_multilang((cuerpo.dominios or [None])[0] and f"https://{cuerpo.dominios[0]}"),
                    _operador(con),
                ),
            )
            tsp_id = c.lastrowid
            c.execute(
                """INSERT INTO trust_services
                     (service_type, digital_identity, status, status_start_date,
                      ServiceName, SchemeServiceDefinitionURI, operator_id, tsp_id)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s)""",
                (
                    ROLES["verifier"],
                    json.dumps([{"X509Certificate": base64.b64encode(
                        cert.public_bytes(serialization.Encoding.DER)).decode()}]),
                    "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted",
                    _ahora(),
                    json.dumps([{"lang": "en", "text": cuerpo.nombre}]),
                    _uri_multilang(f"{config.BASE_URL}/pki/cps"),
                    _operador(con),
                    tsp_id,
                ),
            )
            service_id = c.lastrowid
            c.execute(
                """INSERT INTO admin_actores
                     (actor_id, service_id, rol, nombre, pais, url_info,
                      creado_en, sistema)
                   VALUES (%s, %s, 'verifier', %s, %s, %s, %s, 0)""",
                (service_id, service_id, cuerpo.nombre, cuerpo.pais,
                 (cuerpo.dominios or [None])[0], _ahora()),
            )
            # actor_id se inserto explicito (= service_id): lastrowid devolveria 0 y el
            # certificado quedaria colgado de un actor que no existe.
            actor_id = service_id
            info = _info_cert(cert)
            c.execute(
                """INSERT INTO admin_certificados
                     (actor_id, pem, sha256, sujeto, emisor, no_antes,
                      no_despues, es_ca, autofirmado, origen, serie_pki,
                      estado, desde)
                   VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'pki', %s,
                           'otorgado', %s)""",
                (
                    actor_id,
                    cert.public_bytes(serialization.Encoding.PEM).decode(),
                    info["sha256"], info["sujeto"], info["emisor"],
                    info["no_antes"], info["no_despues"],
                    1 if info["es_ca"] else 0, 1 if info["autofirmado"] else 0,
                    serie, _ahora(),
                ),
            )
        con.commit()
        sincronizar(con)
        con.commit()

    return {
        "actor": {"id": actor_id, "nombre": cuerpo.nombre, "rol": "verifier"},
        "certificado_pem": certificado_pem,
        "cadena_pem": cadena_pem,
    }


@router.get("/listas")
def listar_listas(x_api_key: str = Header(default=None)):
    """Las listas publicadas, indexadas por esquema.

    Devuelve un OBJETO, no una lista: la interfaz recorre
    `Object.entries(l)` y usa la clave como nombre de la lista. Un esquema sin
    publicar viene con valor `null`, que la interfaz pinta como "sin publicar".
    """
    _clave(x_api_key)
    with bd.conexion() as con:
        esquemas = bd.esquemas(con)
        salida = {}
        for esquema in esquemas:
            slug = esquema["scheme_slug"]
            registro = bd.ultima_publicacion(con, slug)
            if not registro:
                salida[slug] = None
                continue
            salida[slug] = {
                "secuencia": registro["sequence_number"],
                "emision": registro["issue_date"].isoformat(),
                "proxima": registro["next_update"].isoformat(),
                "xml": f"{config.BASE_URL}/TL/{slug}/{registro['sequence_number']}.xml",
                "jws": f"{config.BASE_URL}/TL/{slug}/{registro['sequence_number']}.json",
            }
    return salida


@router.post("/publicar")
def publicar(x_api_key: str = Header(default=None)):
    """Republica todas las listas (las que esten vencidas y las de la LOTL)."""
    _clave(x_api_key)
    with bd.conexion() as con:
        sincronizar(con)
        con.commit()
        publicadas = []
        for esquema in bd.esquemas(con):
            slug = esquema["scheme_slug"]
            if slug == "LOTL":
                continue
            try:
                resultado = publicacion.publicar(con, esquema, motivo="publicacion manual")
                publicadas.append({"esquema": slug,
                                   "secuencia": resultado["sequence_number"]})
            except Exception as exc:
                publicadas.append({"esquema": slug, "error": str(exc)})
    return {"publicadas": publicadas}
