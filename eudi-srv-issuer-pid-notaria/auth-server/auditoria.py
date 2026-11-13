"""Reporte de actividad al centro de auditoría (srv-auditoria). Ver srv-auditoria/docs/CONTRATO-EVENTOS-SERVICIOS.md.

Es SOLO monitoreo y nunca debe afectar la emisión: envío asíncrono (hilo daemon, cola acotada), tiempo máximo de 2 s y
toda excepción se traga. Variables: AUDITORIA_ACTIVA (por defecto "true"), AUDITORIA_URL (por defecto
http://srv-auditoria:8111), AUDITORIA_CLAVE_SERVICIOS (opcional, cabecera X-Api-Key).
Nunca se envían datos personales, tokens, códigos de oferta, PIN ni claves: solo identificadores de trámite y nombres.
El mismo archivo vive en backend/app/ y en auth-server/ (cada contenedor reporta lo suyo con el mismo «servicio»).
"""
import json
import os
import queue
import threading
import time
import urllib.request
import uuid
from datetime import datetime, timezone

_ACTIVA = os.environ.get("AUDITORIA_ACTIVA", "true").strip().lower() not in ("false", "0", "no", "off")
_URL = os.environ.get("AUDITORIA_URL", "http://srv-auditoria:8111").rstrip("/") + "/api/v1/servicios/eventos"
_CLAVE = os.environ.get("AUDITORIA_CLAVE_SERVICIOS", "")
_SERVICIO = {
    "rol": "emisor",
    "identificador": os.environ.get("AUDITORIA_SERVICIO_ID", "https://civica-desarrollo.avance.org.co/notaria-issuer/api"),
    "nombre": "Notaría (EUDI)",
    "proyecto": "eudi-srv-issuer-pid-notaria",
}
_cola = queue.Queue(maxsize=200)
_hilo = None
_candado = threading.Lock()


def _trabajar():
    while True:
        cuerpo = _cola.get()
        try:
            cab = {"Content-Type": "application/json"}
            if _CLAVE:
                cab["X-Api-Key"] = _CLAVE
            urllib.request.urlopen(urllib.request.Request(_URL, data=cuerpo, headers=cab, method="POST"), timeout=2).read()
        except Exception:
            pass


def _encolar(cuerpo):
    global _hilo
    with _candado:
        if _hilo is None:
            _hilo = threading.Thread(target=_trabajar, name="auditoria", daemon=True)
            _hilo.start()
    _cola.put_nowait(cuerpo)  # si la cola está llena lanza queue.Full: el llamador lo traga


def reportar(tipo, resultado, referencia=None, credencial_tipo=None, duracion_ms=None, tipo_error=None,
             codigo_error=None, mensaje_error=None, sujeto=None, detalle=None):
    """Fire-and-forget. Nunca lanza."""
    try:
        if not _ACTIVA:
            return
        ev = {
            "tipo": tipo, "resultado": resultado, "canal": "api",
            "ocurrido_en": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        }
        for k, v in (("referencia", referencia), ("credencial_tipo", credencial_tipo), ("duracion_ms", duracion_ms),
                     ("sujeto", sujeto), ("detalle", detalle)):
            if v not in (None, "", {}):
                ev[k] = v
        if resultado != "exitoso":
            for k, v in (("tipo_error", tipo_error), ("codigo_error", codigo_error), ("mensaje_error", mensaje_error)):
                if v:
                    ev[k] = v
        _encolar(json.dumps({"evento_id": str(uuid.uuid4()), "servicio": _SERVICIO, "evento": ev}).encode())
    except Exception:
        pass


def oferta(session_id, credenciales, flujo=None):
    """Una oferta de credencial quedó lista para la wallet."""
    try:
        if isinstance(credenciales, (list, tuple)):
            credenciales = ",".join(str(c) for c in credenciales)
        reportar("oferta_credencial", "en_proceso", referencia=str(session_id), credencial_tipo=credenciales,
                 detalle={"flujo": flujo} if flujo else None)
    except Exception:
        pass


def nota(**datos):
    """Deja en la petición en curso datos que ya conoce la ruta (session_id, credencial) para el reporte final."""
    try:
        from flask import g
        g.auditoria = {**getattr(g, "auditoria", {}), **datos}
    except Exception:
        pass


def nota_credencial(session_id, solicitud):
    try:
        nota(session_id=session_id, credencial=solicitud.get("credential_configuration_id"))
    except Exception:
        pass


def _clasificar(estado, codigo):
    if estado == 401 or codigo in ("invalid_token", "invalid_client", "unauthorized_client", "use_dpop_nonce", "invalid_dpop_proof"):
        return "autenticacion"
    if estado >= 500:
        return "emisor"
    if estado >= 400:
        return "validacion"
    return None


def _error_de(resp):
    try:
        if resp.mimetype == "application/json" and resp.content_length is not None and resp.content_length < 8192:
            cuerpo = json.loads(resp.get_data(as_text=True))
            if isinstance(cuerpo, dict):
                return cuerpo.get("error"), cuerpo.get("error_description")
    except Exception:
        pass
    return None, None


def instalar_backend(app):
    """Registra en el backend el reporte de emisión (POST /credential). Las ofertas se reportan con oferta()."""
    try:
        from flask import g, request

        @app.before_request
        def _aud_ini():
            try:
                g.aud_t0 = time.monotonic()
            except Exception:
                pass

        @app.after_request
        def _aud_fin(resp):
            try:
                if request.endpoint == "oidc.credential" and request.method == "POST":
                    nota_ = getattr(g, "auditoria", {})
                    ms = int((time.monotonic() - g.aud_t0) * 1000)
                    ok = 200 <= resp.status_code < 300
                    codigo, desc = (None, None) if ok else _error_de(resp)
                    cred = nota_.get("credencial")
                    if not cred:
                        cuerpo = request.get_json(silent=True)  # solo si la petición va en claro
                        cred = cuerpo.get("credential_configuration_id") if isinstance(cuerpo, dict) else None
                    reportar("emision_credencial", "exitoso" if ok else "fallido", referencia=nota_.get("session_id"),
                             credencial_tipo=cred, duracion_ms=ms,
                             tipo_error=None if ok else _clasificar(resp.status_code, codigo),
                             codigo_error=codigo or (None if ok else "http_%d" % resp.status_code),
                             mensaje_error=desc or ("Credencial emitida." if ok else None),
                             detalle={"http": resp.status_code, **({"diferida": True} if resp.status_code == 202 else {})})
            except Exception:
                pass
            return resp
    except Exception:
        pass


def instalar_token(app):
    """Registra en el servidor OIDC el reporte de autorización (POST /token)."""
    try:
        from flask import g, request

        @app.before_request
        def _aud_ini():
            try:
                if request.endpoint == "oidc_op.token":
                    g.aud_t0 = time.monotonic()
                    g.auditoria = {}
                    from application import request_manager  # lectura previa a la que hace la propia ruta
                    f = request.form
                    pedido = None
                    if f.get("grant_type") == "authorization_code" and f.get("code"):
                        pedido = request_manager.get_request_by_code(f.get("code"))
                    elif f.get("pre-authorized_code"):
                        pedido = request_manager.get_request_by_preauth_code_ref(f.get("pre-authorized_code"))
                    elif f.get("refresh_token"):
                        pedido = request_manager.get_request_by_refresh_token(refresh_token=f.get("refresh_token"))
                    if pedido is not None:
                        g.auditoria = {"session_id": pedido.session_id, "credencial": (pedido.scope or "").replace(" ", ",")}
            except Exception:
                pass

        @app.after_request
        def _aud_fin(resp):
            try:
                if request.endpoint == "oidc_op.token" and request.method == "POST":
                    nota_ = getattr(g, "auditoria", {})
                    g.aud_hecho = True
                    ms = int((time.monotonic() - g.aud_t0) * 1000)
                    ok = 200 <= resp.status_code < 300
                    codigo, desc = (None, None) if ok else _error_de(resp)
                    tipo_err = None
                    if not ok:
                        tipo_err = "autenticacion" if codigo in ("invalid_grant", "invalid_request") and resp.status_code == 400 \
                            else _clasificar(resp.status_code, codigo)
                    reportar("autorizacion_emision", "exitoso" if ok else "fallido", referencia=nota_.get("session_id"),
                             credencial_tipo=nota_.get("credencial") or None, duracion_ms=ms, tipo_error=tipo_err,
                             codigo_error=codigo or (None if ok else "http_%d" % resp.status_code),
                             mensaje_error=desc,
                             detalle={"grant_type": request.form.get("grant_type"), "http": resp.status_code})
            except Exception:
                pass
            return resp

        @app.teardown_request
        def _aud_excepcion(exc):  # con la app en modo debug, una excepción sin atender no pasa por after_request
            try:
                if exc is not None and request.endpoint == "oidc_op.token" and not getattr(g, "aud_hecho", False):
                    nota_ = getattr(g, "auditoria", {})
                    reportar("autorizacion_emision", "fallido", referencia=nota_.get("session_id"),
                             credencial_tipo=nota_.get("credencial") or None,
                             duracion_ms=int((time.monotonic() - g.aud_t0) * 1000), tipo_error="emisor",
                             codigo_error="server_error", mensaje_error="Error interno al canjear el token.",
                             detalle={"grant_type": request.form.get("grant_type"), "http": 500})
            except Exception:
                pass
    except Exception:
        pass
