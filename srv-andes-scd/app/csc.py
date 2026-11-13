"""API de firma remota CSC v2 (Cloud Signature Consortium) en la parte que usa la wallet:
info, credentials/list, credentials/info, signatures/signHash y signatures/timestamp, mas el
sellado de tiempo RFC 3161 en /tsa."""

from __future__ import annotations

import base64
import subprocess
import tempfile
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, utils
from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse, Response

from . import ca, config, db, seguridad, servicio, tsa

router = APIRouter()
_autoridad: ca.Autoridad | None = None

OID_RSA = "1.2.840.113549.1.1.1"
OID_EC = "1.2.840.10045.2.1"
OID_CURVA_P256 = "1.2.840.10045.3.1.7"
ALGORITMOS_FIRMA = {
    # signAlgo -> hash implicito (None: lo da hashAlgorithmOID)
    "1.2.840.10045.2.1": None,
    "1.2.840.10045.4.3.2": "2.16.840.1.101.3.4.2.1",
    "1.2.840.10045.4.3.3": "2.16.840.1.101.3.4.2.2",
    "1.2.840.10045.4.3.4": "2.16.840.1.101.3.4.2.3",
    "1.2.840.113549.1.1.1": None,
    "1.2.840.113549.1.1.11": "2.16.840.1.101.3.4.2.1",
    "1.2.840.113549.1.1.12": "2.16.840.1.101.3.4.2.2",
    "1.2.840.113549.1.1.13": "2.16.840.1.101.3.4.2.3",
}
HASHES = {
    "2.16.840.1.101.3.4.2.1": hashes.SHA256,
    "2.16.840.1.101.3.4.2.2": hashes.SHA384,
    "2.16.840.1.101.3.4.2.3": hashes.SHA512,
}
HASH_OPENSSL = {
    "2.16.840.1.101.3.4.2.1": "-sha256",
    "2.16.840.1.101.3.4.2.2": "-sha384",
    "2.16.840.1.101.3.4.2.3": "-sha512",
}


def fijar_autoridad(a: ca.Autoridad) -> None:
    global _autoridad
    _autoridad = a


def _error(codigo: str, descripcion: str, estado: int = 400) -> JSONResponse:
    return JSONResponse({"error": codigo, "error_description": descripcion}, status_code=estado)


def _token(request: Request):
    """Fila del token Bearer vigente, o None."""
    auth = request.headers.get("authorization", "")
    if not auth.lower().startswith("bearer "):
        return None
    with db.transaccion() as c:
        fila = c.execute("SELECT * FROM tokens WHERE token = ?", (auth[7:].strip(),)).fetchone()
    if not fila or fila["expira"] < db.ahora():
        return None
    return fila


@router.api_route("/csc/v2/info", methods=["GET", "POST"])
async def info() -> dict:
    return {
        "specs": "2.2.0.0",
        "name": config.NOMBRE_PRESTADOR,
        "logo": f"{config.URL_PUBLICA}/static/logo.svg",
        "region": "CO",
        "lang": "es-CO",
        "description": "Simulador de firma remota del ecosistema Avance. Solo para pruebas.",
        "authType": ["oauth2code", "oauth2client"],
        "oauth2Issuer": config.URL_PUBLICA,
        "supportsRar": True,
        "supportedHashTypes": ["DTBS", "DTBSR"],
        "asynchronousOperationMode": False,
        "methods": [
            "info", "credentials/list", "credentials/info",
            "signatures/signHash", "signatures/timestamp",
        ],
        "validationInfo": False,
        "signAlgorithms": {"algos": list(ALGORITMOS_FIRMA)},
        "signature_formats": {"formats": ["P", "C", "X"], "envelope_properties": [["Enveloped"], ["Detached"], ["Detached"]]},
        "conformance_levels": ["Ades-B-B", "Ades-B-T"],
    }


def _fecha(t) -> str:
    return t.strftime("%Y%m%d%H%M%S") + "Z"


def _info_credencial(fila, certificados: str, cert_info: bool) -> dict:
    cert = x509.load_pem_x509_certificate(bytes(fila["cert_pem"]))
    cadena = [ca.b64_der(bytes(fila["cert_pem"]))]
    if certificados == "chain":
        for pem in _cadena_ca():
            cadena.append(ca.b64_der(pem))
    es_ec = ca.tipo_de_clave(cert.public_key()) == "ec"
    cert_bloque: dict = {"status": "valid" if fila["estado"] == "valid" else "revoked"}
    if certificados != "none":
        cert_bloque["certificates"] = cadena
    if cert_info or True:
        cert_bloque.update({
            "issuerDN": cert.issuer.rfc4514_string(),
            "serialNumber": str(cert.serial_number),
            "subjectDN": cert.subject.rfc4514_string(),
            "validFrom": _fecha(cert.not_valid_before_utc),
            "validTo": _fecha(cert.not_valid_after_utc),
        })
    return {
        "description": "Certificado de firma electrónica de prueba",
        "signatureQualifier": "eu_eidas_aes",
        "key": (
            {"status": "enabled", "algo": ["1.2.840.10045.4.3.2", OID_EC], "len": 256, "curve": OID_CURVA_P256}
            if es_ec else
            {"status": "enabled", "algo": [OID_RSA, "1.2.840.113549.1.1.11"], "len": 2048}
        ),
        "cert": cert_bloque,
        "auth": {"mode": "oauth2code", "objects": None},
        "SCAL": "1",
        "multisign": 100,
        "lang": "es-CO",
    }


def _cadena_ca() -> list[bytes]:
    pems = []
    for bloque in _autoridad.cadena_pem.split(b"-----END CERTIFICATE-----"):
        if b"-----BEGIN CERTIFICATE-----" in bloque:
            pems.append(bloque.strip() + b"\n-----END CERTIFICATE-----\n")
    return pems


@router.post("/csc/v2/credentials/list")
async def credentials_list(request: Request):
    tok = _token(request)
    if tok is None:
        return _error("invalid_token", "token ausente o vencido", 401)
    if tok["tipo"] != "usuario":
        return _error("access_denied", "el token no identifica a ningun firmante", 403)
    cuerpo = await _json(request)
    filas = servicio.credenciales_de(tok["documento"])
    respuesta: dict = {"credentialIDs": [f["id"] for f in filas]}
    if cuerpo.get("credentialInfo"):
        respuesta["credentialInfos"] = [
            {"credentialID": f["id"], **_info_credencial(f, cuerpo.get("certificates") or "single", bool(cuerpo.get("certInfo")))}
            for f in filas
        ]
    return respuesta


@router.post("/csc/v2/credentials/info")
async def credentials_info(request: Request):
    tok = _token(request)
    if tok is None:
        return _error("invalid_token", "token ausente o vencido", 401)
    cuerpo = await _json(request)
    fila = servicio.credencial(cuerpo.get("credentialID", ""))
    if fila is None or fila["documento"] != tok["documento"]:
        return _error("invalid_request", "credencial desconocida")
    datos = _info_credencial(fila, cuerpo.get("certificates") or "single", bool(cuerpo.get("certInfo")))
    datos["cert"]["qcStatements"] = []
    datos["cert"]["policy"] = [config.OID_POLITICA_USUARIO]
    return datos


@router.post("/csc/v2/signatures/signHash")
async def sign_hash(request: Request):
    tok = _token(request)
    if tok is None:
        return _error("invalid_token", "token ausente o vencido", 401)
    cuerpo = await _json(request)
    cid = cuerpo.get("credentialID", "")
    fila = servicio.credencial(cid)
    if fila is None or fila["documento"] != tok["documento"]:
        return _error("invalid_request", "credencial desconocida")

    # La firma solo se hace con un token que autorizo ESTAS huellas (flujo credential de CSC):
    # un token de simple inicio de sesion no firma nada.
    detalles = db.loads(tok["detalles"])
    if not detalles:
        return _error("access_denied", "el token no autoriza ninguna firma", 403)
    autorizadas = {
        seguridad.b64_estandar(h["hash"])
        for d in detalles if d.get("credentialID") == cid
        for h in (d.get("documentDigests") or [])
    }
    maximo = sum(int(d.get("numSignatures") or 0) for d in detalles if d.get("credentialID") == cid)
    huellas = cuerpo.get("hashes") or []
    if not huellas or len(huellas) > max(maximo, 1):
        return _error("invalid_request", "cantidad de huellas no autorizada")
    if autorizadas and any(seguridad.b64_estandar(h) not in autorizadas for h in huellas):
        return _error("access_denied", "una huella no coincide con lo autorizado", 403)

    algoritmo = cuerpo.get("signAlgo", "")
    if algoritmo not in ALGORITMOS_FIRMA:
        return _error("invalid_request", f"signAlgo no admitido: {algoritmo}")
    hash_oid = ALGORITMOS_FIRMA[algoritmo] or cuerpo.get("hashAlgorithmOID", "")
    if hash_oid not in HASHES:
        return _error("invalid_request", f"hashAlgorithmOID no admitido: {hash_oid}")
    clase_hash = HASHES[hash_oid]

    clave = ca.descifrar_clave(bytes(fila["clave_cifrada"]))
    firmas = []
    for h in huellas:
        crudo = seguridad.decodificar_b64(h)
        if len(crudo) != clase_hash.digest_size:
            return _error("invalid_request", "la longitud de la huella no corresponde al hash")
        if ca.tipo_de_clave(clave) == "ec":
            if not algoritmo.startswith("1.2.840.10045."):
                return _error("invalid_request", "la credencial es ECDSA y signAlgo no lo es")
            # Firma ECDSA en DER (ECDSA-Sig-Value), que es lo que lleva una firma CMS/PAdES.
            firma = clave.sign(crudo, ec.ECDSA(utils.Prehashed(clase_hash())))
        else:
            if algoritmo.startswith("1.2.840.10045."):
                return _error("invalid_request", "la credencial es RSA y signAlgo no lo es")
            firma = clave.sign(crudo, padding.PKCS1v15(), utils.Prehashed(clase_hash()))
        firmas.append(base64.b64encode(firma).decode())

    with db.transaccion() as c:
        c.execute(
            "INSERT INTO firmas (fecha, documento, credencial, huellas, algoritmo) VALUES (?,?,?,?,?)",
            (db.ahora(), tok["documento"], cid, db.dumps(huellas), algoritmo),
        )
    return {"signatures": firmas}


@router.post("/csc/v2/signatures/timestamp")
async def timestamp(request: Request):
    tok = _token(request)
    if tok is None:
        return _error("invalid_token", "token ausente o vencido", 401)
    cuerpo = await _json(request)
    flag = HASH_OPENSSL.get(cuerpo.get("hashAlgorithmOID", ""))
    if not flag or not cuerpo.get("hash"):
        return _error("invalid_request", "falta hash o hashAlgorithmOID no admitido")
    huella = seguridad.decodificar_b64(cuerpo["hash"])
    with tempfile.TemporaryDirectory() as tmp:
        consulta = Path(tmp) / "q.tsq"
        proc = subprocess.run(
            ["openssl", "ts", "-query", "-digest", huella.hex(), flag, "-cert", "-no_nonce", "-out", str(consulta)],
            capture_output=True, timeout=10,
        )
        if proc.returncode != 0:
            return _error("invalid_request", "no se pudo preparar la consulta de sello")
        respuesta = tsa.sellar(consulta.read_bytes())
    return {"timestamp": base64.b64encode(respuesta).decode()}


@router.post("/tsa")
async def sellado(request: Request):
    """Sellado de tiempo RFC 3161: cuerpo application/timestamp-query, respuesta timestamp-reply."""
    consulta = await request.body()
    if not consulta:
        return Response("consulta vacia", status_code=400)
    try:
        return Response(tsa.sellar(consulta), media_type="application/timestamp-reply")
    except tsa.TSAError as e:
        return Response(f"consulta de sello no valida: {e}", status_code=400)


async def _json(request: Request) -> dict:
    try:
        cuerpo = await request.json()
        return cuerpo if isinstance(cuerpo, dict) else {}
    except Exception:
        return {}
