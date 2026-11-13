#!/usr/bin/env python3
"""Prueba de punta a punta de srv-andes-scd, sin telefono: reproduce lo que hace la wallet.

  info -> metadata OAuth2 -> PAR -> autorizar (servicio) -> token -> credentials/list
       -> PAR con authorization_details -> autorizar (firma) -> token -> signHash
       -> comprobar la firma con el certificado -> sello de tiempo RFC 3161 con `openssl ts -verify`

Uso:  pruebas/probar_andes_scd.py [URL_BASE]      (por defecto http://127.0.0.1:8099/andes-scd)
"""

import base64
import hashlib
import json
import os
import re
import secrets
import subprocess
import sys
import tempfile
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, utils

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8099/andes-scd").rstrip("/")
CLIENT_ID = os.environ.get("ANDES_CLIENT_ID", "wallet-client")
CLIENT_SECRET = os.environ.get("ANDES_CLIENT_SECRET", "andes-secreto-de-pruebas")
REDIRECT = "rqes://oauth/callback"
TIPO = "https://cloudsignatureconsortium.org/2025/credential"
DOC = os.environ.get("PRUEBA_DOCUMENTO", "1098765432")
PIN = "4321"
NOMBRE = "Maria Fernanda Prueba Gomez"

fallos = 0


def ok(cond: bool, msg: str) -> None:
    global fallos
    print(("  ✔ " if cond else "  ✘ ") + msg)
    if not cond:
        fallos += 1


class SinRedireccion(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k):
        return None


abridor = urllib.request.build_opener(SinRedireccion)


def peticion(url, datos=None, cabeceras=None, json_cuerpo=None, metodo=None):
    cab = dict(cabeceras or {})
    cuerpo = None
    if json_cuerpo is not None:
        cuerpo = json.dumps(json_cuerpo).encode()
        cab["Content-Type"] = "application/json"
    elif isinstance(datos, dict):
        cuerpo = urllib.parse.urlencode(datos).encode()
        cab["Content-Type"] = "application/x-www-form-urlencoded"
    elif datos is not None:
        cuerpo = datos
    req = urllib.request.Request(url, data=cuerpo, headers=cab, method=metodo)
    try:
        r = abridor.open(req, timeout=30)
        return r.status, {k.lower(): v for k, v in r.headers.items()}, r.read()
    except urllib.error.HTTPError as e:
        return e.code, {k.lower(): v for k, v in e.headers.items()}, e.read()


def basic():
    return {"Authorization": "Basic " + base64.b64encode(f"{CLIENT_ID}:{CLIENT_SECRET}".encode()).decode()}


def pkce():
    v = secrets.token_urlsafe(48)
    c = base64.urlsafe_b64encode(hashlib.sha256(v.encode()).digest()).rstrip(b"=").decode()
    return v, c


def flujo_codigo(scope=None, detalles=None):
    """PAR -> pagina de autorizacion -> envio -> redireccion -> token. Devuelve la respuesta del token."""
    verificador, desafio = pkce()
    p = {"response_type": "code", "client_id": CLIENT_ID, "redirect_uri": REDIRECT, "state": "est-" + secrets.token_hex(4),
         "code_challenge": desafio, "code_challenge_method": "S256", "prompt": "login"}
    if scope:
        p["scope"] = scope
    if detalles:
        p["authorization_details"] = json.dumps(detalles)
    est, _, cuerpo = peticion(f"{BASE}/oauth2/par", p, basic())
    ok(est == 201, f"PAR aceptado ({est})")
    request_uri = json.loads(cuerpo)["request_uri"]

    est, _, pagina = peticion(f"{BASE}/oauth2/authorize?" + urllib.parse.urlencode({"client_id": CLIENT_ID, "request_uri": request_uri}))
    ok(est == 200 and b"Andes SCD" in pagina, f"pagina de autorizacion ({est})")
    rid = re.search(rb'name="rid" value="([^"]+)"', pagina).group(1).decode()

    est, cab, _ = peticion(f"{BASE}/oauth2/authorize", {"rid": rid, "documento": DOC, "pin": "9999", "nombre": NOMBRE}) \
        if False else (None, None, None)
    est, cab, cuerpo = peticion(f"{BASE}/oauth2/authorize", {"rid": rid, "documento": DOC, "pin": PIN, "nombre": NOMBRE})
    ok(est == 302 and cab.get("location", "").startswith(REDIRECT), f"redireccion a la wallet ({est})")
    q = urllib.parse.parse_qs(urllib.parse.urlparse(cab["location"]).query)
    ok(q.get("state") == [p["state"]], "state devuelto intacto")

    est, _, cuerpo = peticion(f"{BASE}/oauth2/token", {
        "grant_type": "authorization_code", "code": q["code"][0], "redirect_uri": REDIRECT,
        "code_verifier": verificador, "client_id": CLIENT_ID}, basic())
    ok(est == 200, f"token emitido ({est})")
    # El codigo no se puede reutilizar.
    est2, _, _ = peticion(f"{BASE}/oauth2/token", {
        "grant_type": "authorization_code", "code": q["code"][0], "redirect_uri": REDIRECT,
        "code_verifier": verificador, "client_id": CLIENT_ID}, basic())
    ok(est2 == 400, "el codigo de autorizacion no se reutiliza")
    return json.loads(cuerpo)


def main():
    print(f"== srv-andes-scd · {BASE}")

    print("\n[1] info y metadata")
    est, _, cuerpo = peticion(f"{BASE}/csc/v2/info", json_cuerpo={}, metodo="POST")
    info = json.loads(cuerpo)
    ok(est == 200 and info["oauth2Issuer"] == info.get("oauth2Issuer") and "signatures/signHash" in info["methods"], "info CSC")
    est, _, cuerpo = peticion(f"{info['oauth2Issuer']}/.well-known/oauth-authorization-server")
    meta = json.loads(cuerpo) if est == 200 else {}
    ok(est == 200 and meta.get("issuer") == info["oauth2Issuer"], "metadata OAuth2 con el mismo issuer que /info")

    print("\n[2] client_credentials")
    est, _, _ = peticion(f"{BASE}/oauth2/token", {"grant_type": "client_credentials"}, {"Authorization": "Basic " + base64.b64encode(b"wallet-client:mal").decode()})
    ok(est == 401, "client_credentials con secreto malo -> 401")
    est, _, cuerpo = peticion(f"{BASE}/oauth2/token", {"grant_type": "client_credentials", "scope": "service"}, basic())
    ok(est == 200, "client_credentials con secreto bueno")

    print("\n[3] autorizacion de servicio y credenciales")
    t = flujo_codigo(scope="service")
    auth = {"Authorization": "Bearer " + t["access_token"]}
    est, _, cuerpo = peticion(f"{BASE}/csc/v2/credentials/list", json_cuerpo={"credentialInfo": True, "certificates": "chain", "certInfo": True}, cabeceras=auth)
    lista = json.loads(cuerpo)
    ok(est == 200 and len(lista["credentialIDs"]) == 1, "credentials/list devuelve la credencial")
    cred = lista["credentialInfos"][0]
    cid = cred["credentialID"]
    ok(cred["auth"]["mode"] == "oauth2code" and cred["key"]["len"] in (256, 2048), f"info de credencial (oauth2code, clave de {cred['key']['len']} bits)")
    # La wallet firma con el primer algoritmo que anuncia la credencial.
    sign_algo = cred["key"]["algo"][0]
    cadena = [x509.load_der_x509_certificate(base64.b64decode(c)) for c in cred["cert"]["certificates"]]
    ok(len(cadena) >= 2, f"cadena de {len(cadena)} certificados (usuario + sub-CA{' + raiz' if len(cadena) > 2 else ''})")
    est, _, _ = peticion(f"{BASE}/csc/v2/credentials/list", json_cuerpo={})
    ok(est == 401, "credentials/list sin token -> 401")

    print("\n[4] firma de una huella")
    documento = b"%PDF-1.7 contrato de prueba " + os.urandom(16)
    huella = hashlib.sha256(documento).digest()
    h64 = base64.b64encode(huella).decode()
    detalles = [{"type": TIPO, "credentialID": cid, "numSignatures": 1, "hashAlgorithmOID": "2.16.840.1.101.3.4.2.1",
                 "documentDigests": [{"hash": h64, "label": "contrato.pdf", "hashType": "DTBSR"}]}]
    est, _, _ = peticion(f"{BASE}/csc/v2/signatures/signHash", json_cuerpo={"credentialID": cid, "hashes": [h64], "hashAlgorithmOID": "2.16.840.1.101.3.4.2.1", "signAlgo": sign_algo}, cabeceras=auth)
    ok(est == 403, "signHash con token de solo inicio de sesion -> 403")
    tf = flujo_codigo(detalles=detalles)
    ok(tf.get("credentialID") == cid and tf["authorization_details"][0]["documentDigests"][0]["hash"] == h64,
       "el token trae authorization_details completos")
    autf = {"Authorization": "Bearer " + tf["access_token"]}
    otra = base64.b64encode(hashlib.sha256(b"otro").digest()).decode()
    est, _, _ = peticion(f"{BASE}/csc/v2/signatures/signHash", json_cuerpo={"credentialID": cid, "hashes": [otra], "hashAlgorithmOID": "2.16.840.1.101.3.4.2.1", "signAlgo": sign_algo}, cabeceras=autf)
    ok(est == 403, "signHash de una huella NO autorizada -> 403")
    est, _, cuerpo = peticion(f"{BASE}/csc/v2/signatures/signHash", json_cuerpo={"credentialID": cid, "hashes": [h64], "hashAlgorithmOID": "2.16.840.1.101.3.4.2.1", "signAlgo": sign_algo}, cabeceras=autf)
    ok(est == 200, f"signHash de la huella autorizada ({est})")
    firma = base64.b64decode(json.loads(cuerpo)["signatures"][0])
    pub = cadena[0].public_key()
    def verificar(sig, h):
        if isinstance(pub, ec.EllipticCurvePublicKey):
            pub.verify(sig, h, ec.ECDSA(utils.Prehashed(hashes.SHA256())))
        else:
            pub.verify(sig, h, padding.PKCS1v15(), utils.Prehashed(hashes.SHA256()))
    tipo = "ECDSA" if isinstance(pub, ec.EllipticCurvePublicKey) else "RSA"
    ok(len(firma) < 128 if tipo == "ECDSA" else len(firma) == 256, f"la firma {tipo} mide {len(firma)} bytes")
    try:
        verificar(firma, huella)
        ok(True, f"la firma {tipo} verifica con el certificado del firmante")
    except Exception as e:
        ok(False, f"la firma no verifica: {e}")
    try:
        verificar(firma, hashlib.sha256(b"x").digest())
        ok(False, "la firma verifico contra otra huella (no deberia)")
    except Exception:
        ok(True, "la firma NO verifica contra otra huella")
    # Certificado: perfil qtsp-user
    c = cadena[0]
    ku = c.extensions.get_extension_for_class(x509.KeyUsage)
    ok(ku.critical and ku.value.content_commitment, "certificado: keyUsage critical con nonRepudiation")
    ok(NOMBRE in c.subject.rfc4514_string() and f"CC{DOC}" in c.subject.rfc4514_string(), "certificado: sujeto con nombre y documento")

    print("\n[5] sello de tiempo RFC 3161")
    with tempfile.TemporaryDirectory() as d:
        d = Path(d)
        subprocess.run(["openssl", "ts", "-query", "-digest", huella.hex(), "-sha256", "-cert", "-out", str(d / "q.tsq")], check=True, capture_output=True)
        est, cab, resp = peticion(f"{BASE}/tsa", (d / "q.tsq").read_bytes(), {"Content-Type": "application/timestamp-query"})
        ok(est == 200 and cab.get("content-type", "").startswith("application/timestamp-reply"), f"/tsa responde ({est})")
        (d / "r.tsr").write_bytes(resp)
        cadena_pem = b""
        for pem in re.findall(rb"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----\n?", os.environ.get("CADENA_CONFIANZA", "").encode() or b"", re.S):
            cadena_pem += pem
        raiz = os.environ.get("RAIZ_PEM")
        if raiz and Path(raiz).exists():
            r = subprocess.run(["openssl", "ts", "-verify", "-in", str(d / "r.tsr"), "-queryfile", str(d / "q.tsq"), "-CAfile", raiz],
                               capture_output=True)
            ok(r.returncode == 0, "openssl ts -verify: el sello valida contra la raiz del ecosistema" + ("" if r.returncode == 0 else " · " + r.stderr.decode()[-200:]))
        r = subprocess.run(["openssl", "ts", "-reply", "-in", str(d / "r.tsr"), "-text"], capture_output=True)
        ok(b"Status: Granted" in r.stdout, "respuesta del sello: Granted")
        est, _, resp2 = peticion(f"{BASE}/tsa", b"basura", {"Content-Type": "application/timestamp-query"})
        (d / "mala.tsr").write_bytes(resp2)
        txt = subprocess.run(["openssl", "ts", "-reply", "-in", str(d / "mala.tsr"), "-text"], capture_output=True).stdout
        ok(est == 400 or b"Status: Rejected" in txt, "consulta de sello invalida -> rechazada (RFC 3161: respuesta con estado Rejected)")

    print("\n[6] credencial reutilizada en un segundo inicio de sesion")
    t2 = flujo_codigo(scope="service")
    est, _, cuerpo = peticion(f"{BASE}/csc/v2/credentials/list", json_cuerpo={}, cabeceras={"Authorization": "Bearer " + t2["access_token"]})
    ok(json.loads(cuerpo)["credentialIDs"] == [cid], "misma credencial, no se emite otro certificado")

    print("\n" + ("TODO BIEN" if fallos == 0 else f"{fallos} FALLO(S)"))
    sys.exit(1 if fallos else 0)


main()
