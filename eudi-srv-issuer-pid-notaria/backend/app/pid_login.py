"""Identificación con la wallet (inicio de sesión) por el PID, y datos de la persona para los formularios.

La persona se identifica presentando su PID (SD-JWT VC, vct urn:eudi:pid:1) a través del verifier de la
notaría (eudi-srv-verifier-endpoint, API /ui/presentations/v2). El verifier ya valida la firma del emisor, que
el emisor esté en la lista de confianza (PID), el estado y la prueba de posesión: aquí solo se leen los datos.

Con esos datos:
  * los campos personales de cada documento (nombre, documento…) llegan precargados y bloqueados en el
    formulario (`issuer_config.persona_claims` de cada documento), y
  * al enviar el formulario se vuelven a poner desde la sesión, así que no se pueden alterar desde el navegador.

Se pide lo mínimo: número de documento, nombres, apellidos y `age_equal_or_over.18` (mayoría de edad), no la fecha
de nacimiento.
"""
import base64
import io
import json
import logging
import secrets
from urllib.parse import urlparse

import requests
import segno
from flask import Blueprint, redirect, request, session, url_for

import app as _app
from app import CONFIGURATION
from app.redirect_func import post_redirect_with_payload

logger = logging.getLogger(__name__)
login = Blueprint("login", __name__, url_prefix="/")

PID_VCT = "urn:eudi:pid:1"
DATOS_PID = ["personal_administrative_number", "given_name", "family_name", ["age_equal_or_over", "18"]]
ERRORES = {
    "FailedToRetrieve request": "la wallet no pudo leer la solicitud",
    "Wallet failed to post response": "la wallet respondió, pero el verifier rechazó la presentación",
    "Presentation expired": "la solicitud venció",
    "Attestation status check failed": "el documento fue anulado o suspendido por quien lo emitió",
}


def _cfg():
    return CONFIGURATION.get("pid_login") or {}


def exigido() -> bool:
    return bool(_cfg().get("required"))


def persona_actual():
    return session.get("persona")


# --- verifier -------------------------------------------------------------------------------------------
def _api(ruta, **kw):
    base = _cfg()["verifier_api"].rstrip("/")
    return requests.request(kw.pop("method", "GET"), base + ruta, timeout=20,
                            headers={"Accept": "application/json", **kw.pop("headers", {})}, **kw)


def _uso_previsto():
    r = _api("/ui/intended-uses")
    usos = r.json().get("intended_uses") or []
    return usos[0]["intended_use_id"] if usos else None


def _crear_solicitud(redireccion=None):
    cuerpo = {
        "dcql_query": {"credentials": [{
            "id": "doc", "format": "dc+sd-jwt", "meta": {"vct_values": [PID_VCT]},
            "claims": [{"path": [d] if isinstance(d, str) else d} for d in DATOS_PID],
        }]},
        "nonce": secrets.token_urlsafe(16),
        "profile": "haip",
        "intended_use_id": _uso_previsto(),
        "request_uri_method": "get",
    }
    if redireccion:
        cuerpo["wallet_response_redirect_uri_template"] = f"{redireccion}?response_code={{RESPONSE_CODE}}"
    r = _api("/ui/presentations/v2", method="POST", json=cuerpo)
    if r.status_code != 200:
        raise RuntimeError(f"el verifier no creó la transacción (HTTP {r.status_code}: {r.text[:200]})")
    d = r.json()
    return d["transaction_id"], d["authorization_request_uri"]


def _leer_sd_jwt(presentado: str) -> dict:
    jwt, *partes = presentado.split("~")

    def b64(s):
        return json.loads(base64.urlsafe_b64decode(s + "=" * (-len(s) % 4)))

    carga = b64(jwt.split(".")[1])
    datos = {}
    for d in partes[:-1]:
        if d:
            v = b64(d)
            if len(v) == 3 and v[1] not in datos:
                datos[v[1]] = v[2]
    return {"emisor": carga.get("iss"), "datos": datos}


def _resultado(transaccion, response_code=None):
    """{'estado': 'esperando'} | {'estado': 'listo', 'persona': {...}} | {'estado': 'error', 'motivo': str}"""
    params = {"response_code": response_code} if response_code else None
    r = _api(f"/ui/presentations/{transaccion}", params=params)
    if r.status_code == 404:
        return {"estado": "error", "motivo": "la solicitud ya no existe en el verifier"}
    if r.status_code == 400:
        cuerpo = r.json() if r.text else {}
        if cuerpo.get("error") == "InvalidResponseCode":
            return {"estado": "error", "motivo": "código de respuesta inválido"}
        ev = _api(f"/ui/presentations/{transaccion}/events").json().get("events", [])
        fallo = next((e for e in ev if e.get("event") in ERRORES), None)
        if not fallo:
            return {"estado": "esperando"}
        causa = json.dumps(fallo.get("cause", ""))
        motivo = ("el documento fue anulado o suspendido por quien lo emitió"
                  if "status" in causa.lower() else ERRORES[fallo["event"]])
        return {"estado": "error", "motivo": motivo}
    cuerpo = r.json()
    if cuerpo.get("error"):
        return {"estado": "error", "motivo": "no se aceptó compartir los datos"
                if cuerpo["error"] == "access_denied" else cuerpo.get("error_description") or cuerpo["error"]}
    presentado = (cuerpo.get("vp_token") or {}).get("doc")
    presentado = presentado[0] if isinstance(presentado, list) and presentado else presentado
    if not isinstance(presentado, str):
        return {"estado": "error", "motivo": "la respuesta no trae el documento pedido"}
    leido = _leer_sd_jwt(presentado)
    d = leido["datos"]
    if not (d.get("personal_administrative_number") and d.get("given_name") and d.get("family_name")):
        return {"estado": "error", "motivo": "la wallet no compartió los datos necesarios para identificarte"}
    if d.get("18") is not True:
        return {"estado": "error", "motivo": "para otorgar estos actos ante notario tienes que ser mayor de edad"}
    return {"estado": "listo", "persona": {
        "documento": str(d["personal_administrative_number"]), "nombres": d["given_name"],
        "apellidos": d["family_name"], "emisor_pid": leido["emisor"],
    }}


# --- datos de la persona en los formularios --------------------------------------------------------------
def _valores(p):
    return {
        "nombre_completo": f"{p['nombres']} {p['apellidos']}", "nombres": p["nombres"], "apellidos": p["apellidos"],
        "documento": p["documento"], "emisor_pid": p.get("emisor_pid") or "",
        "medio_identificacion_pid": "Documento de identidad digital (PID) presentado con la wallet"
                                    + (f", emitido por {p['emisor_pid']}" if p.get("emisor_pid") else ""),
    }


def _campos_persona(credenciales):
    """{campo: valor} que salen del PID para los documentos pedidos ({} si no hay persona identificada)."""
    p = persona_actual()
    if not p:
        return {}
    valores = _valores(p)
    out = {}
    for cred in credenciales:
        cfg = _app.oidc_metadata.get("credential_configurations_supported", {}).get(cred, {})
        for campo, token in (cfg.get("issuer_config", {}).get("persona_claims") or {}).items():
            out[campo] = valores[token]
    return out


def precargar(atributos: dict, credenciales):
    """Pone los datos del PID en los campos del formulario: precargados y de solo lectura."""
    for campo, valor in _campos_persona(credenciales).items():
        if campo in atributos and isinstance(atributos[campo], dict):
            atributos[campo].update({"filled_value": valor, "from_pid": True})
    return atributos


def forzar(form_data: dict, credenciales):
    """Vuelve a poner desde la sesión los datos del PID: el navegador no los puede cambiar."""
    for campo, valor in _campos_persona(credenciales).items():
        if campo in form_data:
            form_data[campo] = valor
    return form_data


# --- rutas ----------------------------------------------------------------------------------------------
def _destino_valido(url):
    """Solo se vuelve a páginas de este emisor (evita redirecciones abiertas)."""
    if not url:
        return False
    base = urlparse(CONFIGURATION["service_url"])
    u = urlparse(url)
    return u.scheme == base.scheme and u.netloc == base.netloc and u.path.startswith(base.path.rsplit("/", 1)[0] or "/")


def _volver():
    v = session.get("login_volver")
    return v if _destino_valido(v) else url_for("oidc.credential_offer", _external=True)


def _qr(enlace):
    out = io.BytesIO()
    segno.make(enlace).save(out, kind="png", scale=4, border=2)
    return "data:image/png;base64," + base64.b64encode(out.getvalue()).decode()


@login.route("/login", methods=["GET"])
def iniciar():
    volver = request.args.get("volver")
    if _destino_valido(volver):
        session["login_volver"] = volver
    if persona_actual() and not request.args.get("otra"):
        return redirect(_volver())
    frontend = CONFIGURATION["frontend"]["frontends_config"][CONFIGURATION["frontend"]["default"]]["url"]
    try:
        otro, enlace_otro = _crear_solicitud()
        mismo, enlace_mismo = _crear_solicitud(redireccion=url_for("login.retorno", _external=True))
    except Exception as e:  # noqa: BLE001
        logger.error("login: %s", e)
        return post_redirect_with_payload(f"{frontend}/internal_error", {
            "error": "El servicio de verificación no está disponible. Inténtalo en unos minutos."})
    session["login"] = {"otro": otro, "mismo": mismo}
    return post_redirect_with_payload(f"{frontend}/display_login", {
        "qr": _qr(enlace_otro), "enlace": enlace_mismo,
        "estado_url": url_for("login.estado", _external=True),
    })


def _aceptar(r):
    session["persona"] = r["persona"]
    session.pop("login", None)
    logger.info("login: %s %s %s (PID de %s)", r["persona"]["documento"], r["persona"]["nombres"],
                r["persona"]["apellidos"], r["persona"]["emisor_pid"])


@login.route("/login/estado", methods=["GET"])
def estado():
    """La página consulta esto mientras muestra el QR (otro dispositivo)."""
    from flask import jsonify
    if persona_actual():
        return jsonify({"estado": "listo", "siguiente": _volver()})
    pendiente = session.get("login")
    if not pendiente:
        return jsonify({"estado": "error", "motivo": "la solicitud ya no existe: vuelve a empezar"}), 404
    try:
        r = _resultado(pendiente["otro"])
    except Exception as e:  # noqa: BLE001
        logger.error("login estado: %s", e)
        return jsonify({"estado": "esperando"})
    if r["estado"] == "listo":
        _aceptar(r)
        return jsonify({"estado": "listo", "siguiente": _volver()})
    return jsonify({k: v for k, v in r.items() if k != "persona"})


@login.route("/login/retorno", methods=["GET"])
def retorno():
    """A donde vuelve la wallet en el mismo teléfono, con ?response_code=…"""
    frontend = CONFIGURATION["frontend"]["frontends_config"][CONFIGURATION["frontend"]["default"]]["url"]
    pendiente = session.get("login")
    code = request.args.get("response_code")
    if not pendiente or not code:
        motivo = "No encontramos la solicitud. Inténtalo de nuevo desde este navegador."
    else:
        try:
            r = _resultado(pendiente["mismo"], code)
        except Exception as e:  # noqa: BLE001
            logger.error("login retorno: %s", e)
            r = {"estado": "error", "motivo": "el servicio de verificación no está disponible"}
        if r["estado"] == "listo":
            _aceptar(r)
            return redirect(_volver())
        motivo = r.get("motivo") or "La wallet todavía no ha respondido."
    return post_redirect_with_payload(f"{frontend}/internal_error", {"error": f"No pudimos identificarte: {motivo}"})


@login.route("/logout", methods=["GET"])
def salir():
    session.pop("persona", None)
    session.pop("login", None)
    frontend = CONFIGURATION["frontend"]["frontends_config"][CONFIGURATION["frontend"]["default"]]["url"]
    return redirect(frontend + "/")
