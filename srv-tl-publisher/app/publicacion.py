"""Publicar una Trusted List: generar, validar, firmar, versionar y guardar.

QUÉ SIGNIFICA "PUBLICAR" AQUÍ

Cinco cosas que pasan juntas o no pasan:

  1. Se genera el XML a partir del estado actual de la base del TLP.
  2. Se valida contra el XSD de TS 119 612.
  3. Se firma con XAdES.
  4. Se escribe en el almacén con un nombre que incluye el número de secuencia,
     y ese fichero ya NO se toca nunca más.
  5. Se registra la fila, se marca como superada la anterior y se anota en
     `trusted_list_updates`.

El paso 4 es el que convierte esto en una publicación y no en una descarga: una
lista publicada es inmutable y direccionable para siempre por su secuencia. Un
consumidor que cacheó `/TL/PID/7.xml` puede volver a pedirlo dentro de un año y
recibir exactamente los mismos bytes.
"""

import hashlib
import logging
from datetime import datetime, timedelta, timezone

import bd
import config
import espejo
import firma
import generador

log = logging.getLogger("tl-publisher")


def _ahora():
    return datetime.now(timezone.utc).replace(microsecond=0, tzinfo=None)


def ruta_de(scheme_slug, secuencia):
    return config.ALMACEN / scheme_slug / f"{secuencia}.xml"


def publicar(con, esquema, *, motivo="publicación manual"):
    """Publica una versión nueva del esquema dado. Devuelve el registro."""
    slug = esquema["scheme_slug"]

    operador = bd.operador(con, esquema["operator_id"])
    proveedores = bd.proveedores_con_servicios(con, slug)

    ultima = bd.ultima_publicacion(con, slug)
    # El número de secuencia es monótono por esquema y no se reutiliza NUNCA,
    # ni siquiera si la publicación anterior fue un error. Es lo que permite a
    # un consumidor detectar que se le está sirviendo una lista vieja.
    secuencia = (ultima["sequence_number"] + 1) if ultima else 1

    emision = _ahora()
    proxima = emision + timedelta(hours=config.HORAS_VALIDEZ)

    punteros = None
    if esquema["is_lotl"]:
        punteros = _punteros_lotl(con)

    raiz = generador.construir(
        esquema=esquema,
        operador=operador,
        proveedores=proveedores,
        secuencia=secuencia,
        emision=emision,
        proxima_actualizacion=proxima,
        punteros=punteros,
    )
    generador.validar(raiz)

    xml = firma.firmar(raiz)
    sha = hashlib.sha256(xml).hexdigest()

    destino = ruta_de(slug, secuencia)
    destino.parent.mkdir(parents=True, exist_ok=True)
    # Escritura atómica: si el proceso muere a media escritura, el fichero
    # definitivo no llega a existir y la fila tampoco, así que el estado sigue
    # siendo coherente. Un XML truncado con su fila en la base sería peor.
    temporal = destino.with_suffix(".xml.parcial")
    temporal.write_bytes(xml)
    temporal.rename(destino)

    # ── Espejo JWS (Fase 1.8) ───────────────────────────────────────────────
    #
    # Se genera AQUI, en el mismo acto y de la misma fila, y no en un proceso
    # aparte. Es lo que impide que las dos vistas se separen: no existe un
    # momento en que el XML este publicado y el espejo describa otra cosa.
    #
    # Si fallara, la publicacion entera falla. Publicar el XML y dejar el espejo
    # a medias seria peor que no publicar: un consumidor que use el espejo
    # estaria trabajando con un estado que nadie firmo.
    jws = espejo.construir(
        esquema=esquema,
        secuencia=secuencia,
        emision=emision,
        proxima_actualizacion=proxima,
        xml_bytes=xml,
        xml_sha256=sha,
    )
    destino_jws = espejo.ruta_de(destino)
    temporal_jws = destino_jws.with_suffix(".jws.parcial")
    temporal_jws.write_bytes(jws)
    temporal_jws.rename(destino_jws)

    cert_sha, serie = firma.identidad_firmante()

    n_servicios = sum(len(p["servicios"]) for p in proveedores)
    descripcion = (
        f"{slug} secuencia {secuencia}: {len(proveedores)} proveedor(es), "
        f"{n_servicios} servicio(s). {motivo}"
    )

    bd.registrar_publicacion(
        con,
        tsl_id=esquema["tsl_id"],
        scheme_slug=slug,
        secuencia=secuencia,
        version=6,
        issue_date=emision,
        next_update=proxima,
        xml_sha256=sha,
        xml_path=str(destino),
        signer_cert_sha256=cert_sha,
        signer_serial=serie,
        descripcion=descripcion,
    )

    log.info("publicado %s secuencia %s (%s bytes XML + %s bytes JWS, sha256 %s)",
             slug, secuencia, len(xml), len(jws), sha[:16])
    return dict(scheme=slug, sequence_number=secuencia, issue_date=emision,
                next_update=proxima, xml_sha256=sha, bytes=len(xml),
                jws_bytes=len(jws),
                providers=len(proveedores), services=n_servicios)


def _punteros_lotl(con):
    """Punteros de la LOTL a cada una de las listas de esquema.

    Sólo apunta a esquemas que YA tienen una publicación vigente. Una LOTL que
    apunte a una lista que todavía no existe es peor que una LOTL con menos
    punteros: quien la siga recibe un 404 y no sabe si es un fallo suyo.
    """
    from cryptography.hazmat.primitives import serialization

    cert = firma._cargar_cert()
    cert_b64 = generador.der_a_b64(cert.public_bytes(serialization.Encoding.DER))

    # tsl_type/schemeTerritory de CADA esquema apuntado (no sólo el de la
    # LOTL) — generador.construir() los necesita para el AdditionalInformation
    # de cada puntero (Fase 7.3: sin ellos, un validador DSS real descarta el
    # puntero en silencio).
    esquemas = {e["scheme_slug"]: e for e in bd.esquemas(con)}

    punteros = []
    for pub in bd.todas_las_publicaciones_vigentes(con):
        if pub["scheme_slug"] == "LOTL":
            continue
        esquema_destino = esquemas.get(pub["scheme_slug"], {})
        punteros.append(dict(
            cert_b64=cert_b64,
            location=f"{config.BASE_URL}/TL/{pub['scheme_slug']}/current.xml",
            tsl_type=esquema_destino.get("tsl_type"),
            schemeTerritory=esquema_destino.get("schemeTerritory"),
        ))
    return punteros


def toca_republicar(pub):
    """¿Hay que republicar ya?

    Se republica al 80% del intervalo de validez, aunque no haya cambiado nada.
    Una lista con `NextUpdate` vencido es un FALLO de validación, exactamente
    igual que una firma rota: el consumidor no puede distinguir "no ha cambiado"
    de "el publicador lleva dos días caído".
    """
    if pub is None:
        return True
    emision = pub["issue_date"]
    proxima = pub["next_update"]
    if emision is None or proxima is None:
        return True
    vida = (proxima - emision).total_seconds()
    if vida <= 0:
        return True
    transcurrido = (datetime.utcnow() - emision).total_seconds()
    return transcurrido >= vida * config.FRACCION_REPUBLICACION


def publicar_lo_que_toque():
    """Pasada del trabajo de fondo. Devuelve lo que republicó."""
    hechas = []
    with bd.conexion() as con:
        esquemas = bd.esquemas(con)
        for esquema in esquemas:
            pub = bd.publicacion(con, esquema["scheme_slug"])
            if not toca_republicar(pub):
                continue
            try:
                hechas.append(publicar(con, esquema, motivo="republicación automática"))
            except Exception:
                con.rollback()
                log.exception("no se pudo republicar %s", esquema["scheme_slug"])
    return hechas
