"""PIN de firma, tokens y autenticacion de clientes OAuth2."""

from __future__ import annotations

import base64
import hashlib
import hmac
import secrets

from fastapi import Request

import json
import time

from . import config


def nuevo_secreto(n: int = 32) -> str:
    return secrets.token_urlsafe(n)


def hash_pin(pin: str, sal: bytes) -> bytes:
    return hashlib.scrypt(pin.encode(), salt=sal, n=2**14, r=8, p=1, dklen=32)


def pin_valido(pin: str) -> bool:
    return pin.isdigit() and 4 <= len(pin) <= 8


def sal_nueva() -> bytes:
    return secrets.token_bytes(16)


def comparar(a: str, b: str) -> bool:
    return hmac.compare_digest(a.encode(), b.encode())


def pkce_s256(verificador: str) -> str:
    digest = hashlib.sha256(verificador.encode()).digest()
    return base64.urlsafe_b64encode(digest).rstrip(b"=").decode()


def b64_estandar(valor: str) -> str:
    """Normaliza base64 / base64url con o sin relleno a base64 estandar sin relleno, para comparar huellas."""
    valor = valor.strip().replace("-", "+").replace("_", "/").rstrip("=")
    return valor


def decodificar_b64(valor: str) -> bytes:
    """Decodifica base64 o base64url, con o sin relleno."""
    limpio = b64_estandar(valor)
    return base64.b64decode(limpio + "=" * (-len(limpio) % 4))


def cliente_de(request: Request, formulario) -> tuple[str | None, str | None]:
    """(client_id, client_secret) de la peticion: HTTP Basic o campos del formulario."""
    auth = request.headers.get("authorization", "")
    if auth.lower().startswith("basic "):
        try:
            crudo = base64.b64decode(auth[6:]).decode()
            cid, _, secreto = crudo.partition(":")
            return cid, secreto
        except Exception:
            return None, None
    return formulario.get("client_id"), formulario.get("client_secret")


def cliente_autenticado(request: Request, formulario, exigir_secreto: bool) -> bool:
    cid, secreto = cliente_de(request, formulario)
    if not cid or not comparar(cid, config.CLIENT_ID):
        return False
    if secreto:
        return comparar(secreto, config.CLIENT_SECRET)
    return not exigir_secreto


# --- Sesion de identificacion (cookie) -----------------------------------------------------------------
# Tras presentar el PID, el servicio recuerda quien es el firmante unos minutos en ESE navegador: la autorizacion de
# servicio y la de cada firma no piden el PID otra vez (solo la confirmacion de firmar). Va firmada con HMAC.

COOKIE_SESION = "andes_sesion"
TTL_SESION = 600


def _llave_sesion() -> bytes:
    from . import ca
    ca._fernet()  # crea la llave maestra si todavia no existe
    return (config.DIR_DATOS / "llave-maestra.key").read_bytes()


def _b64u(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def crear_sesion(documento: str, nombre: str) -> str:
    cuerpo = _b64u(json.dumps({"d": documento, "n": nombre, "e": int(time.time()) + TTL_SESION}).encode())
    firma = _b64u(hmac.new(_llave_sesion(), cuerpo.encode(), hashlib.sha256).digest())
    return f"{cuerpo}.{firma}"


def leer_sesion(valor: str | None) -> dict | None:
    """{documento, nombre, vence} si la cookie es autentica y no vencio; si no, None."""
    if not valor or "." not in valor:
        return None
    cuerpo, _, firma = valor.partition(".")
    esperada = _b64u(hmac.new(_llave_sesion(), cuerpo.encode(), hashlib.sha256).digest())
    if not hmac.compare_digest(firma, esperada):
        return None
    try:
        d = json.loads(base64.urlsafe_b64decode(cuerpo + "=" * (-len(cuerpo) % 4)))
    except Exception:
        return None
    if d.get("e", 0) < time.time():
        return None
    return {"documento": d["d"], "nombre": d["n"], "vence": d["e"]}
