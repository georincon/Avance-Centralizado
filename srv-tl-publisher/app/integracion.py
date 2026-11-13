"""Configuracion para conectar un verifier oficial a esta capa de confianza.

El verifier oficial (eudi-srv-verifier-endpoint) no valida un documento cuyo tipo
no este clasificado ("Could not find Attestation Classification"), y la clase
decide en que lista busca al emisor (contexto PID, QEAA o PubEAA del
trust-validator). Las clases salen de la metadata OpenID4VCI de los emisores
activos registrados, via su punto de suministro.
"""

import datetime as dt
import json

import admin
import bd
import config

# Lo que es un PID aunque ningun emisor registrado lo declare todavia.
_PID_BASE = {"vcts": ["urn:eudi:pid:1"], "docTypes": ["eu.europa.ec.eudi.pid.1"]}

# Rol en el registro -> clave de VERIFIER_ATTESTATIONCLASSIFICATIONS, en orden de
# prioridad: si dos emisores de listas distintas declaran el mismo tipo, gana el
# primero (un PID sigue siendo PID aunque tambien lo emita un organismo publico).
_CLASE_DE_ROL = {"pid": "pid", "qeaa": "qeaa", "pub-eaa": "pubeaa"}


# Los dos emisores (Registraduria y UIS) comparten maquina y sus Keycloak no pueden estar
# arriba a la vez, asi que la metadata de uno casi siempre esta apagada. Cada lectura buena
# se recuerda aqui y se usa cuando el emisor no responde: el verifier no puede depender de
# que el emisor este encendido para saber que tipo de documento es.
_CACHE = config.ALMACEN / "emisores-cache.json"


def _cache() -> dict:
    try:
        return json.loads(_CACHE.read_text())
    except (OSError, ValueError):
        return {}


def _leer_con_memoria(url: str) -> tuple[dict, str | None]:
    """Metadata del emisor; si no responde, la ultima lectura buena. Devuelve (lectura, aviso)."""
    r = admin.leer_emisor(url)
    memoria = _cache()
    if r.get("configuraciones"):
        memoria[url] = {"configuraciones": r["configuraciones"],
                        "leido": dt.datetime.now(dt.timezone.utc).isoformat(timespec="seconds")}
        try:
            _CACHE.write_text(json.dumps(memoria, indent=1))
        except OSError:
            pass
        return r, None
    if url in memoria:
        copia = memoria[url]
        return ({"configuraciones": copia["configuraciones"]},
                f"{url} no responde: se usa la lectura del {copia['leido']}")
    return r, None


def clasificaciones() -> tuple[dict, list[str]]:
    """Que vct y docType es PID, QEAA o PubEAA, y avisos de lo que no se pudo leer."""
    clases = {"pid": {"vcts": list(_PID_BASE["vcts"]), "docTypes": list(_PID_BASE["docTypes"])},
              "qeaa": {"vcts": [], "docTypes": []}, "pubeaa": {"vcts": [], "docTypes": []}}
    vistos = set(_PID_BASE["vcts"]) | set(_PID_BASE["docTypes"])
    notas: list[str] = []
    with bd.conexion() as con:
        actores = admin._cargar_actores(con)
    activos = [a for a in actores if a["estado"] == admin.ESTADO_ACTIVO and a["rol"] in _CLASE_DE_ROL]
    for rol, clase in _CLASE_DE_ROL.items():
        for actor in (a for a in activos if a["rol"] == rol):
            # Los puntos de suministro tambien son CRL y OCSP de la PKI: no son emisores.
            emisores = [u for u in actor["puntos"] if "/pki/" not in u]
            if not emisores:
                continue
            if not actor["puntos"]:
                notas.append(f"{actor['nombre']} (#{actor['id']}): sin URL OpenID4VCI, no se sabe que emite")
            for url in emisores:
                r, aviso = _leer_con_memoria(url)
                if aviso:
                    notas.append(f"{actor['nombre']} (#{actor['id']}): {aviso}")
                if not r.get("configuraciones"):
                    notas.append(f"{actor['nombre']} (#{actor['id']}): {r.get('detalle')}")
                    continue
                for c in r["configuraciones"]:
                    if not c["tipo"] or c["tipo"] in vistos:
                        continue
                    vistos.add(c["tipo"])
                    clases[clase]["docTypes" if c["formato"] == "mso_mdoc" else "vcts"].append(c["tipo"])
    return {**clases, "eaa": []}, notas


def verifier_env() -> str:
    """Variables del verifier oficial para esta capa de confianza."""
    clases, notas = clasificaciones()
    lineas = [
        f"# Generado por el tl-publisher: {config.BASE_URL}/tl-api/v1/integracion/verifier.env",
        "# El verifier pregunta al trust-validator si el emisor de cada documento esta en",
        "# la lista de su clase (PID, QEAA o PubEAA). Las clases salen de la metadata de",
        "# los emisores registrados: vuelve a descargarlo cuando se registre uno nuevo.",
        *(f"# Aviso: {n}" for n in notas),
        f"VERIFIER_TRUSTVALIDATOR_SERVICEURL={config.TRUST_VALIDATOR_URL}",
        f"VERIFIER_ATTESTATIONCLASSIFICATIONS='{json.dumps(clases, separators=(',', ':'))}'",
    ]
    return "\n".join(lineas) + "\n"
