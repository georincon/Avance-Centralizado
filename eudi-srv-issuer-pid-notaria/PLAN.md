# notaria-issuer: plan y estado (2026-10-30)

Emisor de documentos notariales sobre la pila Python del EUDI (4 repos de referencia
copiados aquí): `backend/` (eudi-srv-web-issuing-eudiw-py), `frontend/` (…-frontend-eudiw-py),
`auth-server/` (eudi-srv-issuer-oidc-py: token, PAR, DPoP, WIA vía trust-validator) y
`status-list/` (eudi-srv-statuslist-py). Lógica y diseño: `../verifiers/notaria`.

## Hecho
- 1 (metadata, 9 documentos: `scripts/generar-metadata.mjs`) y la PKI en `secretos/` (CA raíz + DS).
- Decidido (30-oct): el registro en el tl-publisher va AL FINAL; `/notaria` sigue en paralelo.

## Pendiente (en orden)
1. Metadata: 10 `credentials_supported/*.json` (SD-JWT, `vct` urn:co:notaria:*) desde `verifiers/notaria/src/documentos.js`; quitar los de ejemplo UE.
2. Claves: `scripts/generar-claves.sh` → CA raíz + DS; regenerar las claves de muestra que trae cada repo (private/, certs/).
3. Config: `backend/app/config_issuer_backend.yaml`, `auth-server/config.json` (issuer `https://DOMINIO/notaria-issuer`, trust_validator, dominio), status-list (DS de la notaría).
4. Flujos: los de los repos Python — preautorizado con código (`/preauth` y `/credentialOfferReq2`) y authorization_code con selección de país `FC` (formulario). DECIDIDO 30-oct: sin PID de la Registraduría/UIS ni OID4VP en el emisor.
5. Frontend: plantillas con el diseño de `verifiers/notaria/src/publico`.
6. docker-compose + up.sh/down.sh (red `avance-eudi`, alias propios con sufijo `-notaria-issuer`).
7. Confianza y ecosistema: registrar.py (pub-eaa, TL/PUBEAA) · ancla en `srv-tl-publisher/pki/publico/ca` + `construir-jks-anclas.sh` y reinicio del trust-validator · rutas en `srv-nw-nginx/nginx.conf` (`/notaria-issuer`, well-known, `/status-list/notaria-issuer`) · WRPRC_VCTS del `verifier-notaria` · panel-control · ECOSISTEMA.md · carpeta postman.
8. Prueba sin teléfono (como `verifiers/notaria/pruebas`) y con la wallet.

## Estado 2026-10-30 (tarde)
- Pasos 1–3 y 6 hechos: `scripts/configurar.py` → `docker-compose/config/`; `docker-compose/` levanta postgres, status-list, oidc, backend, frontend y `haproxy-notaria` (sin puertos en el host). Metadata servida bien por el router (9 documentos, endpoints públicos `/notaria-issuer/api/*`).
- Parche propio al frontend: `backend_public_url` (URL pública del backend; `backend_url` sigue siendo la interna). Parche al Dockerfile del backend: `libpq5`.
- Sin selección de país: un solo «país» `FC` (formulario) → el backend se lo salta.
- Pendiente: logo del display (el frontend sirve `/ic-logo.png`), formulario de trámites con el diseño de la notaría, prueba del flujo preautorizado, y TODO el paso 7 (confianza) al final.
- Revisión jurídica de los 9 documentos: ver la conversación; cambios de campos SIN aplicar hasta que el usuario los confirme (varias citas quedaron «NO VERIFICADO»).

## Estado 2026-10-30 (noche): frontend cuadrado
- `/notaria-issuer/` publicado en nw-nginx (+ `/.well-known/*/notaria-issuer`, `/status-list/notaria-issuer`). haproxy-notaria usa variables (`txn.destino`) para no perder la ruta al quitar el prefijo.
- Frontend: una sola hoja (`static/notaria/notaria.css`, escala 15px), sin bootstrap/tailwind en el flujo. Pantallas rehechas: elegir documento, formulario (rejilla de 2 columnas, orden del documento, etiquetas en español), revisión (lo que declaras / lo que pone la notaría) y QR+PIN.
- Parches propios a los repos: ProxyFix (frontend y backend), `backend_public_url`, `libpq5`, `USER_VERIFY_URL` y metadata del auth-server, `source=issuer` fuera del formulario, `emulated_claims`, consecutivo en Postgres.
- Probado: flujo preautorizado hasta el QR con PIN (cliente HTTP). SIN PROBAR: token + credencial con una wallet real (WIA vía trust-validator), key attestation, status list real.
- Ojo: `iss` del SD-JWT = URL del backend (`…/notaria-issuer/api`), distinto de `credential_issuer`. Decidir antes de registrar.
- Pendiente: paso 7 (registro tl-publisher, ancla trust-validator, WRPRC del verifier, panel, ECOSISTEMA.md, postman).

## Estado 2026-11-03: inicio de sesión con la wallet (PID de la Registraduría)
- Revierte la decisión del 30-oct (sin PID/OID4VP): ahora para pedir un documento hay que identificarse con el PID. `backend/app/pid_login.py` (blueprint `login`: `/login`, `/login/estado`, `/login/retorno`, `/logout`) pide el PID (SD-JWT `urn:eudi:pid:1`) por el verifier de la notaría (`/ui/presentations/v2`, config `pid_login.verifier_api`). Datos pedidos: `personal_administrative_number`, `given_name`, `family_name`, `age_equal_or_over.18` (no la fecha de nacimiento). El verifier valida firma, lista de confianza (PID), estado y nonce.
- Los campos personales de cada documento salen del PID: `issuer_config.persona_claims` (generado desde `scripts/documentos.mjs`, constante `PERSONA`). Llegan precargados y de solo lectura (chip «de tu PID») y el servidor los vuelve a poner al recibir el formulario (`pid_login.forzar`), así que no se pueden alterar.
- Rutas protegidas (before_request): `credential_offer_choice`, `auth_choice`, `/preauth`, selección de país. Sin sesión → `/login?volver=…`.
- Probado: redirección al login, creación de transacciones en el verifier real, consulta de estado con sesión, y (con identificación simulada) precarga + corrección de un valor alterado. SIN PROBAR: presentación real del PID desde la wallet; flujo authorization_code con login; `/login/retorno` (mismo teléfono).
- Pendiente de datos: domicilio y estado civil no están en el PID (se escriben). Se podría pedir `address.formatted` con claim_sets si la wallet lo soporta.

## Estado 2026-11-03 (tarde): archivo del acto por huella
- Reconocimiento, autenticación de firma y copia de escritura: selector de archivo en el formulario. El navegador calcula nombre, tamaño y SHA-256 (Web Crypto); el archivo no se envía. Límite 50 MB (`backend/app/documento_archivo.py`, `MAX_BYTES`), tipos PDF/imagen/Word. Resumen visible (nombre, tamaño, huella abreviada); «Continuar» deshabilitado hasta tener archivo.
- El servidor valida formato (64 hex), tamaño (1..50 MB) y nombre, y devuelve un error legible; no puede comprobar que la huella sea del archivo (no lo recibe).
- Corregido: `alcance` (autenticación y copia) lo pone la notaría (texto fijo), ya no se le pide a la persona; se quitó la cita de artículo no verificada.

## Estado 2026-11-03 (tarde): fechas
- Todos los campos de fecha que escribe la persona son `full-date` en la metadata (`FECHAS` en `scripts/generar-metadata.mjs`): escritura_fecha, convivencia_desde, vigente_hasta, fecha_salida, fecha_regreso, fecha_nacimiento. El formulario usa el selector nativo, con límites (hechos pasados: máx. hoy; vigencia/salida/regreso: mín. hoy; regreso ≥ salida) y `hora_nacimiento` con selector de hora.
- `backend/app/validacion.py` vuelve a comprobar formato y lógica en el servidor (hoy = fecha de Colombia, UTC-5) y normaliza a AAAA-MM-DD.
- Pendiente de fidelidad: el permiso de salida de menor debería vencer en `fecha_regreso` (hoy usa la validez fija del documento).
