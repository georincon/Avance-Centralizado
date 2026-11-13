#!/usr/bin/env python3
"""Genera docker-compose/config/* (config del backend, del auth-server y del status-list) a partir de
.env y de los archivos de ejemplo de cada repo, sin modificarlos. Idempotente. Uso: scripts/configurar.py"""
import json, os, re, secrets
from pathlib import Path
import yaml

RAIZ = Path(__file__).resolve().parent.parent
env = {}
for linea in (RAIZ / ".env").read_text().splitlines():
    m = re.match(r"^([A-Z_]+)=(.*)$", linea)
    if m:
        env[m.group(1)] = m.group(2).strip().strip('"')
D, P = env["DOMINIO"], env["PREFIJO"]
BASE = f"https://{D}/{P}"                    # frontend = credential_issuer
API, OIDC = f"{BASE}/api", f"{BASE}/oidc"    # backend y servidor de autorización
SALIDA = RAIZ / "docker-compose" / "config"
SALIDA.mkdir(parents=True, exist_ok=True)

# Secretos de servicio: se crean una vez y se reutilizan.
sec = RAIZ / "secretos"
api_key = sec / "status-list-api-key"
if not api_key.exists():
    api_key.write_text(secrets.token_urlsafe(24))
clave = api_key.read_text().strip()

# --- backend ---------------------------------------------------------------------------------
c = yaml.safe_load((RAIZ / "backend/app/config_issuer_backend_example.yaml").read_text())
c["service_url"] = API
# Las billeteras del ecosistema abren openid-credential-offer:// (no haip-vci://).
c["credential_offer_scheme"] = "openid-credential-offer://"
c["wallet_tester_url"] = BASE
c["revocation"] = {"take_url": "http://status-list-notaria:5000/token_status_list/take",
                   "set_url": "http://status-list-notaria:5000/token_status_list/set",
                   "api_key": clave, "enabled": True}
fid = "notaria-issuer-frontend"
c["frontend"] = {"default": fid, "frontends_config": {fid: {
    "url": BASE,
    "metadata_signing_key_path": "/etc/eudiw/notaria/privKey/ds.key",
    "metadata_access_certificate_path": "/etc/eudiw/notaria/cert/ds.crt",
    "metadata_signing_key_password": None,
    "registration_certificate_path": None}}}
c["authorization_server"] = {"base_url": OIDC, "internal_url": "http://notaria-issuer-oidc:5000",
                             "user_verify_endpoint": f"{BASE}/oidc/verify/user"}
# Sin PID de otros emisores: la identificación es la propia de los repos Python (formulario, país "FC").
c["dynamic_presentation_url"] = None
# Inicio de sesión con la wallet: se pide el PID por el verifier de la notaría (ecosistema).
c["pid_login"] = {"required": True,
                  "verifier_api": f"http://verifier-{env['VERIFIER_INSTANCIA']}/verifier-{env['VERIFIER_INSTANCIA']}"}
c["trust_validator"] = {"url": "http://trust-validator:8080/trust", "enabled": True}
c["status_validator"] = {"url": "", "enabled": False}
c["postgres"] = {"host": "postgres-notaria-issuer", "port": 5432, "dbname": "notaria_issuer",
                 "user": "notaria_issuer", "password": "notaria_issuer"}
c["logging"] = {"backend_path": "/tmp/notaria-issuer/logs.log", "log_level": "INFO"}
c["keys"] = {"nonce_path": "/etc/eudiw/notaria/privKey/nonce_rsa2048.pem",
             "credential_request_path": "/etc/eudiw/notaria/privKey/credential_request.pem"}
c["trusted_CAs_path"] = "/etc/eudiw/notaria/cert/"
ids = [p.stem for p in []]
cred = [next(iter(json.loads(f.read_text()))) for f in sorted((RAIZ / "backend/app/metadata_config/credentials_supported").glob("*.json"))]
c["credential_auth_methods"] = {"PID_login": [], "country_selection": cred}
c["countries"] = {"FC": {"name": "Notaría", "un_distinguishing_sign": "CO",
    "keys": {"_default": {"private_key_path": "/etc/eudiw/notaria/privKey/ds.key", "private_key_password": None,
                          "certificate_path": "/etc/eudiw/notaria/cert/ds.der"}},
    "supported_credential_ids": cred}}
(SALIDA / "config_issuer_backend.yaml").write_text(yaml.safe_dump(c, sort_keys=False, allow_unicode=True))

# --- auth-server -----------------------------------------------------------------------------
a = json.loads((RAIZ / "auth-server/config.json").read_text())
a["domain"] = f"{D}/{P}/oidc"
a["base_url"] = OIDC
a["authorization_redirect_url"] = f"{API}/auth_choice"  # la ruta vive en el backend
srv = a["op"]["server_info"]
srv["issuer"] = f"https://{D}/{P}/oidc"
def parchear(o):
    if isinstance(o, dict):
        for k, v in list(o.items()):
            if k == "allowed_htu":
                o[k] = [f"{OIDC}/token", f"{API}/token"]
            elif k == "trusted_attesters_path":
                o[k] = "/etc/eudiw/notaria/oidc_trusted_attesters"
            elif k == "trust_validator_url":
                o[k] = "http://trust-validator:8080/trust"
            elif k == "status_validator_url":
                o[k] = ""
            else:
                parchear(v)
    elif isinstance(o, list):
        for v in o: parchear(v)
parchear(a)
(SALIDA / "authorization_config.json").write_text(json.dumps(a, indent=2))

# metadata RFC 8414 / OIDC que sirve el servidor de autorización: el archivo del repo trae el dominio de la UE
om = (RAIZ / "auth-server/openid-configuration.json").read_text().replace("https://dev.issuer.eudiw.dev/oidc", OIDC)
(SALIDA / "openid-configuration.json").write_text(om)

# --- frontend --------------------------------------------------------------------------------
f = {"service_url": BASE, "log_dir": "/tmp/notaria-issuer-frontend/logs/logs.log", "frontend_id": fid,
     "backend_url": "http://notaria-issuer-backend:5000", "backend_public_url": API, "oauth_url": OIDC, "credentials_supported": cred}
(SALIDA / "frontend_config.yaml").write_text(yaml.safe_dump(f, sort_keys=False, allow_unicode=True))

# --- status-list -----------------------------------------------------------------------------
print(f"config en {SALIDA}  (issuer {BASE})")
