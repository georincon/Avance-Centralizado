#!/usr/bin/env python3
"""Smoke test del validador de confianza contra las cadenas REALES del ecosistema.

Para cada emisor monta la cadena que presenta de verdad y se la pregunta al
validador por el dominio. Es la prueba que dice si la cadena de confianza del
ecosistema esta montada: si el validador dice que NO es de fiar, o dice que si
con otro ancla, algo se ha desincronizado entre la PKI y las listas.

    ./probar-ecosistema.py
    ./probar-ecosistema.py --verbose
"""

import base64
import json
import re
import ssl
import subprocess
import sys
import tempfile
import urllib.error
import urllib.request
from pathlib import Path

DOMINIO = "https://civica-desarrollo.avance.org.co"
AVANCE = Path(__file__).resolve().parent.parent.parent
VERBOSE = "--verbose" in sys.argv


def contexto():
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


def b64pem(texto):
    return "".join(l for l in texto.splitlines() if not l.startswith("-----"))


def de_fichero(ruta):
    return b64pem(Path(ruta).read_text())


def de_alias(keystore, alias, password):
    out = subprocess.run(
        ["keytool", "-exportcert", "-rfc", "-alias", alias,
         "-keystore", str(keystore), "-storepass", password],
        capture_output=True, text=True, check=True).stdout
    return [b64pem(c) for c in re.findall(
        r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", out, re.S)]


def pedir(cadena, contexto_validacion):
    cuerpo = json.dumps({"chain": cadena,
                         "verificationContext": contexto_validacion}).encode()
    peticion = urllib.request.Request(
        f"{DOMINIO}/trust", data=cuerpo,
        headers={"Content-Type": "application/json", "Accept": "application/json"})
    try:
        r = urllib.request.urlopen(peticion, timeout=120)
        return r.status, json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read())


def sujeto_de_ancla(anchor_b64):
    if not anchor_b64:
        return "(ninguna)"
    crudo = "".join(anchor_b64.split())
    try:
        der = base64.b64decode(crudo + "=" * (-len(crudo) % 4))
    except Exception:
        return "(no decodificable)"
    with tempfile.NamedTemporaryFile("wb", suffix=".der", delete=False) as f:
        f.write(der)
        nombre = f.name
    salida = subprocess.run(["openssl", "x509", "-inform", "DER", "-in", nombre,
                              "-noout", "-subject"], capture_output=True, text=True)
    return salida.stdout.strip().replace("subject=", "") or "(ilegible)"


CASOS = [
    {
        "nombre": "Wallet Provider (Spartans) - WUA",
        "contexto": "WalletProviderAttestation",
        "cadena": lambda: de_alias(
            AVANCE / "eudi-srv-wallet-provider/docker/spartans/keystore.jks",
            "spartans-wallet-provider", "spartans-keys") + [
            de_fichero(AVANCE / "eudi-srv-wallet-provider/docker/secretos/pki/raiz.crt")],
    },
    {
        "nombre": "Issuer PID de la Registraduria - emision",
        "contexto": "PID",
        "cadena": lambda: [
            de_fichero(AVANCE / "eudi-srv-issuer-pid-registraduria/docker-compose/pki-dev/iss/issuance.crt"),
            de_fichero(AVANCE / "eudi-srv-issuer-pid-registraduria/docker-compose/pki-dev/iss/int.crt"),
            de_fichero(AVANCE / "eudi-srv-issuer-pid-registraduria/docker-compose/pki-dev/iss/root.crt")],
    },
]


def main() -> int:
    fallos = 0
    for caso in CASOS:
        codigo, r = pedir(caso["cadena"](), caso["contexto"])
        confia = bool(r.get("trusted"))
        ancla = sujeto_de_ancla(r.get("trustAnchor"))
        marca = "OK  " if (codigo == 200 and confia) else "FALLO"
        if not (codigo == 200 and confia):
            fallos += 1
        print(f"  [{marca}] {caso['nombre']}")
        print(f"          contexto {caso['contexto']}  ->  HTTP {codigo}  trusted={confia}")
        print(f"          ancla: {ancla}")
        if not confia and r.get("error"):
            print(f"          error: {r['error']}")
        if VERBOSE:
            print(f"          respuesta: {json.dumps(r)[:400]}")
    print()
    if fallos:
        print(f"RESULTADO: {fallos} cadena(s) NO validadas")
        return 1
    print("RESULTADO: todas las cadenas del ecosistema son de fiar para el validador")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
