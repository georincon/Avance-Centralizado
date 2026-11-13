"""Autoridad de sellado de tiempo RFC 3161 (el telefono la llama directo con `tsaUrl`).

La respuesta la firma `openssl ts -reply`, que implementa el protocolo completo; el servicio solo
prepara su certificado (emitido por la sub-CA QTSP) y su configuracion la primera vez.
"""

from __future__ import annotations

import subprocess
import tempfile
import threading
from pathlib import Path

from cryptography.hazmat.primitives import serialization

from . import ca, config

_candado = threading.Lock()


class TSAError(Exception):
    pass


def _dir() -> Path:
    return config.DIR_DATOS / "tsa"


def preparar(autoridad: ca.Autoridad) -> None:
    """Crea clave, certificado, cadena, serie y configuracion de la TSA si todavia no existen."""
    d = _dir()
    d.mkdir(parents=True, exist_ok=True)
    clave_ruta = d / "tsa.key"
    cert_ruta = d / "tsa.crt"
    if not clave_ruta.exists() or not cert_ruta.exists():
        clave = ca.clave_tsa_nueva()
        cert_pem, _serie = ca.emitir_certificado_tsa(autoridad, clave.public_key())
        clave_ruta.write_bytes(
            clave.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            )
        )
        clave_ruta.chmod(0o600)
        cert_ruta.write_bytes(cert_pem)
    (d / "tsa-cadena.pem").write_bytes(autoridad.cadena_pem)
    serie = d / "tsaserial"
    if not serie.exists():
        serie.write_text("01\n")
    (d / "tsa.cnf").write_text(
        f"""[ tsa ]
default_tsa = tsa_config

[ tsa_config ]
dir = {d}
serial = $dir/tsaserial
crypto_device = builtin
signer_cert = $dir/tsa.crt
certs = $dir/tsa-cadena.pem
signer_key = $dir/tsa.key
signer_digest = sha256
default_policy = {config.OID_POLITICA_TSA}
digests = sha256, sha384, sha512
accuracy = secs:1
ordering = no
tsa_name = yes
ess_cert_id_alg = sha256
"""
    )


def sellar(consulta: bytes) -> bytes:
    """Recibe una TimeStampReq (DER) y devuelve la TimeStampResp (DER)."""
    d = _dir()
    with _candado, tempfile.TemporaryDirectory() as tmp:
        q = Path(tmp) / "q.tsq"
        r = Path(tmp) / "r.tsr"
        q.write_bytes(consulta)
        proc = subprocess.run(
            ["openssl", "ts", "-reply", "-config", str(d / "tsa.cnf"), "-section", "tsa_config",
             "-queryfile", str(q), "-out", str(r)],
            capture_output=True, timeout=20,
        )
        if proc.returncode != 0 or not r.exists():
            raise TSAError(proc.stderr.decode(errors="replace")[-300:] or "openssl ts fallo")
        return r.read_bytes()
