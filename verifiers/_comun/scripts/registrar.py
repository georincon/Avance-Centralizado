"""Pide el certificado de acceso del verifier al tl-publisher de Avance. Lo lanza levantar.sh
dentro de python:3.13-alpine (solo biblioteca estándar).

    POST {TL_PUBLISHER_URL}/api/v1/verifiers   {nombre, dominios, csr_pem, pais}
      → certificado de acceso (lo emite pki-servicio, perfil rp-access, con EKU 1.0.18013.5.1.6)

Escribe en SECRETOS_DIR: cadena.pem (certificado + CA de acceso) y actor (su id en el
registro). Si ya había un actor (renovación), lo da de baja después: un verifier rota su
certificado registrándose de nuevo, y el anterior queda revocado.
"""
import json
import os
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path

DIR = Path(os.environ["SECRETOS_DIR"])
API = os.environ["TL_PUBLISHER_URL"].rstrip("/") + "/api/v1"
CLAVE = os.environ["CLAVE_OPERADOR"]
DOMINIO = os.environ["DOMINIO"]


def llamar(metodo, ruta, cuerpo=None):
    peticion = urllib.request.Request(
        API + ruta, method=metodo, data=json.dumps(cuerpo).encode() if cuerpo is not None else None,
        headers={"X-Api-Key": CLAVE, "Content-Type": "application/json", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(peticion, timeout=60, context=ssl.create_default_context()) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        detalle = e.read().decode(errors="replace")
        sys.exit(f"El tl-publisher respondió {e.code} a {metodo} {ruta}: {detalle}")
    except urllib.error.URLError as e:
        sys.exit(f"No se pudo hablar con el tl-publisher ({API}): {e.reason}")


anterior = (DIR / "actor").read_text().strip() if (DIR / "actor").exists() else None

r = llamar("POST", "/verifiers", {
    "nombre": os.environ["VERIFIER_NOMBRE"],
    # El SAN DNS del certificado: es el client_id con x509_san_dns.
    "dominios": [DOMINIO],
    "csr_pem": (DIR / "acceso.csr").read_text(),
    "pais": os.environ.get("VERIFIER_PAIS") or None,
})
(DIR / "cadena.pem").write_text(r["cadena_pem"])
(DIR / "actor").write_text(str(r["actor"]["id"]))
print(f"· registrado en el tl-publisher como actor #{r['actor']['id']}")

if anterior and anterior != str(r["actor"]["id"]):
    try:
        llamar("POST", f"/actores/{anterior}/retirar")
        print(f"· actor anterior #{anterior} dado de baja (su certificado queda revocado)")
    except SystemExit as e:   # ya retirado o borrado: el nuevo certificado sirve igual
        print(f"· aviso: no se pudo dar de baja el actor anterior #{anterior}: {e}")
