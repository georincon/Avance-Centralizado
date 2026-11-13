"""Cliente del verifier (eudi-srv-verifier-endpoint) de este servicio, para identificar al firmante con su PID.

El servicio no habla OpenID4VP con la wallet: eso lo hace su verifier (`verifiers/_comun`, instancia `firma`). Aqui solo se
le pide una transaccion con lo que se quiere saber (DCQL) y se lee el resultado. Cuando el verifier entrega los datos ya
valido la firma del emisor, que el emisor esta en la lista de confianza de PID, el estado de la credencial y que la presenta
su titular (KB-JWT con el nonce de esta solicitud). Es el mismo patron de los portales de verifiers/ (verifier.js).

Nada de lo que llega se guarda salvo documento y nombre, para emitir el certificado.
"""

from __future__ import annotations

import base64
import json
import secrets
import urllib.error
import urllib.request

from . import config

FALLOS = {
    "FailedToRetrieve request": "la wallet no pudo leer la solicitud",
    "Wallet failed to post response": "la credencial no pasó la verificación",
    "Presentation expired": "la solicitud venció",
    "Attestation status check failed": "la credencial fue revocada o suspendida",
}

_TECNICOS = {"iss", "iat", "nbf", "exp", "cnf", "vct", "vct#integrity", "status", "_sd", "_sd_alg", "jti", "sub", "aud"}

_uso_id: str | None = None


class VerificadorError(Exception):
    pass


def _llamar(ruta: str, cuerpo: dict | None = None) -> tuple[int, dict]:
    datos = json.dumps(cuerpo).encode() if cuerpo is not None else None
    req = urllib.request.Request(
        config.VERIFIER_API + ruta, data=datos, method="POST" if datos is not None else "GET",
        headers={"accept": "application/json", **({"content-type": "application/json"} if datos is not None else {})},
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            texto = r.read().decode()
            return r.status, json.loads(texto) if texto else {}
    except urllib.error.HTTPError as e:
        texto = e.read().decode()
        try:
            return e.code, json.loads(texto) if texto else {}
        except json.JSONDecodeError:
            return e.code, {}
    except (urllib.error.URLError, TimeoutError) as e:
        raise VerificadorError(f"el verifier no responde: {e}") from e


def _intended_use() -> str | None:
    global _uso_id
    if _uso_id is None:
        _, cuerpo = _llamar("/ui/intended-uses")
        usos = cuerpo.get("intended_uses") or []
        _uso_id = usos[0].get("intended_use_id") if usos else None
    return _uso_id


def crear_solicitud(retorno: str | None = None) -> dict:
    """Pide el PID (nombre y documento). Devuelve {transaccion, enlace}.

    `retorno` es la plantilla de la direccion que la wallet abre al terminar (debe llevar `{RESPONSE_CODE}`): es el
    mecanismo del estandar para el mismo dispositivo. Con ella, el verifier exige ese codigo para entregar la respuesta.
    """
    claims = [{"path": [d]} for d in config.PID_DATOS]
    cuerpo_solicitud = {
        "dcql_query": {"credentials": [{
            "id": "pid", "format": "dc+sd-jwt", "meta": {"vct_values": [config.PID_VCT]}, "claims": claims,
        }]},
        "nonce": secrets.token_urlsafe(16),
        "profile": "haip",
        "intended_use_id": _intended_use(),
        "request_uri_method": "get",
    }
    if retorno:
        cuerpo_solicitud["wallet_response_redirect_uri_template"] = retorno
    estado, cuerpo = _llamar("/ui/presentations/v2", cuerpo_solicitud)
    if estado != 200:
        raise VerificadorError(f"el verifier no creó la solicitud (HTTP {estado})")
    return {"transaccion": cuerpo["transaction_id"], "enlace": cuerpo["authorization_request_uri"]}


def resultado(transaccion: str, response_code: str | None = None) -> dict:
    """{estado: 'esperando'} | {estado: 'listo', emisor, vct, datos} | {estado: 'error', motivo}.

    `response_code` es el que el verifier puso en la direccion de retorno (mismo dispositivo).
    """
    from urllib.parse import quote
    t = quote(transaccion, safe="")
    sufijo = f"?response_code={quote(response_code, safe='')}" if response_code else ""
    estado, cuerpo = _llamar(f"/ui/presentations/{t}{sufijo}")
    if estado == 404:
        return {"estado": "error", "motivo": "la solicitud ya no existe"}
    if estado == 400:
        _, ev = _llamar(f"/ui/presentations/{t}/events")
        fallo = next((e for e in ev.get("events", []) if e.get("event") in FALLOS), None)
        return {"estado": "error", "motivo": FALLOS[fallo["event"]]} if fallo else {"estado": "esperando"}
    if cuerpo.get("error"):
        motivo = "la persona no aceptó compartir los datos" if cuerpo["error"] == "access_denied" else "la wallet devolvió un error"
        return {"estado": "error", "motivo": motivo}
    presentado = cuerpo.get("vp_token", {}).get("pid")
    if isinstance(presentado, list):
        presentado = presentado[0] if presentado else None
    if not isinstance(presentado, str):
        return {"estado": "esperando"} if estado == 200 and not cuerpo else {"estado": "error", "motivo": "la respuesta no trae la credencial"}
    return {"estado": "listo", **leer_sd_jwt(presentado)}


def _b64(texto: str) -> bytes:
    return base64.urlsafe_b64decode(texto + "=" * (-len(texto) % 4))


def leer_sd_jwt(presentado: str) -> dict:
    """Lee un SD-JWT ya validado por el verifier: tipo (vct), emisor y datos divulgados de primer nivel."""
    jwt, *partes = presentado.split("~")
    payload = json.loads(_b64(jwt.split(".")[1]))
    datos = {k: v for k, v in payload.items() if k not in _TECNICOS}
    for d in partes[:-1]:  # el ultimo es el KB-JWT
        if not d:
            continue
        v = json.loads(_b64(d))
        if isinstance(v, list) and len(v) == 3 and v[1] not in datos:
            datos[v[1]] = v[2]
    return {"emisor": payload.get("iss"), "vct": payload.get("vct"), "datos": datos}


def identidad_de(res: dict) -> tuple[str, str]:
    """(documento, nombre completo) de un resultado 'listo'; lanza VerificadorError si no es un PID usable."""
    if res.get("vct") != config.PID_VCT:
        raise VerificadorError("La credencial presentada no es un PID.")
    d = res["datos"]
    for dato in config.PID_DATOS:
        if not d.get(dato):
            raise VerificadorError(f"La wallet no compartió un dato necesario ({dato}).")
    documento = str(d["personal_administrative_number"]).strip()
    nombre = f"{d['given_name']} {d['family_name']}".strip()
    return documento, nombre
