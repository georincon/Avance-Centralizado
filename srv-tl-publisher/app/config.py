"""Configuración del publicador de Trusted Lists.

Todo sale del entorno, con valores por defecto que funcionan dentro del compose.
La única variable que hay que tocar al mover el despliegue es `PUBLIC_DOMAIN`:
el resto de URLs se derivan de ella, igual que en el resto del ecosistema.
"""

import os
from pathlib import Path

# ─── Identidad pública ───────────────────────────────────────────────────────

DOMINIO = os.getenv("PUBLIC_DOMAIN", "localhost")
BASE_URL = f"https://{DOMINIO}"

# ─── Base de datos del TLP ───────────────────────────────────────────────────
#
# Se lee la MISMA base que usa el TLP, y eso es deliberado: el TLP sigue siendo
# el back-office donde un operador da de alta proveedores y servicios. Este
# servicio no duplica ese modelo de datos, lo publica.
#
# De las tablas del TLP sólo se LEE. Las únicas que se escriben son las dos que
# añade el Parche 9 (`published_lists`, `tl_schemes`, `service_type_registry`) y
# `trusted_list_updates`, que existía desde el principio y en la que hasta ahora
# no escribía nadie.

BD = dict(
    host=os.getenv("DB_HOST", "tlp-db"),
    port=int(os.getenv("DB_PORT", "3306")),
    user=os.getenv("DB_USER", "tlp_user"),
    password=os.getenv("DB_PASSWORD", ""),
    database=os.getenv("DB_NAME", "trusted_lists"),
)

# ─── Material de firma ───────────────────────────────────────────────────────
#
# El MISMO firmante que usa el TLP: la hoja que emite la `Civica TL Signing CA`
# (Fase 1.4). Que ambos firmen con la misma clave es lo correcto — quien valida
# ve un único firmante de trusted lists para este esquema, no dos.

CERT_FIRMA = Path(os.getenv("SIGNER_CERT", "/app/firma/tl-signer.crt"))
CLAVE_FIRMA = Path(os.getenv("SIGNER_KEY", "/app/firma/tl-signer.key"))

# ─── Almacén de listas publicadas ────────────────────────────────────────────
#
# Un volumen. Una lista publicada es INMUTABLE: se sirve por su número de
# secuencia para siempre, así que no puede vivir en el sistema de ficheros de un
# contenedor que se recrea.

ALMACEN = Path(os.getenv("STORE_PATH", "/var/lib/tl-publisher"))

# ─── Ritmo de publicación ────────────────────────────────────────────────────
#
# POR QUÉ 24 HORAS Y NO LOS 6 MESES DEL UPSTREAM
#
# `ConfXML.validity` traía 6 meses. Una TL caducada no es un aviso: es un FALLO
# de validación. Con 6 meses, cualquier corte de servicio largo deja a todo el
# ecosistema sin poder validar nada y nadie se entera hasta que pasa.
#
# Con 24 h y republicación automática al 80% del intervalo, la lista se refresca
# sola cada ~19 h. Si el publicador se cae, hay ~5 h de margen para arreglarlo
# antes de que algo deje de validar.

HORAS_VALIDEZ = int(os.getenv("VALIDITY_HOURS", "24"))
FRACCION_REPUBLICACION = float(os.getenv("REPUBLISH_FRACTION", "0.8"))

# Cada cuánto despierta el trabajo de fondo a mirar si toca republicar. No es el
# intervalo de republicación: es la resolución con la que lo detecta.
SEGUNDOS_ENTRE_REVISIONES = int(os.getenv("CHECK_INTERVAL_SECONDS", "900"))

# ─── Autenticación de las operaciones de administración ──────────────────────
#
# Publicar es una operación privilegiada: fija un número de secuencia que no se
# puede reutilizar. Va detrás de una API key, y si no está configurada el
# endpoint responde 503 en vez de quedar abierto.

API_KEY = os.getenv("PUBLISHER_API_KEY", "")

# ─── La administracion (el back-office) ──────────────────────────────────────
#
# La interfaz de administracion se abre con una clave de operador que viaja en
# la cabecera `X-Api-Key`. Sin ella, la administracion responde 503: es
# preferible que no exista a que exista abierta.
#
# Es una credencial de la persona que da de alta emisores y firma las listas
# con la CA de confianza del ecosistema: la mas privilegiada del despliegue.

CLAVE_OPERADOR = os.getenv("TL_OPERATOR_KEY", "")

# ─── Emision de certificados de acceso ───────────────────────────────────────
#
# El publicador NO tiene las claves privadas: la CA de acceso las tiene el
# servicio de PKI, que es el unico autorizado a firmar hojas. Este publicador
# le pide ahi el certificado de un verifier a partir de su CSR.

PKI_EMITIR_URL = os.getenv("PKI_ISSUE_URL", "http://tl-publisher-pki:8080/issue/rp-access")
PKI_EMITIR_KEY = os.getenv("PKI_ISSUE_API_KEY", "")

# Donde los verifiers preguntan por la confianza de un emisor. Es lo que `integracion.py`
# entrega en verifier.env. Es la URL PUBLICA del dominio y no `http://trust-validator:8080`:
# el verifier-api de cada instancia no se une a la red avance-eudi (ver verifiers/_comun/compose.yml).
TRUST_VALIDATOR_URL = os.getenv("TRUST_VALIDATOR_URL", f"{BASE_URL}/trust")

# ─── Metadatos del esquema ───────────────────────────────────────────────────

TERRITORIO = os.getenv("SCHEME_TERRITORY", "CO")

# Reglas de comunidad del esquema.
#
# NO se declara `EUcommon`: sería mentira. Este no es un esquema de la UE ni
# nuestra raíz está en la LOTL de la Comisión. Se declara un URI propio y
# resoluble, que es la forma correcta de hacer conforme una desviación en vez de
# esconderla. Ver docs/03-arquitectura.md.
REGLAS_COMUNIDAD = os.getenv(
    "SCHEME_RULES_URI", f"{BASE_URL}/trust/schemerules/CIVICA"
)

XSD = Path(__file__).resolve().parent / "ts_119612v020101.xsd"
