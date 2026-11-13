"""Registra la cancillería en el tl-publisher como emisor de la lista PUBEAA. Lo lanza
levantar.sh dentro de python:3.13-alpine (solo biblioteca estándar).

    POST {TL_PUBLISHER_URL}/api/v1/actores
      {rol: pub-eaa, nombre, certificados_pem: raiz.crt, puntos: [URL OpenID4VCI]}

El punto de suministro es la URL del emisor: con ella la wallet la descubre y el
tl-publisher sabe qué documentos emite (para las clases de los verifiers).
Escribe SECRETOS_DIR/actor con su id. Si ya existe, no hace nada.
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

if (DIR / "actor").exists():
    print(f"· ya registrada en el tl-publisher (actor #{(DIR / 'actor').read_text().strip()})")
    sys.exit(0)

cuerpo = {
    # La Cancillería es un organismo público: PUBEAA.
    "rol": "pub-eaa",
    "nombre": os.environ["CANCILLERIA_NOMBRE"],
    "certificados_pem": (DIR / "raiz.crt").read_text(),
    "pais": os.environ.get("PAIS") or "CO",
    "url_info": os.environ["URL_PUBLICA"] + "/",
    "puntos": [os.environ["URL_PUBLICA"]],
}
peticion = urllib.request.Request(
    API + "/actores", method="POST", data=json.dumps(cuerpo).encode(),
    headers={"X-Api-Key": os.environ["CLAVE_OPERADOR"], "Content-Type": "application/json"})
try:
    with urllib.request.urlopen(peticion, timeout=60, context=ssl.create_default_context()) as r:
        actor = json.load(r)
except urllib.error.HTTPError as e:
    sys.exit(f"El tl-publisher respondió {e.code}: {e.read().decode(errors='replace')}")
except urllib.error.URLError as e:
    sys.exit(f"No se pudo hablar con el tl-publisher ({API}): {e.reason}")

(DIR / "actor").write_text(str(actor["id"]))
print(f"· registrada en el tl-publisher como actor #{actor['id']} (lista PUBEAA)")
