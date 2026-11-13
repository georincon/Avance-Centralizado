"""API REST de órdenes de emisión para la plataforma de notarías (SIGNO).

La plataforma NO implementa OpenID4VCI: solo (a) crea una orden de emisión con los datos del documento,
(b) pide el enlace/QR de oferta que la persona abre con su wallet y (c) consulta el estado del proceso.
El emisor (este backend + el servidor de autorización) hace el resto del protocolo.

Rutas (públicas: /notaria-issuer/api/v1/…; el haproxy quita /notaria-issuer/api): /v1/documentos · /v1/ordenes · /v1/ordenes/<id>
· /v1/ordenes/<id>/oferta · /v1/ordenes/<id>/cancelar.  Autenticación: cabecera X-API-Key.
"""
import base64
import hmac
import io
import json
import logging
import urllib.parse
import uuid
from datetime import datetime, timedelta, timezone
from functools import wraps
from pathlib import Path

import segno
from flask import Blueprint, jsonify, request

from app import CONFIGURATION, auditoria, db_status_persistence as db, oidc_metadata, session_manager, validacion

ordenes = Blueprint("ordenes", __name__, url_prefix="/v1")
logger = logging.getLogger(__name__)

ARCHIVO_CLAVES = Path("/etc/eudiw/notaria/api-ordenes.json")  # {"CLIENTE": "clave", ...}
VIGENCIA_ORDEN = timedelta(hours=24)  # la orden vive 24 h; la oferta (QR) vive lo que dure la sesión del emisor
ESTADOS = ("CREADA", "OFERTA_GENERADA", "OFERTA_EXPIRADA", "EMITIDA", "CANCELADA", "EXPIRADA")


# ------------------------------------------------------------------------------------------------ base de datos
def crear_tablas():
    with db._pool.connection() as conn, conn.cursor() as cur:
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS notaria_orden_emision (
                id                UUID PRIMARY KEY,
                cliente           TEXT NOT NULL,
                referencia        TEXT NOT NULL,
                documento         TEXT NOT NULL,
                datos             JSONB NOT NULL,
                estado            TEXT NOT NULL DEFAULT 'CREADA'
                                  CHECK (estado IN ('CREADA','OFERTA_GENERADA','EMITIDA','CANCELADA')),
                session_id        TEXT,
                tx_code           TEXT,
                oferta_uri        TEXT,
                oferta_generada_en TIMESTAMPTZ,
                oferta_expira_en  TIMESTAMPTZ,
                emitida_en        TIMESTAMPTZ,
                creada_en         TIMESTAMPTZ NOT NULL DEFAULT now(),
                expira_en         TIMESTAMPTZ NOT NULL,
                actualizada_en    TIMESTAMPTZ NOT NULL DEFAULT now(),
                CONSTRAINT uq_orden_cliente_referencia UNIQUE (cliente, referencia)
            );
            """
        )
        cur.execute(
            """
            CREATE TABLE IF NOT EXISTS notaria_orden_evento (
                id        BIGSERIAL PRIMARY KEY,
                orden_id  UUID NOT NULL REFERENCES notaria_orden_emision(id) ON DELETE CASCADE,
                evento    TEXT NOT NULL,
                detalle   JSONB,
                creado_en TIMESTAMPTZ NOT NULL DEFAULT now()
            );
            """
        )
        cur.execute("CREATE INDEX IF NOT EXISTS idx_orden_session ON notaria_orden_emision(session_id);")
        cur.execute("CREATE INDEX IF NOT EXISTS idx_orden_evento_orden ON notaria_orden_evento(orden_id);")
        conn.commit()


def _evento(cur, orden_id, evento, detalle=None):
    cur.execute(
        "INSERT INTO notaria_orden_evento (orden_id, evento, detalle) VALUES (%s, %s, %s)",
        (orden_id, evento, db.to_jsonb(detalle)),
    )


def marcar_emitida(session_id):
    """Lo llama /credential cuando la wallet recibió el documento: cierra la orden asociada a esa sesión."""
    try:
        with db._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                "UPDATE notaria_orden_emision SET estado='EMITIDA', emitida_en=now(), actualizada_en=now() "
                "WHERE session_id=%s AND estado='OFERTA_GENERADA' RETURNING id",
                (str(session_id),),
            )
            fila = cur.fetchone()
            if fila:
                _evento(cur, fila[0], "EMITIDA", {"session_id": str(session_id)})
            conn.commit()
    except Exception:  # nunca debe romper la entrega del documento a la persona
        logger.exception("No se pudo marcar la orden como emitida (session_id=%s)", session_id)


# ------------------------------------------------------------------------------------------------ utilidades
def _error(status, codigo, mensaje, **extra):
    return jsonify({"error": {"codigo": codigo, "mensaje": mensaje, **extra}}), status


def _clientes():
    try:
        return json.loads(ARCHIVO_CLAVES.read_text())
    except (OSError, ValueError):
        return {}


def con_clave(fn):
    @wraps(fn)
    def envoltura(*a, **k):
        recibida = request.headers.get("X-API-Key", "")
        for cliente, clave in _clientes().items():
            if recibida and hmac.compare_digest(recibida.encode(), str(clave).encode()):
                return fn(cliente, *a, **k)
        return _error(401, "no_autenticado", "Falta la cabecera X-API-Key o la clave no es válida.")
    return envoltura


def _documentos():
    """Catálogo: id de credencial -> {vct, nombre, requeridos, opcionales} (solo lo que escribe quien pide)."""
    salida = {}
    for cid, cfg in oidc_metadata["credential_configurations_supported"].items():
        meta = cfg.get("credential_metadata", {})
        claims = [c for c in meta.get("claims", []) if c.get("source") == "user"]
        nombre = (meta.get("display") or cfg.get("display") or [{}])[0].get("name", cid)
        salida[cid] = {
            "documento": cid,
            "vct": cfg.get("vct"),
            "nombre": nombre,
            "campos_requeridos": [c["path"][0] for c in claims if c.get("mandatory")],
            "campos_opcionales": [c["path"][0] for c in claims if not c.get("mandatory")],
        }
    return salida


def _ahora():
    return datetime.now(timezone.utc)


def _iso(t):
    return t.astimezone(timezone.utc).isoformat().replace("+00:00", "Z") if t else None


COLUMNAS = ("id, cliente, referencia, documento, datos, estado, session_id, tx_code, oferta_uri, oferta_generada_en, "
            "oferta_expira_en, emitida_en, creada_en, expira_en, actualizada_en")


def _fila(r):
    c = COLUMNAS.split(", ")
    return dict(zip(c, r))


def _estado(o):
    """Estado que se informa: el guardado, ajustado por los vencimientos."""
    if o["estado"] in ("EMITIDA", "CANCELADA"):
        return o["estado"]
    if _ahora() >= o["expira_en"]:
        return "EXPIRADA"
    if o["estado"] == "OFERTA_GENERADA" and (o["oferta_expira_en"] <= _ahora() or not session_manager.get_session(o["session_id"])):
        return "OFERTA_EXPIRADA"
    return o["estado"]


def _vista(o, eventos=None):
    v = {
        "id": str(o["id"]),
        "referencia": o["referencia"],
        "documento": o["documento"],
        "estado": _estado(o),
        "creada_en": _iso(o["creada_en"]),
        "expira_en": _iso(o["expira_en"]),
        "oferta": None if not o["oferta_uri"] else {"generada_en": _iso(o["oferta_generada_en"]), "expira_en": _iso(o["oferta_expira_en"])},
        "emitida_en": _iso(o["emitida_en"]),
    }
    if eventos is not None:
        v["eventos"] = [{"evento": e, "detalle": d, "en": _iso(t)} for e, d, t in eventos]
    return v


def _cargar(cliente, orden_id):
    try:
        uuid.UUID(orden_id)
    except ValueError:
        return None
    with db._pool.connection() as conn, conn.cursor() as cur:
        cur.execute(f"SELECT {COLUMNAS} FROM notaria_orden_emision WHERE id=%s AND cliente=%s", (orden_id, cliente))
        r = cur.fetchone()
    return _fila(r) if r else None


# ------------------------------------------------------------------------------------------------ catálogo
@ordenes.route("/documentos", methods=["GET"])
@con_clave
def catalogo(_cliente):
    return jsonify({"documentos": list(_documentos().values())})


# ------------------------------------------------------------------------------------------------ (a) crear la orden
@ordenes.route("/ordenes", methods=["POST"])
@con_clave
def crear(cliente):
    cuerpo = request.get_json(silent=True)
    if not isinstance(cuerpo, dict):
        return _error(400, "cuerpo_invalido", "El cuerpo debe ser un objeto JSON.")
    documento, referencia, datos = cuerpo.get("documento"), cuerpo.get("referencia"), cuerpo.get("datos")
    catalogo_ = _documentos()
    if documento not in catalogo_:
        return _error(400, "documento_desconocido", "El campo «documento» no es uno de los que emite la notaría.", validos=list(catalogo_))
    if not isinstance(referencia, str) or not (1 <= len(referencia) <= 100):
        return _error(400, "referencia_invalida", "«referencia» es el identificador del trámite en su plataforma (1 a 100 caracteres).")
    if not isinstance(datos, dict):
        return _error(400, "datos_invalidos", "«datos» debe ser un objeto con los campos del documento.")

    esquema = catalogo_[documento]
    permitidos = set(esquema["campos_requeridos"]) | set(esquema["campos_opcionales"])
    desconocidos = sorted(set(datos) - permitidos)
    if desconocidos:
        return _error(400, "campos_desconocidos", "Hay campos que no se pueden enviar (no existen o los pone la notaría).", campos=desconocidos)
    planos = {}
    for k, v in datos.items():
        if isinstance(v, (dict, list)) or v is None:
            return _error(400, "datos_invalidos", f"El campo «{k}» debe ser un texto o un número.", campos=[k])
        planos[k] = str(v).strip()
    faltan = sorted(c for c in esquema["campos_requeridos"] if not planos.get(c))
    if faltan:
        return _error(400, "campos_faltantes", "Faltan campos obligatorios.", campos=faltan)
    problema = validacion.validar(planos, listas_cerradas=False)  # fechas, huella y tamaño del archivo (normaliza las fechas)
    if problema:
        return _error(422, "datos_no_validos", problema)

    orden_id = str(uuid.uuid4())
    with db._pool.connection() as conn, conn.cursor() as cur:
        cur.execute(
            f"SELECT {COLUMNAS} FROM notaria_orden_emision WHERE cliente=%s AND referencia=%s", (cliente, referencia)
        )
        previa = cur.fetchone()
        if previa:  # misma referencia: no se duplica (idempotencia). Si los datos difieren, es un conflicto.
            previa = _fila(previa)
            if previa["documento"] != documento or previa["datos"] != planos:
                return _error(409, "referencia_en_uso", "Ya existe una orden con esa referencia y datos distintos.", orden_id=str(previa["id"]))
            return jsonify(_vista(previa)), 200
        cur.execute(
            "INSERT INTO notaria_orden_emision (id, cliente, referencia, documento, datos, expira_en) VALUES (%s,%s,%s,%s,%s,%s)",
            (orden_id, cliente, referencia, documento, db.to_jsonb(planos), _ahora() + VIGENCIA_ORDEN),
        )
        _evento(cur, orden_id, "CREADA", {"cliente": cliente})
        conn.commit()
    logger.info("[orden] %s creada por %s (%s, ref %s)", orden_id, cliente, documento, referencia)
    return jsonify(_vista(_cargar(cliente, orden_id))), 201


# ------------------------------------------------------------------------------------------------ (b) oferta / QR
def _nueva_oferta(o):
    """Abre una sesión de preautorización en el emisor y arma la oferta (OpenID4VCI, grant preautorizado con PIN)."""
    from app.preauthorization import request_preauth_token

    # Los campos son planos (texto): no hace falta el formateador del formulario, que depende de la sesión web.
    datos = {**o["datos"], "issuing_country": "FC"}  # "FC": el país del flujo por formulario, como en el portal
    session_id = request_preauth_token(scope=o["documento"])
    session_manager.update_authorization_details(
        session_id=session_id,
        authorization_details=[{"type": "openid_credential", "credential_configuration_id": o["documento"]}],
    )
    session_manager.update_user_data(session_id=session_id, user_data=datos)
    sesion = session_manager.get_session(session_id=session_id)

    frontend = CONFIGURATION["frontend"]["frontends_config"][CONFIGURATION["frontend"]["default"]]["url"]
    oferta = {
        "credential_issuer": frontend,
        "credential_configuration_ids": [o["documento"]],
        "grants": {
            "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                "pre-authorized_code": sesion.pre_authorized_code,
                "tx_code": {"length": 5, "input_mode": "numeric", "description": "Código de un solo uso que le entrega la notaría."},
                "issuer_state": session_id,
            }
        },
    }
    uri = f"{CONFIGURATION['credential_offer_scheme']}credential_offer?credential_offer=" + urllib.parse.quote(json.dumps(oferta), safe=":/")
    auditoria.oferta(session_id, o["documento"], "api_ordenes", datos=o.get("datos"))
    return session_id, str(sesion.tx_code), uri, sesion.expiry_time


def _qr(uri):
    salida = io.BytesIO()
    segno.make(uri).save(salida, kind="png", scale=6, border=2)
    return "data:image/png;base64," + base64.b64encode(salida.getvalue()).decode()


@ordenes.route("/ordenes/<orden_id>/oferta", methods=["GET"])
@con_clave
def oferta(cliente, orden_id):
    o = _cargar(cliente, orden_id)
    if not o:
        return _error(404, "orden_no_encontrada", "No existe una orden con ese id para su cliente.")
    estado = _estado(o)
    if estado == "EMITIDA":
        return _error(409, "orden_emitida", "El documento ya fue entregado a la wallet; esta orden está cerrada.", estado=estado)
    if estado == "CANCELADA":
        return _error(409, "orden_cancelada", "La orden fue cancelada.", estado=estado)
    if estado == "EXPIRADA":
        return _error(410, "orden_expirada", "La orden venció (24 h). Cree una nueva.", estado=estado)

    regenerada = False
    if estado in ("CREADA", "OFERTA_EXPIRADA"):
        regenerada = estado == "OFERTA_EXPIRADA"
        session_id, tx_code, uri, vence = _nueva_oferta(o)
        with db._pool.connection() as conn, conn.cursor() as cur:
            cur.execute(
                "UPDATE notaria_orden_emision SET estado='OFERTA_GENERADA', session_id=%s, tx_code=%s, oferta_uri=%s, "
                "oferta_generada_en=now(), oferta_expira_en=%s, actualizada_en=now() WHERE id=%s",
                (session_id, tx_code, uri, vence, orden_id),
            )
            _evento(cur, orden_id, "OFERTA_REGENERADA" if regenerada else "OFERTA_GENERADA", {"expira_en": _iso(vence)})
            conn.commit()
        o = _cargar(cliente, orden_id)
    return jsonify({
        "orden_id": str(o["id"]),
        "estado": _estado(o),
        "enlace": o["oferta_uri"],          # openid-credential-offer://… (mismo teléfono: se abre con la wallet)
        "qr_png_base64": _qr(o["oferta_uri"]),  # data:image/png;base64,… (otro dispositivo: se escanea)
        "tx_code": o["tx_code"],            # PIN de 5 dígitos: se entrega a la persona por otro medio
        "expira_en": _iso(o["oferta_expira_en"]),
        "regenerada": regenerada,
    })


# ------------------------------------------------------------------------------------------------ (c) estado
@ordenes.route("/ordenes/<orden_id>", methods=["GET"])
@con_clave
def estado(cliente, orden_id):
    o = _cargar(cliente, orden_id)
    if not o:
        return _error(404, "orden_no_encontrada", "No existe una orden con ese id para su cliente.")
    with db._pool.connection() as conn, conn.cursor() as cur:
        cur.execute("SELECT evento, detalle, creado_en FROM notaria_orden_evento WHERE orden_id=%s ORDER BY id", (orden_id,))
        eventos = cur.fetchall()
    return jsonify(_vista(o, eventos))


@ordenes.route("/ordenes/<orden_id>/cancelar", methods=["POST"])
@con_clave
def cancelar(cliente, orden_id):
    o = _cargar(cliente, orden_id)
    if not o:
        return _error(404, "orden_no_encontrada", "No existe una orden con ese id para su cliente.")
    if o["estado"] in ("EMITIDA", "CANCELADA"):
        return _error(409, "estado_final", f"La orden ya está {o['estado']}.", estado=o["estado"])
    with db._pool.connection() as conn, conn.cursor() as cur:
        cur.execute("UPDATE notaria_orden_emision SET estado='CANCELADA', actualizada_en=now() WHERE id=%s", (orden_id,))
        _evento(cur, orden_id, "CANCELADA", {"motivo": str((request.get_json(silent=True) or {}).get("motivo", ""))[:300]})
        conn.commit()
    return jsonify(_vista(_cargar(cliente, orden_id)))
