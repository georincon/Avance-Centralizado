#!/usr/bin/env python3
"""Prueba del Trust Validator contra la LOTL local.

Manda la cadena de un certificado real del ecosistema al validador y dice si
la da por confiable. Es el smoke test de la fase de confianza.

    ./probar-validacion.py [contexto] [ruta/almacen] [password]
"""

import base64
import json
import re
import ssl
import subprocess
import sys
import urllib.error
import urllib.request

DOMINIO = "https://civica-desarrollo.avance.org.co"


def pem_a_b64(pem: str) -> str:
    return "".join(l for l in pem.splitlines() if not l.startswith("-----"))


def certs_de_keystore(keystore: str, password: str, alias: str) -> list[str]:
    out = subprocess.run(
        ["keytool", "-exportcert", "-rfc", "-alias", alias,
         "-keystore", keystore, "-storepass", password],
        capture_output=True, text=True, check=True,
    ).stdout
    return [pem_a_b64(c) for c in re.findall(
        r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", out, re.S)]


def pedir(chain: list[str], contexto: str) -> tuple[int, str]:
    cuerpo = json.dumps({"chain": chain, "verificationContext": contexto}).encode()
    peticion = urllib.request.Request(
        f"{DOMINIO}/trust", data=cuerpo,
        headers={"Content-Type": "application/json", "Accept": "application/json"},
    )
    try:
        r = urllib.request.urlopen(peticion, timeout=120)
        return r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()


def main() -> int:
    contexto = sys.argv[1] if len(sys.argv) > 1 else "WalletProviderAttestation"
    keystore = sys.argv[2] if len(sys.argv) > 2 else (
        "/home/geovani/Workspace/Avance/eudi-srv-wallet-provider/docker/spartans/keystore.jks")
    password = sys.argv[3] if len(sys.argv) > 3 else "spartans-keys"
    alias = sys.argv[4] if len(sys.argv) > 4 else "spartans-wallet-provider"

    cadena = certs_de_keystore(keystore, password, alias)
    # La raiz va aparte: el almacen solo exporta la hoja.
    raiz = "/home/geovani/Workspace/Avance/eudi-srv-wallet-provider/docker/secretos/pki/raiz.crt"
    try:
        cadena.append(pem_a_b64(open(raiz).read()))
    except OSError:
        pass

    print(f"contexto      {contexto}")
    print(f"certificados  {len(cadena)}")
    codigo, cuerpo = pedir(cadena, contexto)
    print(f"HTTP          {codigo}")
    try:
        print(json.dumps(json.loads(cuerpo), indent=1)[:1500])
    except json.JSONDecodeError:
        print(cuerpo[:1500])
    return 0 if codigo == 200 else 1


if __name__ == "__main__":
    raise SystemExit(main())
