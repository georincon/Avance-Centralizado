# Pruebas del ecosistema con Postman

Colección lista para importar: **`Avance-EUDI.postman_collection.json`**. Dominio de las pruebas: `https://civica-desarrollo.avance.org.co` (variable `base_url`).

## Cómo empezar (3 pasos)

1. **Importa** en Postman: `Import` → arrastra `Avance-EUDI.postman_collection.json` y `Avance.postman_environment.json`.
2. **Elige el entorno** «Avance · civica-desarrollo» (arriba a la derecha). Para las pruebas que usan claves, copia los valores de `DATOS-LOCALES.md` a las variables del entorno (las claves no se comparten y por eso vienen vacías).
3. **Ejecuta la carpeta `00 · Humo`** con el *Runner* (clic derecho en la carpeta → *Run folder*). Si sale en verde, todo el ecosistema está en pie.

## Cómo leer una prueba

Cada petición trae su explicación (pestaña *Docs*) y **comprobaciones automáticas** (pestaña *Test Results* tras enviarla): el código HTTP, los campos que debe traer y, a veces, valores concretos. Las pruebas que dependen de la anterior (por ejemplo crear una solicitud y luego consultarla) pasan los datos solas mediante variables (`solicitud_id`, `transaction_id`, `session_id`…): **ejecútalas en orden**, de arriba abajo, o usa *Run folder*.

> Las pruebas marcadas **«Paso manual»** necesitan una persona con la wallet (abrir el enlace `haip-vp://…` o escanear el QR). Sin ese paso devuelven «esperando», que también es el resultado correcto antes de que la wallet responda.

## Variables del entorno

| Variable | Para qué sirve | Se comparte |
|---|---|---|
| `base_url` | Dominio del ecosistema | sí |
| `instancia` | Entidad cuyo verifier se prueba (`banco`, `eps`, `gym`…) | sí |
| `fecha_vuelo` | Fecha para buscar vuelos (hoy + 30 días) | sí |
| `status_list_ruta` | Ruta de una lista de estado real del Wallet Provider | sí |
| `solicitud_id`, `transaction_id`, `session_id`, `dev_code`, `intended_use_id`, `x5c_chain`, `andes_token`, `kc_token` | Las rellenan las pruebas solas | — |
| `panel_token` | Token de la API del panel (`CONTROL_PANEL_TOKEN`) | **no** |
| `tl_api_key`, `tl_operator_key` | Claves del publicador de listas | **no** |
| `eps_clave`, `gym_clave` | Clave de administración de la EPS y del gimnasio | **no** |
| `andes_client_id`, `andes_client_secret` | Cliente de la wallet ante Andes SCD | **no** |
| `kc_admin_user`, `kc_admin_password` | Administrador de Keycloak | **no** |

## Las pruebas, proyecto por proyecto

### 00 · Humo: ¿está todo en pie?

Una pasada rápida por todo el ecosistema. Si esta carpeta sale en verde, los servicios están arriba y se pueden hacer las demás pruebas. Úsala con el *Runner* de Postman: se ejecuta sola y no necesita claves.

**1. banco · salud** — `GET /banco/salud`

Responde `ok` si el portal de banco está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. contratacion · salud** — `GET /contratacion/salud`

Responde `ok` si el portal de contratacion está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**3. discoteca · salud** — `GET /discoteca/salud`

Responde `ok` si el portal de discoteca está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**4. beca · salud** — `GET /beca/salud`

Responde `ok` si el portal de beca está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**5. acceso · salud** — `GET /acceso/salud`

Responde `ok` si el portal de acceso está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**6. pensiones · salud** — `GET /pensiones/salud`

Responde `ok` si el portal de pensiones está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**7. migracion · salud** — `GET /migracion/salud`

Responde `ok` si el portal de migracion está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**8. eps · salud** — `GET /eps/salud`

Responde `ok` si el portal de eps está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**9. notaria · salud** — `GET /notaria/salud`

Responde `ok` si el portal de notaria está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**10. gym · salud** — `GET /gym/salud`

Responde `ok` si el portal de gym está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok","aforo_maximo":60}
```

**11. aerolinea · salud** — `GET /aerolinea/salud`

Responde `ok` si el portal de aerolinea está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**12. cancilleria · salud** — `GET /cancilleria/salud`

Responde `ok` si el portal de cancilleria está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**13. Wallet Provider · metadatos** — `GET /wallet-provider/.well-known/oauth-protected-resource`

El servicio que certifica cada instalación de la wallet.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `resource`.

*Respuesta real de ejemplo:*
```
HTTP 200  { "resource": "https://civica-desarrollo.avance.org.co/wallet-provider", "jwks_uri": "https://civica-desarrollo.avance.org.co/wallet-provider/jwks", "resource_name": "Spartans Wallet Provider", "resource_signing_alg_values_supported": [ "ES256" ], "client_atte …
```

**14. Registraduría (PID) · metadatos** — `GET /pid-registraduria/.well-known/openid-credential-issuer`

El emisor del documento de identidad.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/pid-registraduria","authorization_servers":["https://civica-desarrollo.avance.org.co/idp-reg/realms/pid-registraduria-realm"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/pid-regis …
```

**15. UIS · metadatos** — `GET /uis-issuer/.well-known/openid-credential-issuer`

El emisor de credenciales académicas.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/uis-issuer","authorization_servers":["https://civica-desarrollo.avance.org.co/idp-uis/realms/uis-issuer-realm"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/uis-issuer/wallet/crede …
```

**16. Lista de confianza de emisores PID** — `GET /tl-publisher/TL/PID/current.json`

Lista firmada (ETSI TS 119 612) de quiénes pueden emitir identidad.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InRsK2p3dCIsIng1YyI6WyJNSUlDM3pDQ0FvYWdBd0lCQWdJQ0VBQXdDZ1lJS29aSXpqMEVBd0l3UWpFTE1Ba0dBMVVFQmhNQ1EwOHhGREFTQmdOVkJBb01DME5wZG1sallTQkZWVVJKTVIwd0d3WURWUVFEREJSRGFYWnBZMkVnVkV3Z1UybG5ibWx1WnlCRFFUQWVGdzB5TmpFd01qQXhOVFF4TlRsYUZ3MHlO …
```

**17. Validador de confianza · salud** — `GET /trust-validator/actuator/health`

El servicio que decide si una cadena de certificados es de confianza.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `status` = `"UP"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"groups":["liveness","readiness"],"status":"UP"}
```

**18. Andes SCD (firma remota) · salud** — `GET /andes-scd/health`

El prestador de firma simulado.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `ok` = `true`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"ok":true}
```

**19. Onboarding · salud** — `GET /onboarding/actuator/health`

Verificación de identidad previa a la emisión.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `status` = `"UP"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"groups":["liveness","readiness"],"status":"UP"}
```

**20. Panel de control · ficha del APK** — `GET /descargas/apk.json`

Última versión de la wallet disponible para descargar.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `archivo`; trae `sha256`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"archivo":"app-dev-debug.apk","tamano":491087620,"subido":"2026-10-27T19:57:36.211Z","sha256":"3ff220096b2311c3e5c955095585b723d6b74326adacb91b0442c9d6862b9cb7","url":"https://localhost/descargas/wallet.apk"}
```

### Panel de control

Panel de operación (`/`): sube, baja y reinicia los stacks. Su API exige el token `CONTROL_PANEL_TOKEN` (cabecera `Authorization: Bearer …`). Las rutas `/descargas/*` son públicas (las abre el teléfono).

**1. Estado de todos los proyectos** — `GET /api/projects`

Lista cada stack con su estado real (activo, parcial, detenido), contenedores y enlace. Es lo que pinta el panel.

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

**2. Sin token: debe rechazar** — `GET /api/projects`

Prueba de seguridad: sin el token, la API responde 401.

**Resultado esperado:** HTTP 401.

*Comprobaciones automáticas:* HTTP 401.

*Respuesta real de ejemplo:*
```
HTTP 401  {"error":"Token de acceso incorrecto o ausente."}
```

**3. Recursos del ecosistema** — `GET /api/recursos`

Contenedores, redes y volúmenes con puertos y enlaces (la vista `/recursos`).

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `contenedores`.

**4. Reiniciar un proyecto** — `POST /api/projects/verifier-discoteca/action`

Sube (`up`), baja (`down`) o reinicia (`restart`) un stack. Cambia el estado real del sistema: úsala a propósito.

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

**5. Ficha del APK de la wallet** — `GET /descargas/apk.json`

Archivo, tamaño, fecha, SHA-256 y enlace de la última versión compilada de la wallet.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `archivo`; trae `tamano`; trae `sha256`; trae `url`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"archivo":"app-dev-debug.apk","tamano":491087620,"subido":"2026-10-27T19:57:36.211Z","sha256":"3ff220096b2311c3e5c955095585b723d6b74326adacb91b0442c9d6862b9cb7","url":"https://localhost/descargas/wallet.apk"}
```

**6. QR de descarga de la wallet** — `GET /descargas/qr.svg`

El código QR (SVG) que apunta al APK.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una imagen SVG.

*Respuesta real de ejemplo:*
```
HTTP 200  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 31 31" shape-rendering="crispEdges"><path fill="#ffffff" d="M0 0h31v31H0z"/><path stroke="#000000" d="M1 1.5h7m2 0h2m3 0h1m1 0h1m1 0h3m1 0h7M1 2.5h1m5 0h1m8 0h2m1 0h1m1 0h1m1 0h1m5 0h1M1 3.5h1m1 0h3m1 0h1m1  …
```

**7. Descarga del APK (primeros 4 bytes)** — `GET /descargas/wallet.apk`

Pide solo los primeros bytes (`Range`) para probar la descarga sin bajar 400 MB. Debe responder 206.

**Resultado esperado:** HTTP 206.

*Comprobaciones automáticas:* HTTP 206.

*Respuesta real de ejemplo:*
```
HTTP 206  PK
```

### Confianza · tl-publisher (listas de confianza)

Registro de actores y publicación de las listas de confianza firmadas (ETSI TS 119 612): WP (wallet providers), PID, QEAA, PUBEAA y RPREG, más la LOTL (lista de listas). Cada lista sale en XML y en `tl+jwt`.

**1. Listas publicadas** — `GET /tl-publisher/tl-api/v1/lists`

Todas las listas con su secuencia, fecha y próxima actualización.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

*Respuesta real de ejemplo:*
```
HTTP 200  [{"scheme":"PID","name":"Civica PID Provider Trusted List","tsl_type":"http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric","is_lotl":false,"published":true,"sequence_number":21,"issue_date":"2026-10-27T12:27:56","next_update":"2026-10-28T12:27:56","sha2 …
```

**2. Lista de emisores PID (JWT)** — `GET /TL/PID/current.json`

La lista que consume la wallet, firmada (`tl+jwt`).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InRsK2p3dCIsIng1YyI6WyJNSUlDM3pDQ0FvYWdBd0lCQWdJQ0VBQXdDZ1lJS29aSXpqMEVBd0l3UWpFTE1Ba0dBMVVFQmhNQ1EwOHhGREFTQmdOVkJBb01DME5wZG1sallTQkZWVVJKTVIwd0d3WURWUVFEREJSRGFYWnBZMkVnVkV3Z1UybG5ibWx1WnlCRFFUQWVGdzB5TmpFd01qQXhOVFF4TlRsYUZ3MHlO …
```

**3. Lista de emisores PubEAA (XML)** — `GET /TL/PUBEAA/current.xml`

La misma lista en el formato oficial XML.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**4. Lista de listas (LOTL)** — `GET /LOTL/current.json`

Apunta a todas las demás listas.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InRsK2p3dCIsIng1YyI6WyJNSUlDM3pDQ0FvYWdBd0lCQWdJQ0VBQXdDZ1lJS29aSXpqMEVBd0l3UWpFTE1Ba0dBMVVFQmhNQ1EwOHhGREFTQmdOVkJBb01DME5wZG1sallTQkZWVVJKTVIwd0d3WURWUVFEREJSRGFYWnBZMkVnVkV3Z1UybG5ibWx1WnlCRFFUQWVGdzB5TmpFd01qQXhOVFF4TlRsYUZ3MHlO …
```

**5. Anclas de confianza de una lista** — `GET /tl-publisher/tl-api/v1/lists/PID/trust-anchors`

Los certificados raíz en los que confía esa lista.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

*Respuesta real de ejemplo:*
```
HTTP 200  {"scheme":"PID","sequence_number":21,"issue_date":"2026-10-27T12:27:56","next_update":"2026-10-28T12:27:56","xml_uri":"https://civica-desarrollo.avance.org.co/TL/PID/21.xml","xml_sha256":"355399e4d0923680b6203de1de4b4f61b0fa30f8e5374d683bfb684ae1ac034b","servi …
```

**6. Publicar sin clave: debe rechazar** — `POST /tl-publisher/tl-api/v1/publish/PID`

Publicar es una operación privilegiada: sin `X-API-Key` responde 401.

**Resultado esperado:** HTTP 401.

*Comprobaciones automáticas:* HTTP 401.

*Respuesta real de ejemplo:*
```
HTTP 401  {"detail":"X-API-Key incorrecta o ausente"}
```

**7. Publicar una lista (con clave)** — `POST /tl-publisher/tl-api/v1/publish/PID`

Vuelve a firmar y publicar la lista PID con una nueva secuencia.

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

**8. Política (CPS) de la PKI** — `GET /pki/cps`

Declaración de prácticas de la PKI del ecosistema.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

*Respuesta real de ejemplo:*
```
HTTP 200  <!doctype html> <html lang="es"><head><meta charset="utf-8"> <title>CPS — PKI del ecosistema EUDI</title> <style>body{font-family:system-ui,sans-serif;max-width:50rem;margin:3rem auto;padding:0 1rem;line-height:1.6} table{border-collapse:collapse;width:100%}td …
```

### Confianza · trust-validator

Servicio oficial de EUDI que responde «¿esta cadena de certificados es de confianza para este rol?» leyendo las listas. Estas pruebas usan un certificado real: el del verifier del banco.

**1. Salud** — `GET /trust-validator/actuator/health`

Estado del servicio.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `status` = `"UP"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"groups":["liveness","readiness"],"status":"UP"}
```

**2. 1 · Obtener la cadena de un verifier** — `GET /verifier-banco/wallet/public-keys.json`

Descarga la clave pública del verifier del banco, que trae su cadena de certificados (`x5c`). Se guarda para la prueba siguiente.

**Guarda para las siguientes pruebas:** `x5c_chain`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `keys`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"keys":[{"kty":"EC","x5t#S256":"8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk","nbf":1792676385,"use":"sig","crv":"P-256","kid":"verifier","x5c":["MIIDIjCCAsegAwIBAgICEAEwCgYIKoZIzj0EAwIwQTELMAkGA1UEBhMCQ08xFDASBgNVBAoMC0NpdmljYSBFVURJMRwwGgYDVQQDDBNDaXZpY2EgUl …
```

**3. 2 · Validar la cadena con el rol correcto** — `POST /trust-validator/trust`

La cadena del verifier, validada como *certificado de acceso de parte usuaria*: es el rol que tiene. Responde `trusted: true` y el ancla.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `trusted` = `true`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"trusted":true,"trustAnchor":"MIIC3jCCAmOgAwIBAgICEAUwCgYIKoZIzj0EAwMwQTELMAkGA1UEBhMCQ08xFDASBgNVBAoMC0NpdmljYSBFVURJMRwwGgYDVQQDDBNDaXZpY2EgRVVESSBSb290IENBMB4XDTI2MTAyMDE1NDE1MloXDTM2MTAxNzE1NDE1MlowQTELMAkGA1UEBhMCQ08xFDASBgNVBAoMC0NpdmljYSBFVURJMRwwGgYDV …
```

**4. 3 · Una cadena que no es un certificado: debe rechazar** — `POST /trust-validator/trust`

Si lo que se envía no es un certificado X.509 válido, el validador no lo puede leer y responde 400.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"description":"Request body cannot be parsed: Could not parse certificate: java.io.IOException: Empty input"}
```

**5. Cuerpo vacío: debe rechazar** — `POST /trust-validator/trust`

Sin `chain` ni `verificationContext` la petición no se puede leer.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"description":"Request body cannot be parsed: 400 BAD_REQUEST \"Failed to read HTTP message\""}
```

### Confianza · Andes SCD (firma remota)

Prestador de servicios de firma remota **simulado** (CSC v2.2) y autoridad de sellado de tiempo. La wallet firma contratos con él; el portal de contratación sella las firmas. No es un prestador cualificado: es de prueba.

**1. Salud** — `GET /andes-scd/health`

Estado del servicio.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `ok` = `true`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"ok":true}
```

**2. Información del prestador (CSC)** — `GET /andes-scd/csc/v2/info`

Qué métodos de autenticación y operaciones soporta.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `name`; trae `authType`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"specs":"2.2.0.0","name":"Andes SCD (simulador)","logo":"https://civica-desarrollo.avance.org.co/andes-scd/static/logo.svg","region":"CO","lang":"es-CO","description":"Simulador de firma remota del ecosistema Avance. Solo para pruebas.","authType":["oauth2cod …
```

**3. Metadatos OAuth** — `GET /andes-scd/.well-known/oauth-authorization-server`

Endpoints de autorización, token y PAR.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `issuer`; trae `token_endpoint`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"issuer":"https://civica-desarrollo.avance.org.co/andes-scd","authorization_endpoint":"https://civica-desarrollo.avance.org.co/andes-scd/oauth2/authorize","token_endpoint":"https://civica-desarrollo.avance.org.co/andes-scd/oauth2/token","pushed_authorization_ …
```

**4. Token de cliente (client credentials)** — `POST /andes-scd/oauth2/token`

Token del cliente de la wallet (`wallet-client`). Sirve para operaciones del servicio, pero **no identifica a un firmante**.

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Guarda para las siguientes pruebas:** `andes_token`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `access_token`.

**5. Listar credenciales de firma sin firmante: debe rechazar** — `POST /andes-scd/csc/v2/credentials/list`

Con el token de cliente no hay firmante: responde 403. Para listar las credenciales de firma de una persona hay que hacer el flujo de autorización con su identidad (lo hace la wallet).

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 403.

*Comprobaciones automáticas:* HTTP 403.

**6. Sello de tiempo (RFC 3161)** — `POST /andes-scd/tsa`

Recibe una consulta de sello (`consulta.tsq`) y devuelve el sello firmado. Se crea con: `openssl ts -query -data archivo.txt -sha256 -cert -no_nonce -out consulta.tsq`.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

### Wallet · wallet-provider

Servicio (Spartans) que certifica que cada instalación de la wallet y su clave son legítimas: emite la Wallet Instance Attestation y la Key Attestation, con una lista de estado para poder revocarlas. Valida el paquete Android de la app.

**1. Metadatos (RFC 9728)** — `GET /wallet-provider/.well-known/oauth-protected-resource`

Quién es el servicio y dónde están sus claves.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `resource`; trae `jwks_uri`.

*Respuesta real de ejemplo:*
```
HTTP 200  { "resource": "https://civica-desarrollo.avance.org.co/wallet-provider", "jwks_uri": "https://civica-desarrollo.avance.org.co/wallet-provider/jwks", "resource_name": "Spartans Wallet Provider", "resource_signing_alg_values_supported": [ "ES256" ], "client_atte …
```

**2. Claves públicas (JWKS)** — `GET /wallet-provider/jwks`

Con estas claves se comprueban las atestaciones que firma.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `keys`.

*Respuesta real de ejemplo:*
```
HTTP 200  { "keys": [ { "crv": "P-256", "kty": "EC", "x": "ot7FgAYfg1mHOVLQ3TQQ-y_E4vh-c7pKXwu0cuNtq1g", "x5c": [ "MIIB7TCCAZKgAwIBAgIUGh5efSBSP8yoZUYLQLB1HPP/tu0wCgYIKoZIzj0EAwIwSTELMAkGA1UEBhMCQ08xDzANBgNVBAoMBkF2YW5jZTEpMCcGA1UEAwwgU3BhcnRhbnMgV2FsbGV0IFByb3ZpZGVyIFJ …
```

**3. Pedir un desafío (challenge)** — `POST /wallet-provider/challenge`

Primer paso de la atestación: el servicio entrega un valor aleatorio que la wallet debe incluir en su prueba de integridad.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `challenge`.

*Respuesta real de ejemplo:*
```
HTTP 200  { "challenge": "0rgTLSsDWMEKLxdohl0IsqpRn3ZwjZ40fjkePM3fBWJhSsMsfva_GmD7HEsAxHJY6wECW-MuZI1XML4IBaPm_ci_dWyClstXt4NJLj8UGkrF9iqKwqya6gdhtWILlrBo9-gz1aTSoWc4d4aQg4CJKXniuXUAOrJYamIpSwui46s" }
```

**4. Atestación sin datos válidos: debe rechazar** — `POST /wallet-provider/wallet-instance-attestation/jwk`

Sin una prueba de integridad real del dispositivo, no emite nada. Solo una app Android genuina puede completarla.

**Resultado esperado:** HTTP 400 o 415 o 422.

*Comprobaciones automáticas:* HTTP 400 o 415 o 422.

*Respuesta real de ejemplo:*
```
HTTP 400  Failed to convert request body to class eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.WalletInstanceAttestationIssuanceRequest$Jwk
```

### Revocación · listas de estado (status lists)

Servicios que publican, firmadas, las listas de estado de las credenciales (draft-ietf-oauth-status-list): un bit por credencial, 1 = revocada. Hay una por servicio (`reg`, `uis`, `spartans`) y una propia por cada entidad simulada (`/<entidad>/estado/1`).

**1. Lista de estado de las atestaciones de wallet** — `GET {{status_list_ruta}}`

Una lista real del Wallet Provider (la atestación de cada wallet apunta a una posición de aquí). La URL exacta la trae la credencial; en la guía está cómo sacarla de la base de datos.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUJ6ekNDQVhXZ0F3SUJBZ0lVQmRoNFg5SDl6QS9ldE1WSGxYSjVGWlZpL3NNd0NnWUlLb1pJemowRUF3SXdQVEVMTUFrR0ExVUVCaE1DUTA4eER6QU5CZ05WQkFvTUJrRjJZVzVqWlRFZE1Cc0dBMVVFQXd3VVUzQmhjblJoYm5NZ1UzUmhkSFZ6SUV4cGMzUXdI …
```

**2. Lista de estado de la EPS** — `GET /eps/estado/1`

Lista propia de la EPS (carnés y resúmenes de historia).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUNBRENDQWFhZ0F3SUJBZ0lVQVJ3UDh2NXp5azZKMHpBTmNyM0pvdDRXbE1Vd0NnWUlLb1pJemowRUF3SXdTekVMTUFrR0ExVUVCaE1DUTA4eEdUQVhCZ05WQkFvTUVFVlFVeUJUWVd4MVpDQkJkbUZ1WTJVeElUQWZCZ05WQkFNTUdFVlFVeUJUWVd4MVpDQkJk …
```

**3. Lista de estado de la notaría** — `GET /notaria/estado/1`

Lista propia de la notaría.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUIvVENDQWFLZ0F3SUJBZ0lVWFBqWG5pUUtXTGh6elcyajZPdlFNRVArU053d0NnWUlLb1pJemowRUF3SXdTVEVMTUFrR0ExVUVCaE1DUTA4eEdEQVdCZ05WQkFvTUQwNXZkR0Z5dzYxaElFRjJZVzVqWlRFZ01CNEdBMVVFQXd3WFRtOTBZWExEcldFZ1FYWmhi …
```

**4. Lista de estado del gimnasio** — `GET /gym/estado/1`

Lista propia del gimnasio (tarjetas de socio).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUI1ekNDQVk2Z0F3SUJBZ0lVS1hUUmZQblNaQ0Jkd05wVGJHZ0RNM3RhaTQ0d0NnWUlLb1pJemowRUF3SXdQekVMTUFrR0ExVUVCaE1DUTA4eEV6QVJCZ05WQkFvTUNrTjFiV0p5WlNCSGVXMHhHekFaQmdOVkJBTU1Fa04xYldKeVpTQkhlVzBnVW05dmRDQkRR …
```

**5. Lista de estado de la aerolínea** — `GET /aerolinea/estado/1`

Lista propia de la aerolínea (tiquetes).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUI3RENDQVpLZ0F3SUJBZ0lVUkpCTzJyZkFJOWt0TzUrZS9OT0tVcWdweXVJd0NnWUlLb1pJemowRUF3SXdRVEVMTUFrR0ExVUVCaE1DUTA4eEZEQVNCZ05WQkFvTUMxRERvWEpoYlc4Z1FXbHlNUnd3R2dZRFZRUUREQk5RdzZGeVlXMXZJRUZwY2lCU2IyOTBJ …
```

**6. Lista de estado de la cancillería** — `GET /cancilleria/estado/1`

Lista propia de la cancillería (pasaportes).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUNlVENDQWlDZ0F3SUJBZ0lVWFY1Y05qcXpmWUNjbWpmeC9tVnlmUHZqcnRvd0NnWUlLb1pJemowRUF3SXdnWWN4Q3pBSkJnTlZCQVlUQWtOUE1UY3dOUVlEVlFRS0RDNU5hVzVwYzNSbGNtbHZJR1JsSUZKbGJHRmphVzl1WlhNZ1JYaDBaWEpwYjNKbGN5Qmta …
```

### Wallet · onboarding (verificación de identidad)

Identity proofing previo a pedir la PID: crea una sesión, verifica el contacto con un código (OTP), pide consentimiento, captura el documento y la selfie, y decide. Base de datos Postgres en el puerto 5436. En desarrollo el código OTP viene en la respuesta (`devCode`).

**1. Salud del backend** — `GET /onboarding/actuator/health`

Estado del servicio.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `status` = `"UP"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"groups":["liveness","readiness"],"status":"UP"}
```

**2. Salud de biometría y OCR** — `GET /biometrics/health`

Servicio que compara el rostro y lee el documento.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

*Respuesta real de ejemplo:*
```
HTTP 200  {"status":"UP"}
```

**3. 1 · Crear una sesión** — `POST /onboarding/api/v1/onboarding/sessions`

Abre una sesión de onboarding (dura 2 horas). Devuelve su `id`.

**Guarda para las siguientes pruebas:** `session_id`

**Resultado esperado:** HTTP 201.

*Comprobaciones automáticas:* HTTP 201; trae `id`; trae `status`.

*Respuesta real de ejemplo:*
```
HTTP 201  {"id":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"CREATED","expiresAt":"2026-10-27T21:33:13.081333696Z"}
```

**4. 2 · Ver el estado de la sesión** — `GET /onboarding/api/v1/onboarding/sessions/{{session_id}}`

Estado, decisión y datos capturados hasta ahora.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `status`; trae `contactVerified`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"CREATED","decision":null,"contactVerified":false,"consentAccepted":false,"failureReason":null,"data":{"documentNumber":null,"firstName":null,"middleName":null,"lastName":null,"secondLastName":null,"birthDa …
```

**5. 3 · Enviar el código al contacto** — `POST /onboarding/api/v1/onboarding/sessions/{{session_id}}/contact`

Envía un código al correo o teléfono. En desarrollo no sale a ningún lado: llega en `devCode`.

**Guarda para las siguientes pruebas:** `dev_code`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `status`; trae `devCode`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"sessionId":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"CONTACT_PENDING","channel":"EMAIL","destinationMasked":"**************.com","provider":"logging","devCode":"733338"}
```

**6. 4 · Verificar el código** — `POST /onboarding/api/v1/onboarding/sessions/{{session_id}}/contact/verify`

Confirma que la persona controla ese contacto.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `contactVerified` = `true`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"CONTACT_VERIFIED","decision":null,"contactVerified":true,"consentAccepted":false,"failureReason":null,"data":{"documentNumber":null,"firstName":null,"middleName":null,"lastName":null,"secondLastName":null, …
```

**7. 5 · Aceptar el consentimiento** — `POST /onboarding/api/v1/onboarding/sessions/{{session_id}}/consent`

La persona acepta el tratamiento de sus datos.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `consentAccepted` = `true`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"DOCUMENTS_PENDING","decision":null,"contactVerified":true,"consentAccepted":true,"failureReason":null,"data":{"documentNumber":null,"firstName":null,"middleName":null,"lastName":null,"secondLastName":null, …
```

**8. 6 · Auditoría de la sesión** — `GET /onboarding/api/v1/onboarding/sessions/{{session_id}}/audit`

Cada paso queda registrado con hora.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

*Respuesta real de ejemplo:*
```
HTTP 200  [{"type":"SESSION_CREATED","message":"Sesion de onboarding creada","createdAt":"2026-10-27T20:33:13.083213Z"},{"type":"OTP_SENT","message":"Codigo enviado por EMAIL","createdAt":"2026-10-27T20:33:13.103431Z"},{"type":"CONTACT_VERIFIED","message":"Contacto veri …
```

### Emisor · Registraduría (PID)

Emisor oficial del documento de identidad digital (PID, `urn:eudi:pid:1` en SD-JWT y `eu.europa.ec.eudi.pid.1` en mdoc). Los ciudadanos de prueba están en su base y se autentica con Keycloak. Puerto de su base de datos: 5441.

**1. Metadatos OpenID4VCI** — `GET /pid-registraduria/.well-known/openid-credential-issuer`

Credenciales que ofrece, formatos y datos de cada una. Pide `Accept: application/json` (sin él puede responder firmado).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/pid-registraduria","authorization_servers":["https://civica-desarrollo.avance.org.co/idp-reg/realms/pid-registraduria-realm"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/pid-regis …
```

**2. Metadatos JWT-VC** — `GET /pid-registraduria/.well-known/jwt-vc-issuer`

Cómo verificar las credenciales SD-JWT que firma.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `issuer`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"issuer":"https://civica-desarrollo.avance.org.co/pid-registraduria","jwks":{"keys":[{"kty":"EC","x5t#S256":"ML-W8M9HFduoWDKBicQQBnZZPtf4iNWteMXTn4cl2ek","nbf":1792157331,"use":"sig","crv":"P-256","kid":"issuance","x5c":["MIIB+DCCAZ6gAwIBAgIUR+uXxTPJfcsN/VluU …
```

**3. Claves públicas del emisor** — `GET /pid-registraduria/public_keys.jwks`

JWKS con la clave de firma.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `keys`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"keys":[{"kty":"EC","x5t#S256":"ML-W8M9HFduoWDKBicQQBnZZPtf4iNWteMXTn4cl2ek","nbf":1792157331,"use":"sig","crv":"P-256","kid":"issuance","x5c":["MIIB+DCCAZ6gAwIBAgIUR+uXxTPJfcsN/VluUwnTVrW25UgwCgYIKoZIzj0EAwIwLjEsMCoGA1UEAwwjUkVHSVNUUkFEVVJJQSBJU1NVQU5DRSBJTl …
```

**4. Metadatos del tipo de la PID** — `GET /pid-registraduria/type-metadata/urn:eudi:pid:1`

Descripción y datos del tipo `urn:eudi:pid:1`.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

*Respuesta real de ejemplo:*
```
HTTP 200  {"vct":"urn:eudi:pid:1","name":"Type Metadata for Person Identification Data","display":[{"locale":"es-CO","name":"Cédula de Ciudadanía (SD-JWT VC Compact)","description":"Gobierno de Colombia","rendering":{"simple":{"logo":{"uri":"https://civica-desarrollo.av …
```

**5. Pantalla ciudadana: solicitar la PID** — `GET /pid-registraduria/registraduria/solicitar`

La página donde el ciudadano pide su PID digital (sin login; se identifica con el onboarding).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**6. Login del panel de administración** — `GET /pid-registraduria/registraduria/admin/login`

Formulario de acceso del administrador de la Registraduría (la clave está en `DATOS-LOCALES.md`).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**7. Crear una oferta sin indicar qué credencial: debe rechazar** — `POST /pid-registraduria/issuer/credentialsOffer/create`

La API para crear ofertas exige saber qué credencial se ofrece.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400; `error` = `"MissingCredentialConfigurationIds"`.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"MissingCredentialConfigurationIds"}
```

**8. Token de administrador de Keycloak** — `POST /idp-reg/realms/master/protocol/openid-connect/token`

Pide un token para operar el Keycloak de la Registraduría (sus claves están en `DATOS-LOCALES.md`).

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Guarda para las siguientes pruebas:** `kc_token`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `access_token`.

**9. Configuración OIDC del realm** — `GET /idp-reg/realms/pid-registraduria-realm/.well-known/openid-configuration`

Endpoints de autorización y token del realm donde viven los ciudadanos.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `issuer`; trae `token_endpoint`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"challenge_endpoint":"https://civica-desarrollo.avance.org.co/idp-reg/realms/pid-registraduria-realm/challenge","client_attestation_signing_alg_values_supported":["ES256","ES384","ES512"],"client_attestation_pop_signing_alg_values_supported":["ES256","ES384", …
```

### Emisor · UIS (credenciales académicas)

Universidad Industrial de Santander: emite credencial académica (`urn:eudi:academic:credential:1`), de aprendizaje (`urn:eu.europa.ec.eudi:learning:credential:1`) y **carnet estudiantil** (`urn:co:uis:carnet-estudiantil:1`). Sus datos académicos viven en Postgres (puerto 5433) y su registro de credenciales en el 5434.

**1. Metadatos OpenID4VCI** — `GET /uis-issuer/.well-known/openid-credential-issuer`

Credenciales que ofrece la UIS: académica, aprendizaje, carnet, PID y mDL. Pide `Accept: application/json`.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/uis-issuer","authorization_servers":["https://civica-desarrollo.avance.org.co/idp-uis/realms/uis-issuer-realm"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/uis-issuer/wallet/crede …
```

**2. Metadatos JWT-VC** — `GET /uis-issuer/.well-known/jwt-vc-issuer`

Cómo verificar las credenciales que firma.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `issuer`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"issuer":"https://civica-desarrollo.avance.org.co/uis-issuer","jwks":{"keys":[{"kty":"EC","x5t#S256":"W4hvUaBZfn1-gFgOEq3blFpwptmzeA-zGXko3v1hngY","nbf":1791989487,"use":"sig","crv":"P-256","kid":"pid-issuance","x5c":["MIICITCCAcigAwIBAgIUCx+DD8wYVgvuilzBildV …
```

**3. Claves públicas del emisor** — `GET /uis-issuer/public_keys.jwks`

JWKS con la clave de firma.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `keys`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"keys":[{"kty":"EC","x5t#S256":"W4hvUaBZfn1-gFgOEq3blFpwptmzeA-zGXko3v1hngY","nbf":1791989487,"use":"sig","crv":"P-256","kid":"pid-issuance","x5c":["MIICITCCAcigAwIBAgIUCx+DD8wYVgvuilzBildVI0i3BlYwCgYIKoZIzj0EAwIwXDELMAkGA1UEBhMCQ08xLDAqBgNVBAoMI1VuaXZlcnNpZG …
```

**4. Metadatos del tipo «aprendizaje»** — `GET /uis-issuer/type-metadata/urn:eu.europa.ec.eudi:learning:credential:1`

Descripción del tipo de credencial de aprendizaje.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

*Respuesta real de ejemplo:*
```
HTTP 200  {"vct":"urn:eu.europa.ec.eudi:learning:credential:1","name":"Micro-Credential Attestation SD-JWT VC Type Metadata","display":[{"locale":"en","name":"Learning Credential","description":"Learning Credential"}],"claims":[{"path":["jti"],"display":[{"locale":"en", …
```

**5. Metadatos del tipo académico (no existe)** — `GET /uis-issuer/type-metadata/urn:eudi:academic:credential:1`

Este tipo se anuncia en los metadatos pero no publica su descripción: 404. Se deja como caso conocido.

**Resultado esperado:** HTTP 404.

*Comprobaciones automáticas:* HTTP 404.

*Respuesta real de ejemplo:*
```
HTTP 404  
```

**6. Portal del emisor (login del estudiante)** — `GET /uis-issuer/issuer/login`

Donde el estudiante entra con su usuario y clave para generar el QR de su carnet. Los estudiantes de prueba están en `DATOS-LOCALES.md`.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**7. Configuración OIDC del realm de la UIS** — `GET /idp-uis/realms/uis-issuer-realm/.well-known/openid-configuration`

Endpoints de autorización y token del Keycloak de la UIS. Si ese Keycloak está apagado (el panel apaga uno de los dos con «Activar consola», porque comparten sesión), responde 503: es un estado válido, no un fallo.

**Resultado esperado:** HTTP 200 o 503.

*Comprobaciones automáticas:* HTTP 200 o 503.

*Respuesta real de ejemplo:*
```
HTTP 503  <html><body><h1>503 Service Unavailable</h1> No server is available to handle this request. </body></html>
```

### Verificación · verifier OpenID4VP (una instancia por entidad)

Cada entidad tiene su propia instancia del verifier oficial de EUDI (`/verifier-<entidad>`). Estas pruebas usan la del banco (`{{instancia}}` = `banco`; cámbiala por `eps`, `gym`, `notaria`…). Es la API que usan los portales para pedir datos a la wallet con OpenID4VP + DCQL.

**1. Usos previstos (intended uses)** — `GET /verifier-{{instancia}}/ui/intended-uses`

Para qué pide datos esta instancia (lo que la wallet muestra al usuario) y su certificado de registro.

**Guarda para las siguientes pruebas:** `intended_use_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `intended_uses`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"intended_uses":[{"intended_use_id":"1","description":"Identificarte para entrar a tu banca en l��nea o abrir una cuenta","registration_certificate":"eyJ0eXAiOiJyYy13cnArand0IiwiYWxnIjoiRVMyNTYiLCJ4NWMiOlsiTUlJRElqQ0NBc2VnQXdJQkFnSUNFQUV3Q2dZSUtvWkl6ajBFQXdJd …
```

**2. Claves públicas del verifier** — `GET /verifier-{{instancia}}/wallet/public-keys.json`

Su certificado de acceso (cadena x5c).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `keys`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"keys":[{"kty":"EC","x5t#S256":"8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk","nbf":1792676385,"use":"sig","crv":"P-256","kid":"verifier","x5c":["MIIDIjCCAsegAwIBAgICEAEwCgYIKoZIzj0EAwIwQTELMAkGA1UEBhMCQ08xFDASBgNVBAoMC0NpdmljYSBFVURJMRwwGgYDVQQDDBNDaXZpY2EgUl …
```

**3. Crear una presentación (DCQL)** — `POST /verifier-{{instancia}}/ui/presentations/v2`

Pide el apellido y el nombre de la PID. Devuelve el enlace `haip-vp://…` que la wallet abre.

**Guarda para las siguientes pruebas:** `transaction_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `transaction_id`; trae `authorization_request_uri`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"transaction_id":"-DRfj2ZiVgEeGq5FjAt4n0V9uRqaR6mLfJ6Gla56PQdtAPV6emdXfOS5qRcQnMashKG7Kl3vFyYxizCf6AR6rA","client_id":"x509_hash:8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk","request_uri":"https://civica-desarrollo.avance.org.co/verifier-banco/wallet/request. …
```

**4. Resultado antes de que la wallet responda** — `GET /verifier-{{instancia}}/ui/presentations/{{transaction_id}}`

Mientras la wallet no presente nada, responde 400 `PresentationNotSubmitted`.

**Paso manual (lo hace una persona):** abre el enlace de la prueba anterior con la wallet y acepta; entonces esta misma petición devuelve el `vp_token` con los datos.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400; `error` = `"PresentationNotSubmitted"`.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"PresentationNotSubmitted"}
```

**5. Eventos de la transacción** — `GET /verifier-{{instancia}}/ui/presentations/{{transaction_id}}/events`

Cada paso de la transacción (inicializada, solicitud leída por la wallet, respuesta, fallo…). Sirve para depurar.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `events`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"transaction_id":"-DRfj2ZiVgEeGq5FjAt4n0V9uRqaR6mLfJ6Gla56PQdtAPV6emdXfOS5qRcQnMashKG7Kl3vFyYxizCf6AR6rA","last_updated":1793133193295,"events":[{"timestamp":1793133193287,"event":"Transaction initialized","actor":"Verifier","response":{"transaction_id":"-DRf …
```

**6. Crear una presentación sin uso previsto: debe rechazar** — `POST /verifier-{{instancia}}/ui/presentations/v2`

Sin `intended_use_id` el verifier no puede adjuntar el certificado de registro.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400; `error` = `"MissingRegistrationCertificate"`.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"MissingRegistrationCertificate"}
```

### Verificación · Banco Cívica

Banco sin contraseñas: se entra y se abre la cuenta presentando el PID desde la wallet (nombre y documento). Solo verifica; no emite.

**1. Salud del servicio** — `GET /banco/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /banco/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Crear solicitud de verificación** — `POST /banco/api/solicitud`

Pide a la wallet los datos para **entrar** a la banca. Devuelve `id`, el enlace `haip-vp://…` y el QR (data URL).

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"nMDU29ZgUkvxqQICjhqgYQ","enlace":"haip-vp://?client_id=x509_hash%3A8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-banco%2Fwallet%2Frequest.jwt%2FAuVgR85Qb3Q-d6a6ZhvSWGSqUOellKjtE0y_OLnzR8 …
```

**4. Consultar el resultado de la solicitud** — `GET /banco/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**5. Crear solicitud de apertura de cuenta** — `POST /banco/api/solicitud`

Pide los datos para **abrir una cuenta** (más datos que el ingreso).

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"MGRd2m4c02FMZAwPnuQKrg","enlace":"haip-vp://?client_id=x509_hash%3A8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-banco%2Fwallet%2Frequest.jwt%2FTPSNj-T5tF_pvMjnOf9UIp9V_DFCMoLI45G1HcFUVv …
```

**6. Tipo no válido: debe rechazar** — `POST /banco/api/solicitud`

Solo existen `ingreso` y `registro`.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"tipo: ingreso o registro"}
```

**7. ¿Quién soy? (sesión)** — `GET /banco/api/yo`

Datos de la persona ya identificada en esta sesión del navegador; sin identificarse, responde 401.

**Resultado esperado:** HTTP 200 o 401.

*Comprobaciones automáticas:* HTTP 200 o 401.

*Respuesta real de ejemplo:*
```
HTTP 401  {"error":"sin sesión"}
```

**8. Cerrar sesión** — `POST /banco/api/salir`

Borra la sesión del navegador.

**Resultado esperado:** HTTP 200 o 204.

*Comprobaciones automáticas:* HTTP 200 o 204.

*Respuesta real de ejemplo:*
```
HTTP 200  {"ok":true}
```

### Verificación · Contratación (empleador)

Portal de empleo: verifica el **nivel de formación** con la credencial académica de la UIS, la identidad con el PID (opcional), genera el contrato en PDF y lo hace firmar desde la wallet (Firmar → Escanear), con sello de tiempo de Andes SCD.

**1. Salud del servicio** — `GET /contratacion/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /contratacion/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Niveles de formación** — `GET /contratacion/api/niveles`

Los niveles que se pueden verificar (doctorado, maestría, … bachiller).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `niveles`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"niveles":[{"clave":"DOCTORADO","nombre":"Doctorado","disponible":true},{"clave":"MAESTRIA","nombre":"Maestría","disponible":true},{"clave":"ESPECIALIZACION","nombre":"Especialización","disponible":true},{"clave":"PREGRADO","nombre":"Pregrado","disponible":tr …
```

**4. Crear solicitud de verificación** — `POST /contratacion/api/solicitud`

Pide la credencial académica de nivel pregrado; la wallet solo ofrece las credenciales que cumplen el filtro.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"Uc0i_EOBNYJgy3G1C_OKow","enlace":"haip-vp://?client_id=x509_hash%3Ak_nlVVaM834iPD0fJbvNblenJl-guLTvdIelrtyg35c&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-contratacion%2Fwallet%2Frequest.jwt%2FjLUsM_5ENnD7TgtO18zsFFsvBuGgcdcAIKR …
```

**5. Consultar el resultado de la solicitud** — `GET /contratacion/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**6. Nivel no disponible: debe rechazar** — `POST /contratacion/api/solicitud`

Un nivel que no existe se rechaza.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"nivel no disponible"}
```

**7. Generar el contrato sin verificar antes: debe rechazar** — `POST /contratacion/api/contrato`

No se puede generar el contrato si la formación y la identidad no están verificadas.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"Primero hay que verificar la formación y confirmar la identidad."}
```

**8. Estado de la firma (id desconocido)** — `GET /contratacion/api/firma/inexistente`

Consulta el estado de una firma de contrato; con un id que no existe, 404.

**Resultado esperado:** HTTP 404.

*Comprobaciones automáticas:* HTTP 404.

*Respuesta real de ejemplo:*
```
HTTP 404  {"estado":"rechazada","motivo":"Solicitud desconocida."}
```

### Verificación · Discoteca Neón (mayoría de edad)

Control de edad en la puerta: pide a la wallet solo «mayor de 18» (sin nombre ni documento) y **no guarda nada**, solo contadores.

**1. Salud del servicio** — `GET /discoteca/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /discoteca/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Crear solicitud de verificación** — `POST /discoteca/api/solicitud`

Pide únicamente la prueba de mayoría de edad del PID.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"18EnpzNaybqOnvYTL-dWFg","enlace":"haip-vp://?client_id=x509_hash%3AOPk-1ALYxHoPxGY56MA9Jjniy-UcBJAnXl5HsFOFMT4&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-discoteca%2Fwallet%2Frequest.jwt%2FjrClRovwsYYNF7FnODJHCfHxxTWPZ3S-kNavQm …
```

**4. Consultar el resultado de la solicitud** — `GET /discoteca/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**5. Contadores de la noche** — `GET /discoteca/api/noche`

Cuántas entradas y rechazos van, sin ningún dato personal.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `entradas`; trae `rechazos`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"entradas":0,"rechazos":0,"desde":"2026-10-27T19:23:09.125Z"}
```

### Verificación · Instituto Meridiano (inscripción y becas)

Pide en una sola presentación el PID y la credencial académica, y evalúa si la persona cumple los requisitos de cada convocatoria.

**1. Salud del servicio** — `GET /beca/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /beca/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Convocatorias** — `GET /beca/api/convocatorias`

Inscripciones y becas disponibles con sus requisitos (nivel, estado, promedio mínimo…).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `convocatorias`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"convocatorias":[{"clave":"inscripcion-maestria","tipo":"Inscripción","nombre":"Inscripción a maestría","descripcion":"Para personas graduadas de pregrado que quieren inscribirse a una maestría.","nivel":"PREGRADO","estado":"GRADUADO","promedio_min":3,"requis …
```

**4. Crear solicitud de verificación** — `POST /beca/api/solicitud`

Pide el PID y la credencial académica para pre-evaluar la inscripción a maestría.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"DCS9d8lY6TmDmcY6T8alQQ","enlace":"haip-vp://?client_id=x509_hash%3ASq5-sMQtrgBxqvM7xZ9vLJaHwftBsBXVYwgf6hBjAeo&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-beca%2Fwallet%2Frequest.jwt%2FheobB77C6Z1vblFjcy7Sv2_R4xt4KjDyKx2-YGan_hN …
```

**5. Consultar el resultado de la solicitud** — `GET /beca/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

### Verificación · Edificio Cumbre (control de acceso)

Pide la foto, el nombre y el documento del PID para que un guardia decida el ingreso, con estilo de centro de control.

**1. Salud del servicio** — `GET /acceso/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /acceso/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Crear solicitud de verificación** — `POST /acceso/api/solicitud`

Pide foto, nombre y documento del PID.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"jHKzpqDEVEXeINfw9vvSpQ","enlace":"haip-vp://?client_id=x509_hash%3ACxc-N5PPyhXGidwJwjh-WW4ZUq5ZR75bn1Fu364HxbQ&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-acceso%2Fwallet%2Frequest.jwt%2FoM8dyQzs73-OlW25yoEUiAzDmQsy6TCyoWu9y9kVv …
```

**4. Consultar el resultado de la solicitud** — `GET /acceso/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**5. Decisión del guardia sobre una solicitud inexistente** — `POST /acceso/api/decision`

El guardia permite o niega; con un id que no existe se rechaza.

**Resultado esperado:** HTTP 400 o 404 o 409.

*Comprobaciones automáticas:* HTTP 400 o 404 o 409.

*Respuesta real de ejemplo:*
```
HTTP 409  {"error":"No hay un ingreso pendiente de decidir."}
```

**6. Registro de decisiones** — `GET /acceso/api/registro`

Quién entró y quién no (datos mínimos).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `registro`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"registro":[]}
```

### Verificación · Fondo de Pensiones Horizonte (prueba de supervivencia)

La persona prueba que está viva presentando el PID y su **fe de vida notarial**; el fondo cruza las dos.

**1. Salud del servicio** — `GET /pensiones/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /pensiones/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Crear solicitud de verificación** — `POST /pensiones/api/solicitud`

Pide el PID y la fe de vida de la notaría.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"4ne0DK-vaSey0Ii8naXcSg","enlace":"haip-vp://?client_id=x509_hash%3AA_85U8lycVvw4JQa57jsqPuyLiTkVzuRALLdhqr12s8&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-pensiones%2Fwallet%2Frequest.jwt%2FZHkON7iFzdW3nnSP5s-pyXuLuKceSyV59-98zI …
```

**4. Consultar el resultado de la solicitud** — `GET /pensiones/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**5. Personas acreditadas** — `GET /pensiones/api/acreditados`

Quién ya probó supervivencia.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `acreditados`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"acreditados":[]}
```

### Verificación · Migración Cívica (control migratorio)

Pide el PID y el pasaporte digital (Cancillería), cruza las comprobaciones y el funcionario decide la entrada o la salida.

**1. Salud del servicio** — `GET /migracion/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /migracion/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Movimiento no indicado: debe rechazar** — `POST /migracion/api/solicitud`

Hay que decir si es `entrada` o `salida`.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400; `error` = `"movimiento: entrada o salida"`.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"movimiento: entrada o salida"}
```

**4. Crear solicitud de verificación** — `POST /migracion/api/solicitud`

Pide PID + pasaporte para una **entrada** al país.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"hNgDCc6BARbIN9_hmvZRZA","enlace":"haip-vp://?client_id=x509_hash%3As8ibb9E4qgSCoDgSr_3uqlmg5-vWCVxu3Uo9YdVDtYc&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-migracion%2Fwallet%2Frequest.jwt%2FLgw4o77tlSkjvUVRl1LnKzyyU9TU2z5qATqifN …
```

**5. Consultar el resultado de la solicitud** — `GET /migracion/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**6. Decisión sobre una solicitud inexistente** — `POST /migracion/api/decision`

El funcionario autoriza o niega; con un id que no existe se rechaza.

**Resultado esperado:** HTTP 400 o 404 o 409.

*Comprobaciones automáticas:* HTTP 400 o 404 o 409.

*Respuesta real de ejemplo:*
```
HTTP 409  {"error":"No hay un control pendiente de decidir."}
```

**7. Registro de movimientos** — `GET /migracion/api/registro`

Entradas y salidas decididas.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `registro`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"registro":[]}
```

### Emisor+Verifier · EPS Salud Cívica (carné e historia clínica)

Emite el **carné de afiliado** y el **resumen de historia clínica** (QEAA, SD-JWT), cada dato divulgable por separado; verifica a farmacias y médicos solo lo necesario. Tiene portal de afiliados, de médicos y de administración (`ADMIN_CLAVE`).

**1. Salud del servicio** — `GET /eps/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /eps/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Crear solicitud de verificación** — `POST /eps/api/solicitud`

El afiliado se identifica con su PID para entrar a su portal.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"mZ3PQxeBmZId88-8M7ZR7g","enlace":"haip-vp://?client_id=x509_hash%3ADFIsB_v7uJcR3Vrc1byBwz76ehmYG2IQIfivc4tD_Hg&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-eps%2Fwallet%2Frequest.jwt%2FcYzbuN1Fhc35sXtyWjMAfY1YvF7HUUZib0BYCd0Msgqv …
```

**4. Consultar el resultado de la solicitud** — `GET /eps/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**5. Propósito no válido: debe rechazar** — `POST /eps/api/solicitud`

Los propósitos válidos son identificacion, medico, carne, historia o convivencia.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"proposito: identificacion, medico, carne, historia o convivencia"}
```

**6. Administración · entrar** — `POST /eps/api/admin/entrar`

Entra a la administración con la clave del administrador.

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200 o 204.

*Comprobaciones automáticas:* HTTP 200 o 204.

**7. Administración · médicos registrados** — `GET /eps/api/admin/medicos`

Los médicos autorizados a consultar historias (requiere haber entrado).

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200 o 401.

*Comprobaciones automáticas:* HTTP 200 o 401.

**8. Administración · salir** — `POST /eps/api/admin/salir`

Cierra la sesión de administración.

**Resultado esperado:** HTTP 200 o 204.

*Comprobaciones automáticas:* HTTP 200 o 204.

*Respuesta real de ejemplo:*
```
HTTP 200  {"ok":true}
```

**9. Metadatos del emisor (OpenID4VCI)** — `GET /.well-known/openid-credential-issuer/eps`

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.eps.carne_afiliado`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/eps","authorization_servers":["https://civica-desarrollo.avance.org.co/eps"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/eps/credencial","nonce_endpoint":"https://civica-desarroll …
```

**10. Nonce del emisor** — `POST /eps/nonce`

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `c_nonce`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"c_nonce":"2raaPMtVmukuugrvdXRDQYDTi4TQAJu_"}
```

**11. Lista de estado de las credenciales (revocación)** — `GET /eps/estado/1`

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUNBRENDQWFhZ0F3SUJBZ0lVQVJ3UDh2NXp5azZKMHpBTmNyM0pvdDRXbE1Vd0NnWUlLb1pJemowRUF3SXdTekVMTUFrR0ExVUVCaE1DUTA4eEdUQVhCZ05WQkFvTUVFVlFVeUJUWVd4MVpDQkJkbUZ1WTJVeElUQWZCZ05WQkFNTUdFVlFVeUJUWVd4MVpDQkJk …
```

### Emisor+Verifier · Notaría Avance (9 documentos notariales)

Verifica el PID y emite nueve documentos (PuB-EAA): declaración extraproceso, poder especial, reconocimiento de documento, **fe de vida**, autenticación de firma, copia de escritura, registro civil, convivencia y permiso de salida de menor.

**1. Salud del servicio** — `GET /notaria/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /notaria/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Crear solicitud de verificación** — `POST /notaria/api/solicitud`

La persona se identifica con su PID para hacer un trámite.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"61CXJ0sFS1r59gjjRjyMvQ","enlace":"haip-vp://?client_id=x509_hash%3ArD4Q4wbJQjBneS3WFYY9wWWAh9z5WK_OzWr0GPq1sfA&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-notaria%2Fwallet%2Frequest.jwt%2FYSn8lHrOhFxr5BF-TMPlcqLoOlPqG2KgBEp-6VU9 …
```

**4. Consultar el resultado de la solicitud** — `GET /notaria/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**5. Propósito no válido: debe rechazar** — `POST /notaria/api/solicitud`

Hay que decir `identificacion` o `verificacion` con un tipo de documento.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"proposito: identificacion, o verificacion con un tipo de documento"}
```

**6. Trámites (sin identificarse)** — `GET /notaria/api/tramites`

Los trámites de la persona; antes de identificarse con la wallet responde 401.

**Resultado esperado:** HTTP 401.

*Comprobaciones automáticas:* HTTP 401; `error` = `"Primero identifícate con tu wallet."`.

*Respuesta real de ejemplo:*
```
HTTP 401  {"error":"Primero identifícate con tu wallet."}
```

**7. Metadatos del emisor (OpenID4VCI)** — `GET /.well-known/openid-credential-issuer/notaria`

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.notaria.fe_de_vida`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/notaria","authorization_servers":["https://civica-desarrollo.avance.org.co/notaria"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/notaria/credencial","nonce_endpoint":"https://civi …
```

**8. Nonce del emisor** — `POST /notaria/nonce`

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `c_nonce`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"c_nonce":"2No2LF-T49dRvjcL8tFW0Au6AO33AtUv"}
```

**9. Lista de estado de las credenciales (revocación)** — `GET /notaria/estado/1`

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUIvVENDQWFLZ0F3SUJBZ0lVWFBqWG5pUUtXTGh6elcyajZPdlFNRVArU053d0NnWUlLb1pJemowRUF3SXdTVEVMTUFrR0ExVUVCaE1DUTA4eEdEQVdCZ05WQkFvTUQwNXZkR0Z5dzYxaElFRjJZVzVqWlRFZ01CNEdBMVVFQXd3WFRtOTBZWExEcldFZ1FYWmhi …
```

### Emisor+Verifier · Cumbre Gym (tarjeta de socio, ingreso y egreso)

Emite la **tarjeta de socio** (QEAA, con foto) y controla el ingreso y egreso de sus sedes (anti-passback y aforo). Las pantallas de la puerta piden la clave de puerta (= `ADMIN_CLAVE`).

**1. Salud del servicio** — `GET /gym/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok","aforo_maximo":60}
```

**2. Portal (página principal)** — `GET /gym/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Crear solicitud de verificación** — `POST /gym/api/solicitud`

La persona se identifica con su PID para afiliarse o ver su portal.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"m8o8U3NyYeRAWBinsBkiFA","enlace":"haip-vp://?client_id=x509_hash%3As7LWKrzlxlNQHWANY44P8Sgwo9TybBoUUkxb7gGwdKM&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-gym%2Fwallet%2Frequest.jwt%2FnZL5TonFegulwIMdjWDMTgYVlmV_dj2pO4irxT8haagK …
```

**4. Consultar el resultado de la solicitud** — `GET /gym/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**5. Propósito no válido: debe rechazar** — `POST /gym/api/solicitud`

Hay que decir `identificacion` o `acceso`.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"proposito: identificacion o acceso"}
```

**6. Puerta · clave incorrecta: debe rechazar** — `POST /gym/api/puerta/entrar`

Prueba de seguridad: una clave equivocada no abre la pantalla de la puerta.

**Resultado esperado:** HTTP 401 o 403 o 429.

*Comprobaciones automáticas:* HTTP 401 o 403 o 429.

*Respuesta real de ejemplo:*
```
HTTP 401  {"error":"Contraseña incorrecta."}
```

**7. Puerta · entrar con la clave** — `POST /gym/api/puerta/entrar`

Habilita esta sesión para las pantallas de entrada y salida.

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200 o 204.

*Comprobaciones automáticas:* HTTP 200 o 204.

**8. Puerta · aforo en vivo** — `GET /gym/api/puerta/aforo`

Cuánta gente hay dentro en cada sede.

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200 o 401.

*Comprobaciones automáticas:* HTTP 200 o 401.

**9. Administración · resumen** — `GET /gym/api/admin/resumen`

Aforo, socios y últimos accesos (requiere entrar como administrador).

**Necesita una clave** que no se comparte: se lee de la variable del entorno (ver la guía).

**Resultado esperado:** HTTP 200 o 401.

*Comprobaciones automáticas:* HTTP 200 o 401.

**10. Metadatos del emisor (OpenID4VCI)** — `GET /.well-known/openid-credential-issuer/gym`

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.gym.tarjeta_socio`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/gym","authorization_servers":["https://civica-desarrollo.avance.org.co/gym"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/gym/credencial","nonce_endpoint":"https://civica-desarroll …
```

**11. Nonce del emisor** — `POST /gym/nonce`

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `c_nonce`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"c_nonce":"Y42Ga80oWsUveCpK2wnaQZP1N0sz-A-j"}
```

**12. Lista de estado de las credenciales (revocación)** — `GET /gym/estado/1`

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUI1ekNDQVk2Z0F3SUJBZ0lVS1hUUmZQblNaQ0Jkd05wVGJHZ0RNM3RhaTQ0d0NnWUlLb1pJemowRUF3SXdQekVMTUFrR0ExVUVCaE1DUTA4eEV6QVJCZ05WQkFvTUNrTjFiV0p5WlNCSGVXMHhHekFaQmdOVkJBTU1Fa04xYldKeVpTQkhlVzBnVW05dmRDQkRR …
```

### Emisor+Verifier · Páramo Air (tiquetes)

Vende pasajes: verifica nombre, documento, nacimiento y foto del PID, **emite el tiquete** (QEAA) y lo verifica en el embarque.

**1. Salud del servicio** — `GET /aerolinea/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /aerolinea/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Destinos** — `GET /aerolinea/api/destinos`

Ciudades a las que vuela.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `ciudades`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"ciudades":[{"codigo":"BGA","nombre":"Bucaramanga","pais":"CO"},{"codigo":"BOG","nombre":"Bogotá","pais":"CO"},{"codigo":"MDE","nombre":"Medellín","pais":"CO"},{"codigo":"CLO","nombre":"Cali","pais":"CO"},{"codigo":"CTG","nombre":"Cartagena","pais":"CO"},{"co …
```

**4. Vuelos disponibles** — `GET /aerolinea/api/vuelos?origen=BOG&destino=MDE&fecha={{fecha_vuelo}}`

Vuelos de Bogotá a Medellín en la fecha indicada.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200.

*Respuesta real de ejemplo:*
```
HTTP 200  {"vuelos":[{"vuelo":"PMO 1273","origen":"BOG","destino":"MDE","fecha":"2026-11-26","salida":"06:00","llegada":"06:55","duracion_min":55,"base":149000,"internacional":false,"tarifas":{"basica":{"tarifa":149000,"tasas":46000,"total":195000,"nombre":"Básica","equ …
```

**5. Mismo origen y destino: debe rechazar** — `GET /aerolinea/api/vuelos?origen=BOG&destino=BOG&fecha={{fecha_vuelo}}`

Hay que elegir origen y destino distintos.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"Elige un origen y un destino distintos."}
```

**6. Crear solicitud de verificación** — `POST /aerolinea/api/solicitud`

La persona se identifica con su PID para comprar.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"ABnK9GjHM7wO4xGm9KtbJw","enlace":"haip-vp://?client_id=x509_hash%3AjGIiQfdrGknqRjd7rhxIbJtMDr9tobmV2tXdFEuHwL4&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-aerolinea%2Fwallet%2Frequest.jwt%2F6r1DAgS4chaCvplsmHRt62h8GmphBZ23RFvqoP …
```

**7. Consultar el resultado de la solicitud** — `GET /aerolinea/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**8. Metadatos del emisor (OpenID4VCI)** — `GET /.well-known/openid-credential-issuer/aerolinea`

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.aerolinea.tiquete`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/aerolinea","authorization_servers":["https://civica-desarrollo.avance.org.co/aerolinea"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/aerolinea/credencial","nonce_endpoint":"https: …
```

**9. Nonce del emisor** — `POST /aerolinea/nonce`

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `c_nonce`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"c_nonce":"hObRsC3UERGy83p2zy55oaghGc1Dhv3_"}
```

**10. Lista de estado de las credenciales (revocación)** — `GET /aerolinea/estado/1`

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUI3RENDQVpLZ0F3SUJBZ0lVUkpCTzJyZkFJOWt0TzUrZS9OT0tVcWdweXVJd0NnWUlLb1pJemowRUF3SXdRVEVMTUFrR0ExVUVCaE1DUTA4eEZEQVNCZ05WQkFvTUMxRERvWEpoYlc4Z1FXbHlNUnd3R2dZRFZRUUREQk5RdzZGeVlXMXZJRUZwY2lCU2IyOTBJ …
```

### Emisor+Verifier · Cancillería (pasaporte digital)

Solicitud, entrega única, reexpedición y verificación del **pasaporte digital** (PuB-EAA). Un pasaporte ya entregado solo se vuelve a emitir con reexpedición.

**1. Salud del servicio** — `GET /cancilleria/salud`

Comprobación rápida de que el portal está en pie.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; `estado` = `"ok"`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"ok"}
```

**2. Portal (página principal)** — `GET /cancilleria/`

La página que ve la persona en el navegador.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una página.

**3. Crear solicitud de verificación** — `POST /cancilleria/api/solicitud`

La persona se identifica con su PID para solicitar el pasaporte.

**Guarda para las siguientes pruebas:** `solicitud_id`

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `id`; trae `enlace`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"id":"us-b_xuGCv8VD5qs1RIMbA","enlace":"haip-vp://?client_id=x509_hash%3A4iJdG-UXuLdC0swupoYzvKpQVV51W3AZO6jnlVXk74A&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-cancilleria%2Fwallet%2Frequest.jwt%2F2R4R4B9Fs7FZseZ2u0ZOAD3xfPizCArJlRBq …
```

**4. Consultar el resultado de la solicitud** — `GET /cancilleria/api/solicitud/{{solicitud_id}}`

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

**Paso manual (lo hace una persona):** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `estado`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"estado":"esperando"}
```

**5. Propósito no válido: debe rechazar** — `POST /cancilleria/api/solicitud`

Hay que decir `identificacion` o `verificacion` con un tipo.

**Resultado esperado:** HTTP 400.

*Comprobaciones automáticas:* HTTP 400.

*Respuesta real de ejemplo:*
```
HTTP 400  {"error":"proposito: identificacion, o verificacion con un tipo de documento"}
```

**6. Trámites (sin identificarse)** — `GET /cancilleria/api/tramites`

Antes de identificarse con la wallet responde 401.

**Resultado esperado:** HTTP 401.

*Comprobaciones automáticas:* HTTP 401.

*Respuesta real de ejemplo:*
```
HTTP 401  {"error":"Primero identifícate con tu wallet."}
```

**7. Metadatos del emisor (OpenID4VCI)** — `GET /.well-known/openid-credential-issuer/cancilleria`

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.cancilleria.pasaporte`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"credential_issuer":"https://civica-desarrollo.avance.org.co/cancilleria","authorization_servers":["https://civica-desarrollo.avance.org.co/cancilleria"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/cancilleria/credencial","nonce_endpoint":" …
```

**8. Nonce del emisor** — `POST /cancilleria/nonce`

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; trae `c_nonce`.

*Respuesta real de ejemplo:*
```
HTTP 200  {"c_nonce":"AeivuE-ud-sJXAieoZkplSDrjzExpx9S"}
```

**9. Lista de estado de las credenciales (revocación)** — `GET /cancilleria/estado/1`

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

**Resultado esperado:** HTTP 200.

*Comprobaciones automáticas:* HTTP 200; devuelve una lista firmada (JWT).

*Respuesta real de ejemplo:*
```
HTTP 200  eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUNlVENDQWlDZ0F3SUJBZ0lVWFY1Y05qcXpmWUNjbWpmeC9tVnlmUHZqcnRvd0NnWUlLb1pJemowRUF3SXdnWWN4Q3pBSkJnTlZCQVlUQWtOUE1UY3dOUVlEVlFRS0RDNU5hVzVwYzNSbGNtbHZJR1JsSUZKbGJHRmphVzl1WlhNZ1JYaDBaWEpwYjNKbGN5Qmta …
```
