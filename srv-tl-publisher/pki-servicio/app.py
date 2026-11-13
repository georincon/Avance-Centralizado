"""
Publicación de la PKI: certificados de CA, CRLs y respuestas OCSP.

POR QUÉ EXISTE
--------------
Cada certificado que emite esta PKI lleva dos extensiones que son promesas:

    authorityInfoAccess    OCSP;URI:https://<dominio>/pki/ocsp
                           caIssuers;URI:https://<dominio>/pki/ca/<subca>.crt
    crlDistributionPoints  URI:https://<dominio>/pki/crl/<subca>.crl

Sin nadie que responda en esas URLs, la revocación no existe fuera de la
máquina que emitió el certificado: un validador que quiera comprobar si sigue
vigente recibe un 404 y, si es estricto, rechaza el certificado. Este servicio
es quien cumple esas promesas.

QUÉ HACE Y QUÉ NO
-----------------
Publica material y firma respuestas OCSP. **No emite certificados**: eso lo
hacen los scripts de la ceremonia, a mano y deliberadamente — con UNA
excepción, acotada: `POST /pki/issue/rp-access`, que emite bajo demanda el
access certificate de un relying party a partir de un CSR ajeno (Fase 5). Vive
aquí y no en `rp-registry` para no montar la clave de la sub-CA `rp` en un
segundo contenedor — este servicio ya la carga en memoria para firmar CRLs.
Ver `emision.py`.

La clave de la RAÍZ nunca se monta aquí. Solo hace falta para emitir una sub-CA
nueva o para regenerar la CRL de la raíz, y ambas cosas son operaciones
manuales. Lo que sí se monta —en solo lectura— son las claves de las sub-CAs,
porque firmar una CRL o una respuesta OCSP exige la clave de quien emitió el
certificado. Es el compromiso que asume cualquier CA real: el firmante de CRL y
OCSP está en línea, la raíz no.

LAS CRLs CADUCAN
----------------
Se emiten con `nextUpdate` a 24 h. Una CRL vencida hace fallar una validación
estricta EXACTAMENTE IGUAL que una revocación, así que hay que regenerarlas
aunque no haya cambios. Lo hace un hilo de fondo cada `CRL_REFRESH_HOURS`.
"""

from __future__ import annotations

import base64
import binascii
import logging
import os
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.x509 import ocsp
from fastapi import Body, FastAPI, Header, HTTPException, Request, Response

import emision

log = logging.getLogger("pki")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

# ─── Configuración ───────────────────────────────────────────────────────────
PKI_DIR = Path(os.getenv("PKI_DIR", "/pki"))
PUBLICO = PKI_DIR / "publico"
SUBCA_DIR = PKI_DIR / "subca"
RAIZ_DIR = PKI_DIR / "root"
DOMINIO = os.getenv("PUBLIC_DOMAIN", "localhost")
BASE_URL = f"https://{DOMINIO}/pki"
CRL_REFRESH_HOURS = float(os.getenv("CRL_REFRESH_HOURS", "6"))

# Emisión bajo demanda de access certificates (rp-registry). Vacía = la
# emisión responde 503 en vez de arrancar abierta — mismo criterio que
# PUBLISHER_API_KEY en tl-publisher.
ISSUE_API_KEY = os.getenv("PKI_ISSUE_API_KEY", "")

# Content-Type que exige RFC 5280 para cada artefacto. No son decorativos:
# algunos clientes rechazan la descarga si no coinciden.
CT_CERT = "application/pkix-cert"
CT_CRL = "application/pkix-crl"
CT_OCSP = "application/ocsp-response"


# ─── Estado de una CA ────────────────────────────────────────────────────────
@dataclass
class Autoridad:
    slug: str
    cert: x509.Certificate
    clave: object | None          # None si su clave no está montada (raíz)
    dir_ca: Path

    @property
    def puede_firmar(self) -> bool:
        return self.clave is not None


def _leer_cert(p: Path) -> x509.Certificate:
    datos = p.read_bytes()
    if b"BEGIN CERTIFICATE" in datos:
        return x509.load_pem_x509_certificate(datos)
    return x509.load_der_x509_certificate(datos)


def _leer_clave(p: Path):
    """Carga una clave privada, o None si no está disponible.

    Que una clave no se pueda leer NO es un error: el servicio funciona con las
    que tenga. Publicar certificados y CRLs ya generadas no necesita ninguna.
    """
    try:
        if not p.exists():
            return None
        return serialization.load_pem_private_key(p.read_bytes(), password=None)
    except (PermissionError, OSError) as e:
        log.info("clave no disponible (%s): %s", p, e.__class__.__name__)
        return None
    except Exception as e:
        log.warning("no se pudo cargar %s: %s", p, e)
        return None


def cargar_autoridades() -> dict[str, Autoridad]:
    """Descubre las CAs mirando el disco, no una lista escrita a mano."""
    autoridades: dict[str, Autoridad] = {}

    # La raíz se carga desde el material PÚBLICO, no desde su directorio.
    #
    # No es un detalle: el compose no monta `pki/root/` en absoluto, así que la
    # clave de la raíz no está físicamente al alcance de este contenedor. La
    # frontera la impone el montaje, no un permiso de fichero — un permiso se
    # cambia sin querer, un volumen que no existe no.
    #
    # Consecuencia: la CRL de la raíz no se regenera aquí. Se emite a mano en la
    # ceremonia, y solo cambia si se revoca una sub-CA entera.
    raiz_pub = PUBLICO / "ca" / "root.crt"
    if raiz_pub.exists():
        autoridades["root"] = Autoridad(
            slug="root", cert=_leer_cert(raiz_pub), clave=None, dir_ca=RAIZ_DIR
        )

    if SUBCA_DIR.is_dir():
        for d in sorted(SUBCA_DIR.iterdir()):
            cert = d / "certs" / "ca.cert.pem"
            if not cert.exists():
                continue
            autoridades[d.name] = Autoridad(
                slug=d.name,
                cert=_leer_cert(cert),
                clave=_leer_clave(d / "private" / "ca.key.pem"),
                dir_ca=d,
            )
    return autoridades


AUTORIDADES: dict[str, Autoridad] = {}


# ─── index.txt: el registro de lo emitido ────────────────────────────────────
#
# `openssl ca` mantiene un fichero de texto con una línea por certificado:
#
#   V|R|E \t caducidad \t [fecha_revocacion[,motivo]] \t serie \t fichero \t sujeto
#
# Es la única fuente que dice si algo está revocado. Se lee en cada consulta
# OCSP en vez de cachearla: revocar tiene que surtir efecto de inmediato.
MOTIVOS = {
    "unspecified": x509.ReasonFlags.unspecified,
    "keyCompromise": x509.ReasonFlags.key_compromise,
    "CACompromise": x509.ReasonFlags.ca_compromise,
    "affiliationChanged": x509.ReasonFlags.affiliation_changed,
    "superseded": x509.ReasonFlags.superseded,
    "cessationOfOperation": x509.ReasonFlags.cessation_of_operation,
    "certificateHold": x509.ReasonFlags.certificate_hold,
}


def estado_en_index(ca: Autoridad, serie: int) -> tuple[str, datetime | None, x509.ReasonFlags | None]:
    """Devuelve ('V'|'R'|'E'|'?', fecha_revocacion, motivo)."""
    index = ca.dir_ca / "index.txt"
    if not index.exists():
        return "?", None, None
    for linea in index.read_text(errors="replace").splitlines():
        campos = linea.split("\t")
        if len(campos) < 4:
            continue
        try:
            if int(campos[3], 16) != serie:
                continue
        except ValueError:
            continue
        estado = campos[0].strip()
        if estado != "R":
            return estado, None, None
        crudo = campos[2].strip()
        fecha, motivo = None, None
        if crudo:
            partes = crudo.split(",")
            try:
                # openssl usa YYMMDDHHMMSSZ
                fecha = datetime.strptime(partes[0], "%y%m%d%H%M%S%z")
            except ValueError:
                fecha = datetime.now(timezone.utc)
            if len(partes) > 1:
                motivo = MOTIVOS.get(partes[1].strip())
        return "R", fecha or datetime.now(timezone.utc), motivo
    return "?", None, None


def cert_emitido(ca: Autoridad, serie: int) -> x509.Certificate | None:
    """Recupera un certificado emitido a partir de su número de serie.

    Una peticion OCSP no lleva el certificado: solo el hash del emisor y el
    numero de serie. Y `cryptography` (44) solo sabe construir una respuesta a
    partir del certificado entero — no existe `add_response_by_hash`. Por suerte
    `openssl ca` archiva cada certificado que emite en `newcerts/<serie>.pem`,
    que es justo lo que hace falta.
    """
    directorio = ca.dir_ca / "newcerts"
    if not directorio.is_dir():
        return None
    # openssl nombra el fichero con la serie en hexadecimal y mayusculas
    for nombre in (f"{serie:X}.pem", f"{serie:02X}.pem", f"{serie:04X}.pem"):
        f = directorio / nombre
        if f.exists():
            try:
                return _leer_cert(f)
            except Exception:
                return None
    # Fallback: recorrer el archivo. Son pocos certificados y solo pasa cuando
    # el nombre no encaja con ninguna de las formas de arriba.
    for f in directorio.glob("*.pem"):
        try:
            c = _leer_cert(f)
        except Exception:
            continue
        if c.serial_number == serie:
            return c
    return None


# ─── Refresco de CRLs ────────────────────────────────────────────────────────
def regenerar_crl(ca: Autoridad) -> bool:
    """Reemite la CRL de una CA a partir de su index.txt.

    Se reconstruye entera en vez de tocar la existente: el número de secuencia
    (`crlnumber`) y la lista de revocados salen del estado en disco, que es la
    verdad.
    """
    if not ca.puede_firmar:
        return False

    index = ca.dir_ca / "index.txt"
    ahora = datetime.now(timezone.utc)
    builder = (
        x509.CertificateRevocationListBuilder()
        .issuer_name(ca.cert.subject)
        .last_update(ahora)
        .next_update(ahora + timedelta(hours=24))
    )

    if index.exists():
        for linea in index.read_text(errors="replace").splitlines():
            campos = linea.split("\t")
            if len(campos) < 4 or campos[0].strip() != "R":
                continue
            try:
                serie = int(campos[3], 16)
            except ValueError:
                continue
            estado, fecha, motivo = estado_en_index(ca, serie)
            entrada = x509.RevokedCertificateBuilder().serial_number(serie).revocation_date(
                fecha or ahora
            )
            if motivo is not None:
                entrada = entrada.add_extension(x509.CRLReason(motivo), critical=False)
            builder = builder.add_revoked_certificate(entrada.build())

    # El AKI enlaza la CRL con el certificado de la CA que la firma. Sin él, un
    # validador que maneje varias CAs no sabe cuál emitió esta lista.
    try:
        ski = ca.cert.extensions.get_extension_for_class(x509.SubjectKeyIdentifier).value
        builder = builder.add_extension(
            x509.AuthorityKeyIdentifier.from_issuer_subject_key_identifier(ski), critical=False
        )
    except x509.ExtensionNotFound:
        pass

    crl = builder.sign(private_key=ca.clave, algorithm=hashes.SHA256())

    destino = PUBLICO / "crl" / f"{ca.slug}.crl"
    destino.parent.mkdir(parents=True, exist_ok=True)
    destino.write_bytes(crl.public_bytes(serialization.Encoding.DER))
    # También en el directorio de la CA, para que los scripts la vean
    interna = ca.dir_ca / "crl" / f"{ca.slug}.crl"
    if interna.parent.is_dir():
        try:
            interna.write_bytes(crl.public_bytes(serialization.Encoding.PEM))
        except OSError:
            pass  # montaje de solo lectura: la copia publicada es la que sirve
    return True


def refrescar_todas() -> int:
    n = 0
    for ca in AUTORIDADES.values():
        try:
            if regenerar_crl(ca):
                n += 1
        except Exception as e:
            log.error("no se pudo regenerar la CRL de %s: %s", ca.slug, e)
    return n


def hilo_refresco():
    intervalo = CRL_REFRESH_HOURS * 3600
    while True:
        time.sleep(intervalo)
        n = refrescar_todas()
        log.info("CRLs regeneradas: %d", n)


# ─── API ─────────────────────────────────────────────────────────────────────
app = FastAPI(title="PKI del ecosistema EUDI", docs_url=None, redoc_url=None)


@app.on_event("startup")
def arrancar():
    global AUTORIDADES
    AUTORIDADES = cargar_autoridades()
    firmantes = [s for s, a in AUTORIDADES.items() if a.puede_firmar]
    log.info("autoridades cargadas: %s", ", ".join(sorted(AUTORIDADES)) or "(ninguna)")
    log.info("pueden firmar CRL/OCSP: %s", ", ".join(sorted(firmantes)) or "(ninguna)")
    if not AUTORIDADES:
        log.warning("no hay PKI en %s — ¿ejecutaste pki/crear-pki.sh?", PKI_DIR)
    # Se refrescan al arrancar: si el servicio estuvo caído más de 24 h, las
    # CRLs publicadas ya estarían vencidas.
    log.info("CRLs regeneradas al arrancar: %d", refrescar_todas())
    threading.Thread(target=hilo_refresco, daemon=True).start()


@app.get("/health")
def health():
    return {
        "autoridades": sorted(AUTORIDADES),
        "firmantes": sorted(s for s, a in AUTORIDADES.items() if a.puede_firmar),
    }


@app.get("/ca/{nombre}")
def certificado_ca(nombre: str):
    """Certificado de una CA. Es la URL del `caIssuers` del AIA."""
    slug = nombre.rsplit(".", 1)[0]
    ca = AUTORIDADES.get(slug)
    if ca is None:
        raise HTTPException(404, f"no existe la CA '{slug}'")
    if nombre.endswith(".pem"):
        return Response(ca.cert.public_bytes(serialization.Encoding.PEM),
                        media_type="application/x-pem-file")
    # `.crt` se sirve en DER: es lo que RFC 5280 asocia a application/pkix-cert,
    # y lo que esperan los clientes que siguen el AIA.
    return Response(ca.cert.public_bytes(serialization.Encoding.DER), media_type=CT_CERT)


@app.get("/crl/{nombre}")
def crl(nombre: str):
    """CRL de una CA, en DER. Es la URL del crlDistributionPoints."""
    slug = nombre.rsplit(".", 1)[0]
    ca = AUTORIDADES.get(slug)
    if ca is None:
        raise HTTPException(404, f"no existe la CA '{slug}'")
    fichero = PUBLICO / "crl" / f"{slug}.crl"
    if not fichero.exists():
        raise HTTPException(404, "todavía no se ha generado la CRL")
    datos = fichero.read_bytes()
    if b"BEGIN X509 CRL" in datos:
        datos = x509.load_pem_x509_crl(datos).public_bytes(serialization.Encoding.DER)
    return Response(datos, media_type=CT_CRL, headers={"Cache-Control": "public, max-age=3600"})


def _responder_ocsp(cuerpo: bytes) -> Response:
    try:
        peticion = ocsp.load_der_ocsp_request(cuerpo)
    except Exception:
        return Response(
            ocsp.OCSPResponseBuilder.build_unsuccessful(
                ocsp.OCSPResponseStatus.MALFORMED_REQUEST
            ).public_bytes(serialization.Encoding.DER),
            media_type=CT_OCSP,
        )

    # ¿Cuál de nuestras CAs emitió el certificado por el que preguntan? Se
    # identifica por el hash del nombre y de la clave del emisor, no por su
    # nombre en claro.
    emisor = None
    for ca in AUTORIDADES.values():
        if not ca.puede_firmar:
            continue
        try:
            tanteo = ocsp.OCSPRequestBuilder().add_certificate_by_hash(
                peticion.issuer_name_hash, peticion.issuer_key_hash,
                peticion.serial_number, peticion.hash_algorithm,
            )
            del tanteo
        except Exception:
            pass
        h = peticion.hash_algorithm
        nombre_hash = _hash(ca.cert.subject.public_bytes(), h)
        clave_hash = _hash(
            ca.cert.public_key().public_bytes(
                serialization.Encoding.DER,
                serialization.PublicFormat.SubjectPublicKeyInfo,
            ),
            h, spki=True,
        )
        if nombre_hash == peticion.issuer_name_hash and clave_hash == peticion.issuer_key_hash:
            emisor = ca
            break

    if emisor is None:
        # No conocemos a ese emisor: se responde UNAUTHORIZED, que es lo que
        # RFC 6960 reserva para "no soy responsable de ese certificado".
        return Response(
            ocsp.OCSPResponseBuilder.build_unsuccessful(
                ocsp.OCSPResponseStatus.UNAUTHORIZED
            ).public_bytes(serialization.Encoding.DER),
            media_type=CT_OCSP,
        )

    estado, fecha_rev, motivo = estado_en_index(emisor, peticion.serial_number)
    ahora = datetime.now(timezone.utc)

    if estado == "R":
        cert_status = ocsp.OCSPCertStatus.REVOKED
    elif estado in ("V", "E"):
        cert_status = ocsp.OCSPCertStatus.GOOD
    else:
        # No está en nuestro registro. UNKNOWN es más honesto que GOOD: no
        # afirmamos nada sobre un certificado que no emitimos.
        cert_status = ocsp.OCSPCertStatus.UNKNOWN

    sujeto = cert_emitido(emisor, peticion.serial_number)
    if sujeto is None:
        # Emitido por una CA nuestra pero no está en su archivo: no podemos
        # afirmar nada. UNKNOWN es la respuesta honesta, y para poder emitirla
        # hace falta un certificado cualquiera de referencia, así que se
        # responde con el error "no autorizado" en su lugar.
        return Response(
            ocsp.OCSPResponseBuilder.build_unsuccessful(
                ocsp.OCSPResponseStatus.UNAUTHORIZED
            ).public_bytes(serialization.Encoding.DER),
            media_type=CT_OCSP,
        )

    builder = ocsp.OCSPResponseBuilder().add_response(
        cert=sujeto,
        issuer=emisor.cert,
        algorithm=peticion.hash_algorithm,
        cert_status=cert_status,
        this_update=ahora,
        next_update=ahora + timedelta(hours=1),
        revocation_time=fecha_rev if cert_status == ocsp.OCSPCertStatus.REVOKED else None,
        revocation_reason=motivo if cert_status == ocsp.OCSPCertStatus.REVOKED else None,
    ).responder_id(ocsp.OCSPResponderEncoding.HASH, emisor.cert)

    # El nonce ata la respuesta a ESTA peticion. Sin el, una respuesta "good"
    # capturada sirve para siempre: bastaria con reproducirla para ocultar una
    # revocacion posterior. RFC 6960 pide devolverlo si el cliente lo envio.
    try:
        nonce = peticion.extensions.get_extension_for_class(x509.OCSPNonce).value
        builder = builder.add_extension(nonce, critical=False)
    except x509.ExtensionNotFound:
        pass

    respuesta = builder.sign(emisor.clave, hashes.SHA256())
    return Response(respuesta.public_bytes(serialization.Encoding.DER), media_type=CT_OCSP)


def _hash(datos: bytes, algoritmo, spki: bool = False) -> bytes:
    from cryptography.hazmat.primitives.hashes import Hash
    h = Hash(algoritmo)
    if spki:
        # El issuerKeyHash es del BIT STRING de la clave, sin la cabecera del
        # SubjectPublicKeyInfo. Se extrae con un parseo mínimo del DER.
        datos = _bits_de_spki(datos)
    h.update(datos)
    return h.finalize()


def _bits_de_spki(spki_der: bytes) -> bytes:
    """Saca el BIT STRING de la clave de un SubjectPublicKeyInfo en DER."""
    from cryptography.hazmat.primitives.asymmetric import ec, rsa
    # Se busca el último BIT STRING (tag 0x03) del SPKI.
    i = spki_der.rfind(b"\x03")
    if i < 0:
        return spki_der
    j = i + 1
    largo = spki_der[j]
    j += 1
    if largo & 0x80:
        n = largo & 0x7F
        j += n
    # El primer octeto del BIT STRING son los bits no usados: se descarta.
    return spki_der[j + 1:]


@app.post("/ocsp")
async def ocsp_post(request: Request):
    return _responder_ocsp(await request.body())


@app.get("/ocsp/{peticion_b64:path}")
def ocsp_get(peticion_b64: str):
    """RFC 6960 permite OCSP por GET con la petición en base64 en la ruta."""
    from urllib.parse import unquote
    try:
        cuerpo = base64.b64decode(unquote(peticion_b64))
    except (binascii.Error, ValueError):
        raise HTTPException(400, "petición OCSP mal codificada")
    return _responder_ocsp(cuerpo)


@app.get("/cps")
def cps():
    """Certificate Policy / CPS. Lo referencian las certificatePolicies."""
    filas = "".join(
        f"<tr><td><code>{s}</code></td><td>{a.cert.subject.rfc4514_string()}</td>"
        f"<td>{a.cert.not_valid_after_utc:%Y-%m-%d}</td>"
        f"<td>{'sí' if a.puede_firmar else 'no'}</td></tr>"
        for s, a in sorted(AUTORIDADES.items())
    )
    return Response(f"""<!doctype html>
<html lang="es"><head><meta charset="utf-8">
<title>CPS — PKI del ecosistema EUDI</title>
<style>body{{font-family:system-ui,sans-serif;max-width:50rem;margin:3rem auto;padding:0 1rem;line-height:1.6}}
table{{border-collapse:collapse;width:100%}}td,th{{border:1px solid #ccc;padding:.4rem .6rem;text-align:left}}
code{{background:#f4f4f4;padding:.1rem .3rem}}</style></head><body>
<h1>Certificate Policy / CPS</h1>
<p>Política de certificación de la PKI que respalda el ecosistema EUDI Wallet
desplegado en <code>{DOMINIO}</code>.</p>

<h2>Alcance y limitación</h2>
<p><strong>Esta no es una PKI cualificada.</strong> Los artefactos que emite son
conformes —X.509 con AIA y CRLDP, CRLs según RFC 5280, respuestas OCSP según
RFC 6960— pero su raíz no figura en la Lista de Confianza de la Comisión
Europea. Sirve para un piloto que sigue el ARF, no para producir efectos
jurídicos bajo eIDAS.</p>

<h2>Autoridades</h2>
<table><tr><th>Slug</th><th>Sujeto</th><th>Caduca</th><th>Firma en línea</th></tr>
{filas}</table>
<p>La clave de la raíz no está en línea: solo se usa para emitir sub-CAs, en una
ceremonia manual. Las sub-CAs sí firman CRL y OCSP desde este servicio.</p>

<h2>Publicación</h2>
<ul>
<li><code>/pki/ca/&lt;slug&gt;.crt</code> — certificado de CA (DER)</li>
<li><code>/pki/crl/&lt;slug&gt;.crl</code> — CRL (DER), <code>nextUpdate</code> a 24 h,
regenerada cada {CRL_REFRESH_HOURS:g} h</li>
<li><code>/pki/ocsp</code> — OCSP (POST, y GET con la petición en base64)</li>
</ul>

<h2>Revocación</h2>
<p>La solicita el operador de la PKI con <code>pki/revocar.sh</code>. El cambio
aparece en la CRL y en OCSP de inmediato: ambos se derivan del registro
<code>index.txt</code> de cada CA en el momento de la consulta.</p>
</body></html>""", media_type="text/html; charset=utf-8")


def _comprobar_api_key(x_api_key: str | None):
    if not ISSUE_API_KEY:
        raise HTTPException(503, "PKI_ISSUE_API_KEY no está configurada")
    if x_api_key != ISSUE_API_KEY:
        raise HTTPException(401, "X-API-Key incorrecta o ausente")


@app.post("/issue/rp-access")
def emitir_rp_access(
    body: dict = Body(...),
    x_api_key: str = Header(default=None),
):
    """Emite un access certificate de relying party a partir de un CSR ajeno.

    Única excepción al "no emite certificados" de este servicio — ver el
    docstring del módulo y el de `emision.py`. La llama `rp-registry`, nunca
    directamente desde internet (denegado en HAProxy).
    """
    _comprobar_api_key(x_api_key)
    ca = AUTORIDADES.get("rp")
    if ca is None:
        raise HTTPException(500, "la sub-CA 'rp' no está cargada")
    csr_pem = body.get("csr_pem")
    if not csr_pem:
        raise HTTPException(400, "falta csr_pem")
    try:
        cert_pem, cadena_pem, serie = emision.emitir_rp_access(
            ca, BASE_URL, csr_pem.encode(), body.get("san_dns")
        )
    except emision.EmisionError as e:
        raise HTTPException(400, str(e))
    return {
        "cert_pem": cert_pem.decode(),
        "chain_pem": cadena_pem.decode(),
        "serial": serie,
    }


@app.post("/revoke/rp-access/{serie}")
def revocar_rp_access(
    serie: str,
    body: dict = Body(default={}),
    x_api_key: str = Header(default=None),
):
    """Revoca una serie emitida por `/issue/rp-access` y refresca su CRL.

    A diferencia de `pki/revocar.sh` (pensado para un operador humano con
    acceso al disco), esto lo llama `rp-registry` por HTTP. Regenera la CRL
    en el mismo acto: sin esto, la revocación tardaría hasta
    `CRL_REFRESH_HOURS` en aparecer publicada — OCSP ya la ve de inmediato
    porque lee `index.txt` en cada consulta, pero la CRL no.
    """
    _comprobar_api_key(x_api_key)
    ca = AUTORIDADES.get("rp")
    if ca is None:
        raise HTTPException(500, "la sub-CA 'rp' no está cargada")
    motivo = body.get("reason", "unspecified")
    try:
        emision.revocar_rp_access(ca, serie, motivo)
    except emision.EmisionError as e:
        raise HTTPException(400, str(e))
    regenerar_crl(ca)
    return Response(status_code=204)
