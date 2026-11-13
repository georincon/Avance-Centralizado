#!/usr/bin/env python3
"""Comprueba que las cadenas REALES del ecosistema encajan con las anclas publicadas.

POR QUE EXISTE

Un certificado puede tener el mismo nombre que el que se publica y ser otro
distinto: es lo que paso con la raiz del Wallet Provider, que en la lista figuraba
con el CN correcto pero con el certificado del despliegue anterior. La wallet
presentaba su WUA, la cadena encadenaba con la raiz VIVA, y la lista de
confianza publicaba OTRA raiz con el mismo nombre: el resultado es "no
confiable" sin que nada falle visiblemente.

Este script comprueba, extremo a extremo, que la cadena que cada emisor
presenta llega hasta el ancla que la lista de confianza publica. Es la prueba
que hace falta cuando se cambia una PKI o se republican las listas.

    ./verificar-cadenas.py            comprueba contra el dominio de desarrollo
    ./verificar-cadenas.py --verbose  muestra tambien las anclas que NO encajan
"""

import base64
import hashlib
import json
import ssl
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

DOMINIO = "https://civica-desarrollo.avance.org.co"
RAIZ = Path(__file__).resolve().parent.parent.parent          # Avance/
VERBOSE = "--verbose" in sys.argv

# Esquema de la lista donde se publica cada cadena.
TL_WP, TL_PID, TL_PUBEAA, TL_RPREG = "WP", "PID", "PUBEAA", "RPREG"


# ── Utilidades ───────────────────────────────────────────────────────────────

def contexto():
    ctx = ssl.create_default_context()
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    return ctx


def anclas_publicadas(esquema):
    """Las anclas (certificados) de una lista publicada, ya en DER."""
    url = f"{DOMINIO}/tl-publisher/TL/{esquema}/current.json"
    crudo = urllib.request.urlopen(url, context=contexto(), timeout=20).read().decode().strip()
    partes = crudo.split(".")
    if len(partes) != 3:
        raise SystemExit(f"{url} no devuelve un JWS compacto")
    def b64(s):
        return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))
    carga = json.loads(b64(partes[1]))
    out = []
    for servicio in carga.get("services", []):
        for cert in servicio.get("certificates", []):
            out.append((cert["subject"], base64.b64decode(cert["der_b64"])))
    return out, carga.get("sequence_number")


def der_de_fichero(ruta):
    return subprocess.run(
        ["openssl", "x509", "-in", str(ruta), "-outform", "DER"],
        capture_output=True, check=True).stdout


def pem_de_fichero(ruta):
    return Path(ruta).read_text()


def exportar_alias(keystore, alias, storepass):
    with tempfile.NamedTemporaryFile("w", suffix=".pem", delete=False) as f:
        subprocess.run(
            ["keytool", "-exportcert", "-rfc", "-alias", alias,
             "-keystore", str(keystore), "-storepass", storepass, "-file", f.name],
            capture_output=True, check=True)
        return Path(f.name).read_text()


def verificar_cadena(hoja_pem, raiz_pem, intermedias_pem=None):
    """openssl verify contra la raiz, opcionalmente con las intermedias."""
    with tempfile.TemporaryDirectory() as d:
        hoja = Path(d) / "leaf.pem"; hoja.write_text(hoja_pem)
        raiz = Path(d) / "root.pem"; raiz.write_text(raiz_pem)
        cmd = ["openssl", "verify", "-CAfile", str(raiz), "-no_check_time"]
        if intermedias_pem:
            inter = Path(d) / "chain.pem"; inter.write_text(intermedias_pem)
            cmd += ["-untrusted", str(inter)]
        cmd.append(str(hoja))
        r = subprocess.run(cmd, capture_output=True, text=True)
        return r.returncode == 0, (r.stdout + r.stderr).strip()


# ── Los casos: emisor -> lista donde se publica su ancla ─────────────────────

CASOS = [
    {
        "nombre": "Wallet Provider (Spartans)",
        "esquema": TL_WP,
        "raiz": RAIZ / "eudi-srv-wallet-provider/docker/secretos/pki/raiz.crt",
        "hoja": lambda: exportar_alias(
            RAIZ / "eudi-srv-wallet-provider/docker/spartans/keystore.jks",
            "spartans-wallet-provider", "spartans-keys"),
        "intermedias": None,
    },
    {
        "nombre": "Issuer PID de la Registraduria",
        "esquema": TL_PID,
        "raiz": RAIZ / "eudi-srv-issuer-pid-registraduria/docker-compose/pki-dev/iss/root.crt",
        "hoja": lambda: pem_de_fichero(
            RAIZ / "eudi-srv-issuer-pid-registraduria/docker-compose/pki-dev/iss/issuance.crt"),
        "intermedias": lambda: pem_de_fichero(
            RAIZ / "eudi-srv-issuer-pid-registraduria/docker-compose/pki-dev/iss/issuance-chain.crt"),
    },
    {
        "nombre": "Verifier (certificado de acceso)",
        "esquema": TL_RPREG,
        "raiz": RAIZ / "eudi-srv-verifier-local-build/docker-compose/secretos/pki/access-root.crt",
        "hoja": lambda: pem_de_fichero(
            RAIZ / "eudi-srv-verifier-local-build/docker-compose/secretos/pki/access-leaf.crt"),
        "intermedias": None,
    },
]


def main() -> int:
    fallos = 0
    for caso in CASOS:
        print(f"\n=== {caso['nombre']}  (ancla publicada en TL/{caso['esquema']}) ===")
        if not caso["raiz"].is_file():
            print(f"  SALTADO: no existe {caso['raiz']}")
            continue
        anclas, secuencia = anclas_publicadas(caso["esquema"])
        print(f"  lista TL/{caso['esquema']}: secuencia {secuencia}, {len(anclas)} anclas")

        raiz_der = der_de_fichero(caso["raiz"])
        publicada = [subj for subj, der in anclas if der == raiz_der]

        if not publicada:
            print(f"  [FALLO] la raiz viva NO esta publicada en TL/{caso['esquema']}")
            print(f"           raiz viva: {subprocess.run(['openssl','x509','-in',str(caso['raiz']),'-noout','-subject'],capture_output=True,text=True).stdout.strip()}")
            fallos += 1
            continue

        print(f"  ancla publicada que coincide: {publicada[0]}")
        ok, detalle = verificar_cadena(caso["hoja"](), pem_de_fichero(caso["raiz"]),
                                        caso["intermedias"]() if caso["intermedias"] else None)
        if ok:
            print("  [OK] la cadena que presenta el emisor encaja con el ancla publicada")
        else:
            print(f"  [FALLO] la cadena NO encaja: {detalle}")
            fallos += 1

        if VERBOSE:
            for subj, der in anclas:
                if der != raiz_der:
                    print(f"    (otra ancla) {subj}")

    print()
    if fallos:
        print(f"RESULTADO: {fallos} cadena(s) NO encajan con lo publicado")
        return 1
    print("RESULTADO: todas las cadenas encajan con las anclas publicadas")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
