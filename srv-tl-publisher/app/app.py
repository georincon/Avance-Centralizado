"""Publicador de Trusted Lists — API HTTP.

Rutas públicas (las que consume un validador o la wallet):

    GET /TL/{esquema}/{n}.xml        una publicación concreta, INMUTABLE
    GET /TL/{esquema}/current.xml    la vigente, con ETag
    GET /LOTL/current.xml            atajo para el esquema LOTL
    GET /tl-api/v1/lists             qué esquemas hay y en qué estado están

Ruta de administración (exige API key):

    POST /tl-api/v1/publish/{esquema}
"""

import asyncio
import logging
import re
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Response
from fastapi.responses import FileResponse, PlainTextResponse
from fastapi.middleware.cors import CORSMiddleware

import admin
import anclas
import bd
import config
import definiciones
import espejo
import integracion
import publicacion

logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s %(levelname)s %(name)s %(message)s")
log = logging.getLogger("tl-publisher")

app = FastAPI(
    title="Civica Trusted List Publisher",
    description="Publica las Trusted Lists del ecosistema (ETSI TS 119 612).",
)

# `refrescarTrustStoreDeLector` (src/libs/trustList.ts, Fase 6.1) lee
# `/LOTL/current.json` y el `json_uri` que apunta -normalmente `/TL/RPREG/
# current.json`- por `fetch` DIRECTO desde el WebView de la app, con el
# origen fijo que Tauri le da (`http://tauri.localhost`). Sin esta cabecera
# el navegador descarta la respuesta como "blocked by CORS policy" aunque el
# servidor haya devuelto 200 -comprobado contra un dispositivo real: en curl
# no se nota, porque CORS lo aplica el navegador, no el servidor. `*` es
# seguro: una Trusted List es pública por definición (es la razón de existir
# de este servicio) y sólo se exponen rutas de lectura -la de publicar ya
# está bloqueada en HAProxy antes de llegar aquí.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["GET"],
)

TIPO_TSL = "application/vnd.etsi.tsl+xml"
TIPO_JWS = "application/tl+jwt"


def _xml(cuerpo: bytes, sha: str, inmutable: bool) -> Response:
    cabeceras = {
        # El ETag es el sha256 del XML publicado, no un hash calculado al vuelo:
        # así coincide con lo que guarda `published_lists` y un auditor puede
        # cruzar los dos sin descargar nada.
        "ETag": f'"sha256:{sha}"',
        "X-Content-Type-Options": "nosniff",
    }
    if inmutable:
        # Una publicación concreta no cambia jamás, así que se puede cachear
        # para siempre. Es lo que hace barato consultar el histórico.
        cabeceras["Cache-Control"] = "public, immutable, max-age=31536000"
    else:
        # `current.xml` sí cambia. `must-revalidate` obliga al cliente a
        # comprobar contra el ETag en vez de servir una lista caducada, que es
        # un fallo de validación, no una molestia.
        cabeceras["Cache-Control"] = "public, max-age=300, must-revalidate"
    return Response(content=cuerpo, media_type=TIPO_TSL, headers=cabeceras)


def _leer(pub):
    from pathlib import Path

    ruta = Path(pub["xml_path"])
    if not ruta.is_file():
        # La fila existe pero el fichero no: el volumen se perdió. Es un 500
        # honesto, no un 404 — decir "no existe" haría pensar que nunca se
        # publicó.
        raise HTTPException(
            500,
            f"la publicación {pub['scheme_slug']}/{pub['sequence_number']} está "
            "registrada pero su fichero no está en el almacén",
        )
    return ruta.read_bytes()


@app.get("/health")
def salud():
    try:
        with bd.conexion() as con:
            esquemas = bd.esquemas(con)
            vigentes = bd.todas_las_publicaciones_vigentes(con)
    except Exception as e:
        raise HTTPException(503, f"sin base de datos: {e}")
    return dict(status="ok", schemes=len(esquemas), published=len(vigentes))


@app.get("/tl-api/v1/lists")
def listas():
    with bd.conexion() as con:
        esquemas = bd.esquemas(con)
        salida = []
        for e in esquemas:
            pub = bd.publicacion(con, e["scheme_slug"])
            salida.append(dict(
                scheme=e["scheme_slug"],
                name=e["scheme_name"],
                tsl_type=e["tsl_type"],
                is_lotl=bool(e["is_lotl"]),
                published=pub is not None,
                sequence_number=pub["sequence_number"] if pub else None,
                issue_date=pub["issue_date"] if pub else None,
                next_update=pub["next_update"] if pub else None,
                sha256=pub["xml_sha256"] if pub else None,
                url=f"{config.BASE_URL}/TL/{e['scheme_slug']}/current.xml",
            ))
    return salida


@app.get("/tl-api/v1/integracion/verifier.env", response_class=PlainTextResponse)
def integracion_verifier():
    """Variables del verifier oficial: trust-validator y clases de documentos (publico)."""
    return integracion.verifier_env()


@app.get("/TL/{esquema}/current.xml")
def actual(esquema: str, if_none_match: str = Header(default=None)):
    with bd.conexion() as con:
        pub = bd.publicacion(con, esquema)
    if pub is None:
        raise HTTPException(404, f"el esquema '{esquema}' no tiene ninguna lista publicada")
    etag = f'"sha256:{pub["xml_sha256"]}"'
    if if_none_match == etag:
        return Response(status_code=304, headers={"ETag": etag})
    return _xml(_leer(pub), pub["xml_sha256"], inmutable=False)


@app.get("/TL/{esquema}/{secuencia}.xml")
def por_secuencia(esquema: str, secuencia: int):
    with bd.conexion() as con:
        pub = bd.publicacion(con, esquema, secuencia)
    if pub is None:
        raise HTTPException(404, f"no existe {esquema} secuencia {secuencia}")
    return _xml(_leer(pub), pub["xml_sha256"], inmutable=True)


@app.get("/LOTL/current.xml")
def lotl(if_none_match: str = Header(default=None)):
    return actual("LOTL", if_none_match)


def _leer_espejo(pub):
    """Bytes del espejo JWS de una publicación.

    Un 404 aquí, con una publicación que sí existe, sólo puede significar una
    cosa: esa lista se publicó antes de que existiera el espejo (Fase 1.8). Se
    dice explícitamente en vez de devolver un 404 pelado, que haría pensar que
    el esquema no existe.
    """
    from pathlib import Path

    ruta = espejo.ruta_de(Path(pub["xml_path"]))
    if not ruta.is_file():
        raise HTTPException(
            404,
            f"la publicación {pub['scheme_slug']}/{pub['sequence_number']} no tiene "
            "espejo JWS: es anterior a la Fase 1.8. Vuelve a publicar el esquema "
            "para generarlo.",
        )
    return ruta.read_bytes()


def _jws(cuerpo: bytes, sha: str, inmutable: bool) -> Response:
    cabeceras = {
        # El ETag del espejo es el sha256 del XML, NO el del propio JWS. Es
        # deliberado: las dos vistas describen la misma publicación, así que
        # comparten identificador de versión y un cliente puede revalidar las
        # dos con el mismo valor.
        "ETag": f'"sha256:{sha}"',
        "X-Content-Type-Options": "nosniff",
    }
    cabeceras["Cache-Control"] = ("public, immutable, max-age=31536000" if inmutable
                                  else "public, max-age=300, must-revalidate")
    return Response(content=cuerpo, media_type=TIPO_JWS, headers=cabeceras)


@app.get("/TL/{esquema}/current.json")
def actual_jws(esquema: str, if_none_match: str = Header(default=None)):
    """El espejo JWS de la lista vigente.

    El XML con firma XAdES sigue siendo el artefacto conforme; esto es la vista
    derivada para el dispositivo, que ya sabe verificar JWS porque es lo que usa
    en OpenID4VCI y OpenID4VP.
    """
    with bd.conexion() as con:
        pub = bd.publicacion(con, esquema)
    if pub is None:
        raise HTTPException(404, f"el esquema '{esquema}' no tiene ninguna lista publicada")
    etag = f'"sha256:{pub["xml_sha256"]}"'
    if if_none_match == etag:
        return Response(status_code=304, headers={"ETag": etag})
    return _jws(_leer_espejo(pub), pub["xml_sha256"], inmutable=False)


@app.get("/TL/{esquema}/{secuencia}.json")
def jws_por_secuencia(esquema: str, secuencia: int):
    with bd.conexion() as con:
        pub = bd.publicacion(con, esquema, secuencia)
    if pub is None:
        raise HTTPException(404, f"no existe {esquema} secuencia {secuencia}")
    return _jws(_leer_espejo(pub), pub["xml_sha256"], inmutable=True)


@app.get("/LOTL/current.json")
def lotl_jws(if_none_match: str = Header(default=None)):
    return actual_jws("LOTL", if_none_match)


@app.get("/tl-api/v1/lists/{esquema}/trust-anchors")
def trust_anchors(esquema: str, status: str = None):
    """Los anclas de confianza de una lista, en JSON.

    Se leen del XML PUBLICADO, no de la base de datos. La base tiene el estado
    que el operador está editando ahora; el XML tiene el estado que se firmó.
    Una API de trust anchors que respondiera desde la base estaría diciendo
    «confía en esto» sobre algo que nadie ha firmado.
    """
    with bd.conexion() as con:
        pub = bd.publicacion(con, esquema)
    if pub is None:
        raise HTTPException(404, f"el esquema '{esquema}' no tiene ninguna lista publicada")
    xml = _leer(pub)
    return dict(
        scheme=esquema,
        sequence_number=pub["sequence_number"],
        issue_date=pub["issue_date"],
        next_update=pub["next_update"],
        xml_uri=f"{config.BASE_URL}/TL/{esquema}/{pub['sequence_number']}.xml",
        xml_sha256=pub["xml_sha256"],
        services=anclas.extraer(xml, estado=status),
    )


@app.get("/tl-api/v1/anchors/lookup")
def lookup(ski: str = None, sha256: str = None):
    """Busca un ancla por SKI o por huella, en TODAS las listas vigentes.

    Es la consulta que hace un verificador cuando le llega un certificado y
    quiere saber si cuelga de algo publicado, sin saber de antemano en qué lista
    mirar.
    """
    if not ski and not sha256:
        raise HTTPException(400, "hace falta ski= o sha256=")

    encontrados = []
    with bd.conexion() as con:
        for pub in bd.todas_las_publicaciones_vigentes(con):
            try:
                xml = _leer(pub)
            except HTTPException:
                continue
            for servicio in anclas.extraer(xml):
                for cert in servicio["certificates"]:
                    if (ski and cert["ski"] == ski) or (sha256 and cert["sha256"] == sha256):
                        encontrados.append(dict(
                            scheme=pub["scheme_slug"],
                            sequence_number=pub["sequence_number"],
                            service_type=servicio["service_type"],
                            service_name=servicio["service_name"],
                            status=servicio["status"],
                            certificate=cert,
                        ))
    return dict(found=len(encontrados), matches=encontrados)


@app.get("/trust/schemerules/CIVICA")
def reglas():
    """Las reglas de comunidad a las que apuntan todas las listas.

    Si esto devolviera 404, `SchemeTypeCommunityRules` sería una promesa rota
    igual que un AIA que no responde. La diferencia entre hacer conforme una
    desviación y esconderla es que la primera se puede leer.
    """
    return Response(definiciones.reglas_del_esquema(), media_type="text/html; charset=utf-8")


@app.get("/trust/Svctype/{slug}")
def tipo_servicio(slug: str):
    """Definición de un tipo de servicio, incluida la desviación si la hay."""
    html = definiciones.tipo_de_servicio(slug)
    if html is None:
        raise HTTPException(404, f"tipo de servicio desconocido: {slug}")
    return Response(html, media_type="text/html; charset=utf-8")


@app.post("/tl-api/v1/publish/{esquema}")
def publicar_ahora(esquema: str, x_api_key: str = Header(default=None)):
    # Publicar consume un número de secuencia que no se puede reutilizar, así
    # que no puede quedar abierto. Sin API key configurada el endpoint responde
    # 503: es más seguro que arrancar sin protección y que nadie se dé cuenta.
    if not config.API_KEY:
        raise HTTPException(503, "PUBLISHER_API_KEY no está configurada")
    if x_api_key != config.API_KEY:
        raise HTTPException(401, "X-API-Key incorrecta o ausente")

    with bd.conexion() as con:
        esquemas = {e["scheme_slug"]: e for e in bd.esquemas(con)}
        if esquema not in esquemas:
            raise HTTPException(404, f"esquema desconocido: {esquema}")
        try:
            return publicacion.publicar(con, esquemas[esquema])
        except Exception as e:
            con.rollback()
            raise HTTPException(500, f"no se pudo publicar: {e}")


async def _bucle_republicacion():
    """Republica sola al 80% del intervalo de validez.

    Va dentro del proceso, no en un cron externo, para que no exista el estado
    "el servicio está arriba pero nadie republica" — que es justo el que deja
    listas caducadas sin que nada lo señale.
    """
    while True:
        try:
            hechas = await asyncio.to_thread(publicacion.publicar_lo_que_toque)
            for h in hechas:
                log.info("republicada %s -> secuencia %s", h["scheme"], h["sequence_number"])
        except Exception:
            log.exception("fallo en la pasada de republicación")
        await asyncio.sleep(config.SEGUNDOS_ENTRE_REVISIONES)


# ─── La administracion ───────────────────────────────────────────────────────
#
# El router vive en `admin.py` y cuelga de `/api/v1`. La interfaz (una SPA de
# un solo fichero) se sirve en `/admin`; la SPA calcula su propia base a partir
# de la URL, asi que el mismo HTML sirve bajo cualquier prefijo.
app.include_router(admin.router)

RUTA_ADMIN = Path(__file__).resolve().parent / "static" / "admin.html"


@app.get("/admin", include_in_schema=False)
def interfaz_de_administracion():
    """La SPA de administracion. Calcula su base con la URL, no con el servidor."""
    if not RUTA_ADMIN.is_file():
        raise HTTPException(404, "La interfaz de administracion no esta desplegada.")
    return FileResponse(RUTA_ADMIN, media_type="text/html; charset=utf-8")


def _asegurar_esquema_de_administracion():
    """Crea las tablas de la administracion si no existen.

    Idempotente: se ejecuta en cada arranque. El esquema esta en
    `app/db/admin-schema.sql`; el del TLP, en `db/script_db.sql`.
    """
    esquema = Path(__file__).resolve().parent / "db" / "admin-schema.sql"
    if not esquema.is_file():
        return
    sentencia = esquema.read_text()
    # Se trocea por un ';' al final de linea: un ';' suelto dentro de un
    # comentario partiria la sentencia y la commented-comentada se comeria el
    # CREATE TABLE siguiente.
    with bd.conexion() as con:
        with con.cursor() as c:
            for trozo in re.split(r";\s*\n", sentencia):
                trozo = trozo.strip()
                if not trozo:
                    continue
                # Se quitan los comentarios de linea: pueden dejar el CREATE
                # pegado a un comentario que lo neutraliza.
                limpio = "\n".join(
                    linea for linea in trozo.split("\n")
                    if not linea.strip().startswith("--")
                ).strip()
                if limpio.upper().startswith("CREATE TABLE"):
                    c.execute(limpio)
        con.commit()


@app.on_event("startup")
async def arranque():
    config.ALMACEN.mkdir(parents=True, exist_ok=True)
    _asegurar_esquema_de_administracion()
    asyncio.create_task(_bucle_republicacion())
    log.info("publicador listo; dominio %s, validez %s h, revisión cada %s s",
             config.DOMINIO, config.HORAS_VALIDEZ, config.SEGUNDOS_ENTRE_REVISIONES)
