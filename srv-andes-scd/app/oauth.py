"""Servidor de autorizacion OAuth2 (codigo + PKCE, PAR y RAR) que usa la wallet para firmar.

La wallet (eudi-lib-jvm-rqes-csc-kt) hace, en este orden:
  1. GET  /csc/v2/info                    -> anuncia este servidor como `oauth2Issuer`
  2. GET  /.well-known/oauth-authorization-server
  3. POST /oauth2/par                     -> request_uri (scope "service", o authorization_details)
  4. navegador -> GET/POST /oauth2/authorize?request_uri=...  (aqui el firmante se identifica)
  5. rqes://oauth/callback?code=...       (la wallet recibe el codigo)
  6. POST /oauth2/token                   -> token (con `authorization_details` si firma)
"""

from __future__ import annotations

import json
from urllib.parse import quote, urlencode

import segno
from fastapi import APIRouter, Form, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse

from . import ca, config, db, paginas, seguridad, servicio, verificador

router = APIRouter()
_autoridad: ca.Autoridad | None = None


def fijar_autoridad(a: ca.Autoridad) -> None:
    global _autoridad
    _autoridad = a


def metadata() -> dict:
    base = config.URL_PUBLICA
    return {
        "issuer": base,
        "authorization_endpoint": f"{base}/oauth2/authorize",
        "token_endpoint": f"{base}/oauth2/token",
        "pushed_authorization_request_endpoint": f"{base}/oauth2/par",
        "response_types_supported": ["code"],
        "grant_types_supported": ["authorization_code", "client_credentials"],
        "code_challenge_methods_supported": ["S256"],
        "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post", "none"],
        "scopes_supported": ["service", "credential"],
        "authorization_details_types_supported": [config.TIPO_AUTORIZACION_CREDENCIAL],
        "require_pushed_authorization_requests": False,
    }


def _error(codigo: str, descripcion: str, estado: int = 400) -> JSONResponse:
    return JSONResponse({"error": codigo, "error_description": descripcion}, status_code=estado)


@router.get("/.well-known/oauth-authorization-server")
async def well_known() -> dict:
    return metadata()


def _detalles_desde(p: dict) -> list[dict] | None:
    """authorization_details (RAR) o, si la wallet no lo usa, los parametros sueltos del scope."""
    crudo = p.get("authorization_details")
    if crudo:
        try:
            detalles = json.loads(crudo) if isinstance(crudo, str) else crudo
        except json.JSONDecodeError:
            return None
        return detalles if isinstance(detalles, list) else None
    if p.get("credentialID") or p.get("hashes"):
        huellas = [h for h in (p.get("hashes") or "").split(",") if h]
        return [{
            "type": config.TIPO_AUTORIZACION_CREDENCIAL,
            "credentialID": p.get("credentialID", ""),
            "numSignatures": int(p.get("numSignatures") or len(huellas) or 1),
            "documentDigests": [
                {"hash": seguridad.b64_estandar(h), "label": "documento", "hashType": "DTBSR"}
                for h in huellas
            ],
            "hashAlgorithmOID": p.get("hashAlgorithmOID", ""),
        }]
    return None


def _validar_solicitud(p: dict) -> str | None:
    if p.get("response_type") != "code":
        return "response_type debe ser code"
    if not p.get("client_id") or not seguridad.comparar(p["client_id"], config.CLIENT_ID):
        return "client_id desconocido"
    if p.get("redirect_uri") not in config.REDIRECT_URIS:
        return "redirect_uri no registrado"
    if p.get("code_challenge_method") != "S256" or not p.get("code_challenge"):
        return "se exige PKCE con S256"
    if not p.get("scope") and not (p.get("authorization_details") or p.get("credentialID")):
        return "falta scope o authorization_details"
    return None


@router.post("/oauth2/par")
async def par(request: Request):
    form = dict((await request.form()).items())
    if not seguridad.cliente_autenticado(request, form, exigir_secreto=False):
        return _error("invalid_client", "cliente no autenticado", 401)
    form.setdefault("client_id", config.CLIENT_ID)
    problema = _validar_solicitud(form)
    if problema:
        return _error("invalid_request", problema)
    form.pop("client_secret", None)
    request_uri = "urn:ietf:params:oauth:request_uri:" + seguridad.nuevo_secreto(24)
    with db.transaccion() as c:
        c.execute(
            "INSERT INTO solicitudes (id, parametros, expira) VALUES (?,?,?)",
            (request_uri, db.dumps(form), db.ahora() + config.TTL_PAR),
        )
    return JSONResponse({"request_uri": request_uri, "expires_in": config.TTL_PAR}, status_code=201)


def _cargar_solicitud(rid: str) -> dict | None:
    with db.transaccion() as c:
        fila = c.execute("SELECT parametros, expira FROM solicitudes WHERE id = ?", (rid,)).fetchone()
    if not fila or fila["expira"] < db.ahora():
        return None
    return db.loads(fila["parametros"])


def _guardar_solicitud(rid: str, p: dict) -> None:
    with db.transaccion() as c:
        c.execute("UPDATE solicitudes SET parametros = ? WHERE id = ?", (db.dumps(p), rid))


def _poner_sesion(respuesta, documento: str, nombre: str):
    """Recuerda la identificacion en este navegador unos minutos (cookie firmada, solo para este servicio)."""
    respuesta.set_cookie(
        seguridad.COOKIE_SESION, seguridad.crear_sesion(documento, nombre), max_age=seguridad.TTL_SESION, httponly=True,
        secure=config.URL_PUBLICA.startswith("https://"), samesite="lax", path=f"/{config.PREFIJO}",
    )
    return respuesta


def _es_movil(request: Request) -> bool:
    return any(m in request.headers.get("user-agent", "") for m in ("Android", "iPhone", "iPad", "Mobile"))


def _pagina_identificacion(request: Request, rid: str, p: dict, detalles, error: str | None = None) -> HTMLResponse:
    """Con PID: pide al verifier del servicio una presentacion (una por solicitud).

    En el telefono (mismo dispositivo que la wallet) se muestra el boton "Abrir mi wallet" y se pide al verifier que la wallet
    abra /oauth2/retorno al terminar; desde otro dispositivo (un PC) se muestra el QR y la pagina consulta el resultado.
    """
    mismo = _es_movil(request)
    if p.get("_tx") and p.get("_modo") != ("mismo" if mismo else "otro"):
        p.pop("_tx", None)  # se abrio desde otro tipo de dispositivo que la vez anterior: otra presentacion
    if not p.get("_tx"):
        retorno = None
        if mismo:
            retorno = f"{config.URL_PUBLICA}/oauth2/retorno?rid={quote(rid, safe='')}&response_code={{RESPONSE_CODE}}"
        try:
            t = verificador.crear_solicitud(retorno)
        except verificador.VerificadorError as e:
            return HTMLResponse(paginas.error_simple(
                "Servicio de identificación no disponible", f"No se pudo pedir tu PID: {e}. Inténtalo en unos minutos."), 503)
        p["_tx"], p["_enlace"], p["_modo"] = t["transaccion"], t["enlace"], ("mismo" if mismo else "otro")
        _guardar_solicitud(rid, p)
    qr = segno.make(p["_enlace"], error="m").svg_data_uri(scale=5, border=2)
    return HTMLResponse(paginas.identificacion_pid(rid, detalles, qr, p["_enlace"], error, mismo_dispositivo=mismo))


@router.get("/oauth2/identidad")
def identidad(rid: str):
    """La pagina de identificacion pregunta aqui si ya se presento el PID."""
    p = _cargar_solicitud(rid)
    if p is None:
        return JSONResponse({"estado": "error", "motivo": "La solicitud venció. Vuelve a la wallet e inténtalo otra vez."})
    if p.get("identidad"):
        return {"estado": "listo", "nombre": p["identidad"]["nombre"]}
    if not p.get("_tx"):
        return {"estado": "error", "motivo": "No hay una presentación pendiente."}
    if p.get("_modo") == "mismo":
        return {"estado": "esperando"}  # en el mismo dispositivo la respuesta se lee en /oauth2/retorno, con su response_code
    try:
        res = verificador.resultado(p["_tx"])
    except verificador.VerificadorError:
        return {"estado": "esperando"}  # fallo momentaneo del verifier: se vuelve a preguntar
    if res["estado"] == "listo":
        try:
            documento, nombre = verificador.identidad_de(res)
        except verificador.VerificadorError as e:
            return {"estado": "error", "motivo": str(e)}
        p["identidad"] = {"documento": documento, "nombre": nombre}
        _guardar_solicitud(rid, p)
        return {"estado": "listo", "nombre": nombre}
    return res if res["estado"] == "error" else {"estado": "esperando"}


@router.get("/oauth2/retorno", response_class=HTMLResponse)
def retorno(rid: str, response_code: str = ""):
    """La wallet abre aqui el navegador al terminar de presentar el PID (mismo dispositivo)."""
    p = _cargar_solicitud(rid)
    if p is None:
        return HTMLResponse(paginas.error_simple("Solicitud vencida", "Vuelve a la wallet e inténtalo otra vez."), 400)
    if not p.get("identidad"):
        if not p.get("_tx") or not response_code:
            return HTMLResponse(paginas.error_simple("Falta la respuesta de la wallet", "Vuelve a empezar la firma desde la wallet."), 400)
        try:
            res = verificador.resultado(p["_tx"], response_code)
            if res["estado"] != "listo":
                motivo = res.get("motivo") or "La wallet todavía no presentó el PID."
                return HTMLResponse(paginas.error_simple("No se pudo verificar tu PID", motivo), 400)
            documento, nombre = verificador.identidad_de(res)
        except verificador.VerificadorError as e:
            return HTMLResponse(paginas.error_simple("No se pudo verificar tu PID", str(e)), 400)
        p["identidad"] = {"documento": documento, "nombre": nombre}
        _guardar_solicitud(rid, p)
    return _poner_sesion(HTMLResponse(paginas.retorno_continuar(rid, p["identidad"]["nombre"])),
                         p["identidad"]["documento"], p["identidad"]["nombre"])


@router.get("/oauth2/authorize", response_class=HTMLResponse)
def autorizar_pagina(request: Request):
    q = dict(request.query_params)
    if q.get("request_uri"):
        rid = q["request_uri"]
        p = _cargar_solicitud(rid)
        if p is None:
            return HTMLResponse(paginas.error_simple("Solicitud vencida", "Vuelve a la wallet e inténtalo otra vez."), 400)
    else:
        problema = _validar_solicitud(q)
        if problema:
            return HTMLResponse(paginas.error_simple("Solicitud no válida", problema), 400)
        rid = "urn:ietf:params:oauth:request_uri:" + seguridad.nuevo_secreto(24)
        with db.transaccion() as c:
            c.execute(
                "INSERT INTO solicitudes (id, parametros, expira) VALUES (?,?,?)",
                (rid, db.dumps(q), db.ahora() + config.TTL_PAR),
            )
        p = q
    if config.AUTENTICACION == "pid":
        sesion = None if request.query_params.get("nueva") else seguridad.leer_sesion(request.cookies.get(seguridad.COOKIE_SESION))
        if sesion:
            # Ya se identifico con su PID hace pocos minutos: no se pide otra vez, solo confirmar.
            p["identidad"] = {"documento": sesion["documento"], "nombre": sesion["nombre"]}
            _guardar_solicitud(rid, p)
            volver = f"{config.URL_PUBLICA}/oauth2/authorize?" + urlencode({"client_id": config.CLIENT_ID, "request_uri": rid, "nueva": "1"})
            return HTMLResponse(paginas.confirmar_sesion(rid, sesion["nombre"], _detalles_desde(p), volver))
        respuesta = _pagina_identificacion(request, rid, p, _detalles_desde(p))
        if request.query_params.get("nueva"):
            respuesta.delete_cookie(seguridad.COOKIE_SESION, path=f"/{config.PREFIJO}")
        return respuesta
    return HTMLResponse(paginas.autorizacion(rid, _detalles_desde(p), p.get("scope", "")))


@router.post("/oauth2/authorize", response_class=HTMLResponse)
def autorizar_envio(request: Request, rid: str = Form(...), documento: str = Form(""), pin: str = Form(""),
                    nombre: str = Form("")):
    p = _cargar_solicitud(rid)
    if p is None:
        return HTMLResponse(paginas.error_simple("Solicitud vencida", "Vuelve a la wallet e inténtalo otra vez."), 400)
    detalles = _detalles_desde(p)
    if config.AUTENTICACION == "pid":
        # El documento y el nombre salen del PID que valido el verifier, no de lo que llegue en el formulario.
        identidad_pid = p.get("identidad")
        if not identidad_pid:
            return _pagina_identificacion(request, rid, p, detalles, "Primero presenta tu PID desde la wallet.")
        documento = identidad_pid["documento"]
        try:
            servicio.registrar_desde_pid(documento, identidad_pid["nombre"])
        except servicio.AutenticacionError as e:
            return HTMLResponse(paginas.error_simple("No se pudo registrar tu identidad", str(e)), 400)
    else:
        try:
            nombre_real, _nuevo = servicio.autenticar_o_registrar(documento, pin, nombre)
        except servicio.AutenticacionError as e:
            return HTMLResponse(paginas.autorizacion(rid, detalles, p.get("scope", ""), str(e), documento, nombre), 200)

    cid = servicio.asegurar_credencial(_autoridad, documento.strip())
    if detalles and detalles[0].get("credentialID") not in (cid, "", None):
        if config.AUTENTICACION == "pid":
            return HTMLResponse(paginas.error_simple(
                "No puedes firmar con esa credencial",
                "El PID que presentaste no corresponde al titular de esa credencial de firma."), 403)
        return HTMLResponse(paginas.autorizacion(
            rid, detalles, p.get("scope", ""), "Esa credencial de firma no es tuya.", documento, nombre), 200)

    codigo = seguridad.nuevo_secreto(24)
    with db.transaccion() as c:
        c.execute("DELETE FROM solicitudes WHERE id = ?", (rid,))
        c.execute(
            "INSERT INTO codigos (codigo, documento, parametros, expira) VALUES (?,?,?,?)",
            (codigo, documento.strip(),
             db.dumps({**{k: v for k, v in p.items() if not k.startswith("_") and k != "identidad"}, "detalles": detalles}),
             db.ahora() + config.TTL_CODIGO),
        )
    retorno = {"code": codigo}
    if p.get("state"):
        retorno["state"] = p["state"]
    redireccion = RedirectResponse(f"{p['redirect_uri']}?{urlencode(retorno)}", status_code=302)
    if config.AUTENTICACION == "pid" and p.get("identidad"):
        _poner_sesion(redireccion, p["identidad"]["documento"], p["identidad"]["nombre"])
    return redireccion


@router.post("/oauth2/token")
async def token(request: Request):
    form = dict((await request.form()).items())
    grant = form.get("grant_type")

    if grant == "client_credentials":
        if not seguridad.cliente_autenticado(request, form, exigir_secreto=True):
            return _error("invalid_client", "cliente no autenticado", 401)
        t = seguridad.nuevo_secreto(32)
        with db.transaccion() as c:
            c.execute(
                "INSERT INTO tokens (token, tipo, scope, expira) VALUES (?,?,?,?)",
                (t, "servicio", form.get("scope") or "service", db.ahora() + config.TTL_TOKEN),
            )
        return {"access_token": t, "token_type": "Bearer", "expires_in": config.TTL_TOKEN}

    if grant != "authorization_code":
        return _error("unsupported_grant_type", "se admiten authorization_code y client_credentials")

    if not seguridad.cliente_autenticado(request, form, exigir_secreto=False):
        return _error("invalid_client", "cliente no autenticado", 401)
    with db.transaccion() as c:
        fila = c.execute("SELECT * FROM codigos WHERE codigo = ?", (form.get("code", ""),)).fetchone()
        if fila is None or fila["usado"] or fila["expira"] < db.ahora():
            return _error("invalid_grant", "codigo desconocido, usado o vencido")
        c.execute("UPDATE codigos SET usado = 1 WHERE codigo = ?", (fila["codigo"],))
    p = db.loads(fila["parametros"])
    if form.get("redirect_uri") != p.get("redirect_uri"):
        return _error("invalid_grant", "redirect_uri no coincide")
    verificador = form.get("code_verifier", "")
    if not verificador or not seguridad.comparar(seguridad.pkce_s256(verificador), p["code_challenge"]):
        return _error("invalid_grant", "code_verifier no coincide con code_challenge")

    detalles = p.get("detalles")
    t = seguridad.nuevo_secreto(32)
    with db.transaccion() as c:
        c.execute(
            "INSERT INTO tokens (token, tipo, documento, scope, detalles, expira) VALUES (?,?,?,?,?,?)",
            (t, "usuario", fila["documento"], p.get("scope", ""), db.dumps(detalles) if detalles else None,
             db.ahora() + config.TTL_TOKEN),
        )
    respuesta = {"access_token": t, "token_type": "Bearer", "expires_in": config.TTL_TOKEN}
    if detalles:
        # La wallet exige estos campos en la respuesta para saber que se autorizo.
        respuesta["credentialID"] = detalles[0].get("credentialID")
        respuesta["authorization_details"] = [
            {
                "type": d.get("type", config.TIPO_AUTORIZACION_CREDENCIAL),
                "credentialID": d.get("credentialID"),
                "numSignatures": d.get("numSignatures", len(d.get("documentDigests") or []) or 1),
                "documentDigests": d.get("documentDigests") or [],
                "hashAlgorithmOID": d.get("hashAlgorithmOID", ""),
            }
            for d in detalles
        ]
    return respuesta
