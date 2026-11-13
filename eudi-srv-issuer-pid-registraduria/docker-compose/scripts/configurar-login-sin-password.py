#!/usr/bin/env python3
"""
Configura en Keycloak (pid-registraduria-realm) un *authentication flow* de
browser sin contraseña para un cliente OAuth2 puntual (Opción A, sección 5 de
CONTEXTO-REGISTRADURIA.md): copia el flow "browser" estándar, cambia su
subflow de formularios para pedir solo "Username Form" (sin password, sin
OTP), y lo enlaza con `authenticationFlowBindingOverrides.browser` **solo al
cliente indicado** — igual que `apply-issuer-state-binding.sh` hace para
`newwallet-app` con el flow `issuer-state-browser`.

Ojo, error ya cometido dos veces en esta sesión: este script NO debe fijar
`realm.browserFlow` — eso aplicaría "sin contraseña" a TODO el realm,
incluida la consola admin de Keycloak. Por eso el cambio se hace siempre
scoped a un client, nunca al realm completo.

Pensado para la vista ciudadana de solicitud manual del PID (cédula -> QR
para que lo escanee cualquier wallet EUDI genérica, no necesariamente
`newwallet-app` — ver RegistraduriaSolicitarUi.kt). Requiere que el
ciudadano ya tenga su usuario fantasma sincronizado en Keycloak (lo hace
`SyncCedulaToKeycloakGhostUser` al guardar la cédula en el panel admin);
"Username Form" solo verifica que el usuario exista y esté habilitado, no
lo crea.

Por qué un script y no la consola web: en algunos entornos (ej. el sandbox de
desarrollo donde se hizo esto) la consola admin de Keycloak no carga por un
hostname fijo (KC_HOSTNAME) con el que el proxy del navegador no puede
validar TLS. Esto usa la misma API REST de administración que usa la
consola — no toca el JSON de import del realm ni ningún archivo de PKI.

Por qué hace falta re-ejecutarlo: aunque Keycloak ya tiene un volumen
persistente (`keycloak-reg-data`, ver docker-compose.yaml), el binding se
pierde si el realm se reimporta desde cero (`--import-realm` con un volumen
vacío, o `docker compose down -v`). Volver a importar el realm NO lo
reaplica solo; hay que volver a correr este script.

Uso: python3 configurar-login-sin-password.py [CLIENT_ID] [URL_BASE]
  CLIENT_ID por defecto: eudiw (la wallet de referencia EUDI genérica)
  URL_BASE por defecto:  https://localhost:9543/idp
"""
import json
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request

REALM = "pid-registraduria-realm"
FLOW_ALIAS = "browser sin contraseña (Registraduría)"
ADMIN_USER = "admin"
ADMIN_PASSWORD = "password"

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE


def get_token(base_kc: str) -> str:
    data = urllib.parse.urlencode(
        {
            "grant_type": "password",
            "client_id": "admin-cli",
            "username": ADMIN_USER,
            "password": ADMIN_PASSWORD,
        }
    ).encode()
    req = urllib.request.Request(
        f"{base_kc}/realms/master/protocol/openid-connect/token", data=data, method="POST"
    )
    with urllib.request.urlopen(req, context=ctx) as resp:
        return json.load(resp)["access_token"]


def call(base_kc: str, method: str, path: str, body=None):
    token = get_token(base_kc)
    url = f"{base_kc}/admin/realms/{REALM}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, context=ctx) as resp:
            raw = resp.read()
            return resp.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        raw = e.read()
        return e.code, (json.loads(raw) if raw else raw.decode(errors="replace"))


def q(s: str) -> str:
    return urllib.parse.quote(s, safe="")


def ensure_flow(base_kc: str) -> str:
    """Crea (si hace falta) el flow sin contraseña y devuelve su id."""
    status, flows = call(base_kc, "GET", "/authentication/flows")
    if status != 200:
        sys.exit(f"No se pudo listar flows: {status} {flows}")
    existing = next((f for f in flows if f["alias"] == FLOW_ALIAS), None)
    if existing is not None:
        print(f"El flow '{FLOW_ALIAS}' ya existe, no se recrea.")
        return existing["id"]

    status, _ = call(base_kc, "POST", "/authentication/flows/browser/copy", {"newName": FLOW_ALIAS})
    if status != 201:
        sys.exit(f"No se pudo copiar el flow 'browser': {status}")
    print(f"Flow '{FLOW_ALIAS}' creado a partir de 'browser'.")

    status, execs = call(base_kc, "GET", f"/authentication/flows/{q(FLOW_ALIAS)}/executions")
    forms = next(e for e in execs if e.get("displayName") == f"{FLOW_ALIAS} forms")
    username_password = next(e for e in execs if e.get("providerId") == "auth-username-password-form")
    conditional_otp = next(e for e in execs if e.get("displayName") == f"{FLOW_ALIAS} Browser - Conditional OTP")

    status, _ = call(
        base_kc,
        "POST",
        f"/authentication/flows/{q(forms['displayName'])}/executions/execution",
        {"provider": "auth-username-form"},
    )
    if status != 201:
        sys.exit(f"No se pudo agregar 'Username Form': {status}")

    status, _ = call(base_kc, "DELETE", f"/authentication/executions/{username_password['id']}")
    if status != 204:
        sys.exit(f"No se pudo borrar 'Username Password Form': {status}")

    status, _ = call(
        base_kc,
        "PUT",
        f"/authentication/flows/{q(FLOW_ALIAS)}/executions",
        {"id": conditional_otp["id"], "requirement": "DISABLED"},
    )
    if status != 204:
        sys.exit(f"No se pudo deshabilitar OTP condicional: {status}")

    print("Subflow 'forms' reemplazado: solo 'Username Form', OTP condicional deshabilitado.")

    status, flows = call(base_kc, "GET", "/authentication/flows")
    return next(f for f in flows if f["alias"] == FLOW_ALIAS)["id"]


def bind_to_client(base_kc: str, client_id: str, flow_id: str) -> None:
    status, clients = call(base_kc, "GET", f"/clients?clientId={q(client_id)}")
    if status != 200 or not clients:
        sys.exit(f"No se encontró el cliente '{client_id}': {status} {clients}")
    client = clients[0]
    overrides = dict(client.get("authenticationFlowBindingOverrides") or {})
    overrides["browser"] = flow_id
    client["authenticationFlowBindingOverrides"] = overrides
    status, _ = call(base_kc, "PUT", f"/clients/{client['id']}", client)
    if status != 204:
        sys.exit(f"No se pudo enlazar el flow al cliente '{client_id}': {status}")


def main() -> None:
    client_id = sys.argv[1] if len(sys.argv) > 1 else "eudiw"
    base_kc = sys.argv[2] if len(sys.argv) > 2 else "https://localhost:9543/idp"

    flow_id = ensure_flow(base_kc)
    bind_to_client(base_kc, client_id, flow_id)

    print(
        f"Listo: el cliente '{client_id}' de '{REALM}' ahora usa '{FLOW_ALIAS}'. "
        "El login para ese cliente solo pide usuario (sin contraseña); el resto del realm sigue igual.",
    )


if __name__ == "__main__":
    main()
