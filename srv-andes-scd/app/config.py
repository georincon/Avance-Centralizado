"""Configuracion del simulador, toda por variables de entorno (con valores de pruebas)."""

import os
from pathlib import Path

DOMINIO = os.environ.get("ANDES_DOMINIO", "civica-desarrollo.avance.org.co")
PREFIJO = os.environ.get("ANDES_PREFIJO", "andes-scd").strip("/")

# Direccion publica del servicio. Es el `issuer` del servidor OAuth2: la wallet compara el
# valor del metadata con el que ella uso para pedirlo, asi que no debe llevar barra final.
URL_PUBLICA = os.environ.get("ANDES_URL_PUBLICA", f"https://{DOMINIO}/{PREFIJO}").rstrip("/")
URL_PKI = os.environ.get("ANDES_URL_PKI", f"https://{DOMINIO}/pki").rstrip("/")

# La sub-CA QTSP del ecosistema (srv-tl-publisher/pki/subca/qtsp) y la carpeta publica de la PKI.
DIR_CA = Path(os.environ.get("ANDES_DIR_CA", "/pki/subca/qtsp"))
DIR_PKI_PUBLICO = Path(os.environ.get("ANDES_DIR_PKI_PUBLICO", "/pki/publico"))
DIR_DATOS = Path(os.environ.get("ANDES_DIR_DATOS", "/datos"))

# El cliente OAuth2 de la wallet (el mismo par que va en RQESConfigImpl de la app).
CLIENT_ID = os.environ.get("ANDES_CLIENT_ID", "wallet-client")
CLIENT_SECRET = os.environ.get("ANDES_CLIENT_SECRET", "andes-secreto-de-pruebas")
REDIRECT_URIS = {
    u.strip()
    for u in os.environ.get("ANDES_REDIRECT_URIS", "rqes://oauth/callback").split(",")
    if u.strip()
}

# Tipo de clave de los certificados de firma nuevos: "ec" (ECDSA P-256) o "rsa" (RSA 2048). Con RSA-2048 la
# librería nativa de la wallet (PoDoFo) incrusta la firma en el PDF recortada a 128 bytes (comprobado con un PDF
# firmado en un teléfono real); con ECDSA la firma es corta y cabe.
TIPO_CLAVE = os.environ.get("ANDES_TIPO_CLAVE", "ec").lower()

# Como se identifica el firmante al autorizar: "pid" (presenta su PID desde la wallet, por el verifier del servicio)
# o "pin" (documento + PIN; solo para pruebas automaticas sin telefono).
AUTENTICACION = os.environ.get("ANDES_AUTENTICACION", "pid").lower()
VERIFIER_API = os.environ.get("ANDES_VERIFIER_API", "http://verifier-firma/verifier-firma").rstrip("/")
PID_VCT = os.environ.get("ANDES_PID_VCT", "urn:eudi:pid:1")
# Datos del PID que se piden: lo minimo para emitir el certificado de firma (documento y nombre).
PID_DATOS = ["personal_administrative_number", "given_name", "family_name"]

# Direccion con la que los certificados anuncian su revocacion (OCSP), su CRL y su CA emisora. Va por http A PROPOSITO: la
# biblioteca de certificados de los visores de PDF de Linux (NSS: Poppler/pdfsig, Okular) solo descarga la revocacion por
# http; con https:// el certificado les sale "problema desconocido" aunque la firma sea valida. Es lo habitual en las PKI
# (el contenido va firmado, no hace falta cifrarlo). El resto del servicio sigue por https.
URL_PKI_REVOCACION = os.environ.get("ANDES_URL_PKI_REVOCACION", f"http://{DOMINIO}/pki").rstrip("/")

# Los certificados emitidos empiezan a valer esta cantidad de horas ANTES de emitirse (practica habitual de las CA para
# tolerar relojes desajustados). Importa de verdad: algunos visores de PDF (Poppler, el de Okular y Evince) validan el
# certificado en la hora de firma que declara el PDF, que guarda la hora local sin convertirla a UTC; con -5 h de zona
# horaria, un certificado recien emitido les parece "todavia no valido" en el momento de la firma. 24 h cubre cualquier zona.
RETROCESO_CERT_HORAS = int(os.environ.get("ANDES_RETROCESO_CERT_HORAS", "24"))

TTL_TOKEN = int(os.environ.get("ANDES_TTL_TOKEN", "3600"))
TTL_CODIGO = int(os.environ.get("ANDES_TTL_CODIGO", "300"))
TTL_PAR = int(os.environ.get("ANDES_TTL_PAR", "300"))
MAX_INTENTOS_PIN = 5

TIPO_AUTORIZACION_CREDENCIAL = "https://cloudsignatureconsortium.org/2025/credential"

# Politica de certificacion del certificado de firma del usuario (perfil `qtsp-user` de pki/perfiles.sh).
OID_POLITICA_USUARIO = "2.25.224783951054833873364394138650481919266.1.5"
# Politica del sello de tiempo (rama propia del simulador).
OID_POLITICA_TSA = "2.25.224783951054833873364394138650481919266.1.9"

NOMBRE_PRESTADOR = "Andes SCD (simulador)"
