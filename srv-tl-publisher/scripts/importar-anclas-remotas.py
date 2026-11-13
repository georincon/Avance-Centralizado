#!/usr/bin/env python3
"""Importa las anclas de confianza de las listas publicadas REMOTAS al PKI local.

POR QUÉ EXISTE

El despliegue público publica sus Trusted Lists en
`https://civica.avance.org.co/tl-publisher`. Para desacoplar el ecosistema de
ese host hay que poder publicar las MISMAS listas desde aquí, no inventar otras:
un publicador local que publicara anclas distintas cambiaría quién valida y
quién no, sin que nadie se entere.

Por defecto este script se lee del publicador LOCAL
(`civica-desarrollo.avance.org.co/tl-publisher`), de modo que reejecutarlo es
idempotente. Consumir el despliegue publico es una decision consciente: hay
que pasar `--url-base` a mano y el script avisa por stderr.

Este script hace el bootstrap, UNA VEZ:

  1. Descarga las listas remotas (LOTL + las cinco del art. 22a).
  2. Extrae de cada una los certificados de ancla (las sub-CAs).
  3. Los deja en `pki/publico/ca/<slug>.crt` (PEM) y escribe el manifiesto
     `pki/publico/anclas.json`, que `sembrar.py` lee para darlos de alta en la
     base de datos del publicador.

A partir de ahí NADA depende del host remoto: las anclas son ficheros locales
y el publicador local firma y sirve sus propias listas.

El `ServiceTypeIdentifier` que aparece en las listas remotas es la forma
canónica (la corregida, con `TrstSvc`). La base de datos del publicador
indexa por la forma DECLARADA (la del upstream, sin `TrstSvc`), y es
`service_type_registry` la que traduce una en otra al publicar. Por eso aquí
se guardan las dos: la clave del registro y la canónica.

    ./importar-anclas-remotas.py [--url-base https://civica-desarrollo.avance.org.co/tl-publisher]
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import sys
import urllib.request
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization

RAIZ = Path(__file__).resolve().parent.parent
PKI_PUBLICO = RAIZ / "pki" / "publico"

NS = "{http://uri.etsi.org/02231/v2#}"

#esquema -> (tipo declarado en el registro, tipo canonico publicado)
ESQUEMAS = {
    "WP": (
        "http://uri.etsi.org/Svc/Svctype/Provider/Wallet",
        "http://uri.etsi.org/TrstSvc/Svctype/Provider/Wallet",
    ),
    "PID": (
        "http://uri.etsi.org/Svc/Svctype/Provider/PID",
        "http://uri.etsi.org/TrstSvc/Svctype/Provider/PID",
    ),
    "QEAA": (
        "http://uri.etsi.org/TrstSvc/Svctype/EAA/Q",
        "http://uri.etsi.org/TrstSvc/Svctype/EAA/Q",
    ),
    "PUBEAA": (
        "http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA",
        "http://uri.etsi.org/TrstSvc/Svctype/EAA/Pub-EAA",
    ),
    "RPREG": (
        "http://uri.etsi.org/Svc/Svctype/CA/RPaccess",
        "http://uri.etsi.org/TrstSvc/Svctype/CA/RPaccess",
    ),
}


def descargar(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=30) as r:
        return r.read()


def crldp(cert: x509.Certificate) -> list[str]:
    """URLs de CRL declaradas por el propio certificado."""
    try:
        ext = cert.extensions.get_extension_for_class(x509.CRLDistributionPoints)
    except x509.ExtensionNotFound:
        return []
    urls = []
    for dp in ext.value:
        if dp.full_name is None:
            continue
        for gn in dp.full_name:
            valor = getattr(gn, "value", None)
            if isinstance(valor, str):
                urls.append(valor)
    return urls


def slug_de(esquema: str, cert: x509.Certificate) -> str:
    """Slug del fichero de la ancla.

    El CN NO basta: en este ecosistema hay dos CAs distintas que se llaman
    igual (`PID Issuer de prueba Root CA`, la del emisor nativo y la de la
    Registraduría). Con el CN solo, una se pisaba a la otra y el despliegue
    perdía un ancla sin avisar. Se añade el prefijo de la huella.
    """
    cn = cert.subject.get_attributes_for_oid(x509.NameOID.COMMON_NAME)
    base = cn[0].value if cn else cert.subject.rfc4514_string()
    base = re.sub(r"[^A-Za-z0-9]+", "-", base).strip("-").lower()
    huella = cert.fingerprint(hashes.SHA256()).hex()[:8]
    return f"{esquema.lower()}-{base}-{huella}"


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument(
        "--url-base",
        default="https://civica-desarrollo.avance.org.co/tl-publisher",
        help=(
            "base de las listas remotas. Por defecto el publicador LOCAL de "
            "desarrollo. Importar desde `civica.avance.org.co` (produccion) es "
            "una decision consciente: hay que pasar la URL a mano."
        ),
    )
    args = ap.parse_args()

    if args.url_base.rstrip("/").endswith("civica.avance.org.co/tl-publisher"):
        print(
            "AVISO: se va a importar desde el despliegue de PRODUCCION "
            "(civica.avance.org.co).",
            file=sys.stderr,
        )

    import xml.etree.ElementTree as ET

    (PKI_PUBLICO / "ca").mkdir(parents=True, exist_ok=True)
    manifiesto = []
    vistos: set[str] = set()

    for esquema, (clave, canonico) in ESQUEMAS.items():
        url = f"{args.url_base}/TL/{esquema}/current.xml"
        print(f"==> {esquema}: {url}")
        try:
            cuerpo = descargar(url)
        except Exception as exc:  # noqa: BLE001
            print(f"    ERROR: no se pudo descargar: {exc}", file=sys.stderr)
            return 1

        raiz = ET.fromstring(cuerpo)
        for servicio in raiz.iter(NS + "TSPService"):
            tipo = servicio.find(".//" + NS + "ServiceTypeIdentifier")
            nombre = servicio.find(".//" + NS + "ServiceName/" + NS + "Name")
            if tipo is None or tipo.text != canonico:
                # La lista puede traer servicios de otros tipos; sólo interesan
                # los de su propio esquema.
                continue
            for cert_b64 in servicio.findall(".//" + NS + "X509Certificate"):
                der = base64.b64decode("".join(cert_b64.text.split()))
                cert = x509.load_der_x509_certificate(der)

                slug = slug_de(esquema, cert)
                if slug in vistos:
                    continue
                vistos.add(slug)

                pem = cert.public_bytes(serialization.Encoding.PEM)
                (PKI_PUBLICO / "ca" / f"{slug}.crt").write_bytes(pem)

                manifiesto.append(
                    dict(
                        slug=slug,
                        tipo=clave,
                        canonico=canonico,
                        nombre=(nombre.text if nombre is not None else slug),
                        supply_points=crldp(cert),
                        sha256=cert.fingerprint(hashes.SHA256()).hex(),
                        origen=f"{args.url_base}/TL/{esquema}/current.xml",
                    )
                )
                print(f"    + {slug}  ({cert.subject.rfc4514_string()})")

    destino = PKI_PUBLICO / "anclas.json"
    destino.write_text(
        json.dumps(dict(anclas=manifiesto), indent=2, ensure_ascii=False) + "\n"
    )
    print()
    print(f"==> {len(manifiesto)} anclas importadas")
    print(f"==> manifiesto: {destino}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
