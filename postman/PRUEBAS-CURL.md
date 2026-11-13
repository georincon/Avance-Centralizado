# Pruebas del ecosistema desde la terminal (curl)

Las mismas pruebas de la colección de Postman, escritas como comandos `curl`, agrupadas por proyecto y con cada paso explicado. Dominio: `https://civica-desarrollo.avance.org.co`.

## Preparación

```bash
export BASE_URL=https://civica-desarrollo.avance.org.co
export JAR=$(mktemp)        # guarda las cookies de sesión entre comandos
# Claves (no se comparten): copia los valores de DATOS-LOCALES.md
export PANEL_TOKEN=...      # pruebas del panel
export TL_API_KEY=...       # publicar listas
export EPS_CLAVE=...  GYM_CLAVE=...
export ANDES_CLIENT_ID=wallet-client ANDES_CLIENT_SECRET=...
export KC_ADMIN_USER=admin KC_ADMIN_PASSWORD=...
export INSTANCIA=banco      # entidad cuyo verifier se prueba
export FECHA_VUELO=2026-11-26
```

- `-s` silencia el progreso y `-k` acepta el certificado de desarrollo.
- `jq` da formato al JSON (`sudo apt install jq`). Sin `jq`, quita `| jq .`.
- Los comandos de cada proyecto están pensados para ejecutarse **en orden**: unos guardan datos en variables de la terminal (`SOLICITUD_ID`, `TRANSACTION_ID`…) que usan los siguientes.
- Las pruebas marcadas **Paso manual** necesitan una persona con la wallet; sin ella, «esperando» es el resultado correcto.

## Ver todo en pie de un vistazo

```bash
for p in banco contratacion discoteca beca acceso pensiones migracion eps notaria gym aerolinea cancilleria; do
  printf "%-14s " $p; curl -sk "$BASE_URL/$p/salud"; echo
done
```

## 00 · Humo: ¿está todo en pie?

Una pasada rápida por todo el ecosistema. Si esta carpeta sale en verde, los servicios están arriba y se pueden hacer las demás pruebas. Úsala con el *Runner* de Postman: se ejecuta sola y no necesita claves.

### 1. banco · salud

Responde `ok` si el portal de banco está en pie.

```bash
curl -sk "$BASE_URL/banco/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. contratacion · salud

Responde `ok` si el portal de contratacion está en pie.

```bash
curl -sk "$BASE_URL/contratacion/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 3. discoteca · salud

Responde `ok` si el portal de discoteca está en pie.

```bash
curl -sk "$BASE_URL/discoteca/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 4. beca · salud

Responde `ok` si el portal de beca está en pie.

```bash
curl -sk "$BASE_URL/beca/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 5. acceso · salud

Responde `ok` si el portal de acceso está en pie.

```bash
curl -sk "$BASE_URL/acceso/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 6. pensiones · salud

Responde `ok` si el portal de pensiones está en pie.

```bash
curl -sk "$BASE_URL/pensiones/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 7. migracion · salud

Responde `ok` si el portal de migracion está en pie.

```bash
curl -sk "$BASE_URL/migracion/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 8. eps · salud

Responde `ok` si el portal de eps está en pie.

```bash
curl -sk "$BASE_URL/eps/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 9. notaria · salud

Responde `ok` si el portal de notaria está en pie.

```bash
curl -sk "$BASE_URL/notaria/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 10. gym · salud

Responde `ok` si el portal de gym está en pie.

```bash
curl -sk "$BASE_URL/gym/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok","aforo_maximo":60}
```

### 11. aerolinea · salud

Responde `ok` si el portal de aerolinea está en pie.

```bash
curl -sk "$BASE_URL/aerolinea/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 12. cancilleria · salud

Responde `ok` si el portal de cancilleria está en pie.

```bash
curl -sk "$BASE_URL/cancilleria/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 13. Wallet Provider · metadatos

El servicio que certifica cada instalación de la wallet.

```bash
curl -sk "$BASE_URL/wallet-provider/.well-known/oauth-protected-resource" | jq .
```

**Qué debes ver:** HTTP 200; trae `resource`.

Ejemplo de respuesta real:
```
{ "resource": "https://civica-desarrollo.avance.org.co/wallet-provider", "jwks_uri": "https://civica-desarrollo.avance.org.co/wallet-provider/jwks", "resource_name": "Spartans Wallet Provider", "resource_signing_alg_values_supported": [ "ES256" ], "client_atte …
```

### 14. Registraduría (PID) · metadatos

El emisor del documento de identidad.

```bash
curl -sk -H "Accept: application/json" "$BASE_URL/pid-registraduria/.well-known/openid-credential-issuer" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/pid-registraduria","authorization_servers":["https://civica-desarrollo.avance.org.co/idp-reg/realms/pid-registraduria-realm"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/pid-regis …
```

### 15. UIS · metadatos

El emisor de credenciales académicas.

```bash
curl -sk -H "Accept: application/json" "$BASE_URL/uis-issuer/.well-known/openid-credential-issuer" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/uis-issuer","authorization_servers":["https://civica-desarrollo.avance.org.co/idp-uis/realms/uis-issuer-realm"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/uis-issuer/wallet/crede …
```

### 16. Lista de confianza de emisores PID

Lista firmada (ETSI TS 119 612) de quiénes pueden emitir identidad.

```bash
curl -sk "$BASE_URL/tl-publisher/TL/PID/current.json" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InRsK2p3dCIsIng1YyI6WyJNSUlDM3pDQ0FvYWdBd0lCQWdJQ0VBQXdDZ1lJS29aSXpqMEVBd0l3UWpFTE1Ba0dBMVVFQmhNQ1EwOHhGREFTQmdOVkJBb01DME5wZG1sallTQkZWVVJKTVIwd0d3WURWUVFEREJSRGFYWnBZMkVnVkV3Z1UybG5ibWx1WnlCRFFUQWVGdzB5TmpFd01qQXhOVFF4TlRsYUZ3MHlO …
```

### 17. Validador de confianza · salud

El servicio que decide si una cadena de certificados es de confianza.

```bash
curl -sk "$BASE_URL/trust-validator/actuator/health" | jq .
```

**Qué debes ver:** HTTP 200; `status` = `"UP"`.

Ejemplo de respuesta real:
```
{"groups":["liveness","readiness"],"status":"UP"}
```

### 18. Andes SCD (firma remota) · salud

El prestador de firma simulado.

```bash
curl -sk "$BASE_URL/andes-scd/health" | jq .
```

**Qué debes ver:** HTTP 200; `ok` = `true`.

Ejemplo de respuesta real:
```
{"ok":true}
```

### 19. Onboarding · salud

Verificación de identidad previa a la emisión.

```bash
curl -sk "$BASE_URL/onboarding/actuator/health" | jq .
```

**Qué debes ver:** HTTP 200; `status` = `"UP"`.

Ejemplo de respuesta real:
```
{"groups":["liveness","readiness"],"status":"UP"}
```

### 20. Panel de control · ficha del APK

Última versión de la wallet disponible para descargar.

```bash
curl -sk "$BASE_URL/descargas/apk.json" | jq .
```

**Qué debes ver:** HTTP 200; trae `archivo`; trae `sha256`.

Ejemplo de respuesta real:
```
{"archivo":"app-dev-debug.apk","tamano":491087620,"subido":"2026-10-27T19:57:36.211Z","sha256":"3ff220096b2311c3e5c955095585b723d6b74326adacb91b0442c9d6862b9cb7","url":"https://localhost/descargas/wallet.apk"}
```

## Panel de control

Panel de operación (`/`): sube, baja y reinicia los stacks. Su API exige el token `CONTROL_PANEL_TOKEN` (cabecera `Authorization: Bearer …`). Las rutas `/descargas/*` son públicas (las abre el teléfono).

### 1. Estado de todos los proyectos

Lista cada stack con su estado real (activo, parcial, detenido), contenedores y enlace. Es lo que pinta el panel.

```bash
curl -sk -H "Authorization: Bearer $PANEL_TOKEN" "$BASE_URL/api/projects" | jq .
```

**Qué debes ver:** HTTP 200.

### 2. Sin token: debe rechazar

Prueba de seguridad: sin el token, la API responde 401.

```bash
curl -sk -w '\nHTTP %{http_code}\n' "$BASE_URL/api/projects"
```

**Qué debes ver:** HTTP 401.

Ejemplo de respuesta real:
```
{"error":"Token de acceso incorrecto o ausente."}
```

### 3. Recursos del ecosistema

Contenedores, redes y volúmenes con puertos y enlaces (la vista `/recursos`).

```bash
curl -sk -H "Authorization: Bearer $PANEL_TOKEN" "$BASE_URL/api/recursos" | jq .
```

**Qué debes ver:** HTTP 200; trae `contenedores`.

### 4. Reiniciar un proyecto

Sube (`up`), baja (`down`) o reinicia (`restart`) un stack. Cambia el estado real del sistema: úsala a propósito.

```bash
curl -sk -X POST -H "Authorization: Bearer $PANEL_TOKEN" -H "Content-Type: application/json" "$BASE_URL/api/projects/verifier-discoteca/action" \
  -d '{"action": "restart"}' | jq .
```

**Qué debes ver:** HTTP 200.

### 5. Ficha del APK de la wallet

Archivo, tamaño, fecha, SHA-256 y enlace de la última versión compilada de la wallet.

```bash
curl -sk "$BASE_URL/descargas/apk.json" | jq .
```

**Qué debes ver:** HTTP 200; trae `archivo`; trae `tamano`; trae `sha256`; trae `url`.

Ejemplo de respuesta real:
```
{"archivo":"app-dev-debug.apk","tamano":491087620,"subido":"2026-10-27T19:57:36.211Z","sha256":"3ff220096b2311c3e5c955095585b723d6b74326adacb91b0442c9d6862b9cb7","url":"https://localhost/descargas/wallet.apk"}
```

### 6. QR de descarga de la wallet

El código QR (SVG) que apunta al APK.

```bash
curl -sk "$BASE_URL/descargas/qr.svg" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una imagen SVG.

Ejemplo de respuesta real:
```
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 31 31" shape-rendering="crispEdges"><path fill="#ffffff" d="M0 0h31v31H0z"/><path stroke="#000000" d="M1 1.5h7m2 0h2m3 0h1m1 0h1m1 0h3m1 0h7M1 2.5h1m5 0h1m8 0h2m1 0h1m1 0h1m1 0h1m5 0h1M1 3.5h1m1 0h3m1 0h1m1  …
```

### 7. Descarga del APK (primeros 4 bytes)

Pide solo los primeros bytes (`Range`) para probar la descarga sin bajar 400 MB. Debe responder 206.

```bash
curl -sk -H "Range: bytes=0-3" "$BASE_URL/descargas/wallet.apk" | jq .
```

**Qué debes ver:** HTTP 206.

Ejemplo de respuesta real:
```
PK
```

## Confianza · tl-publisher (listas de confianza)

Registro de actores y publicación de las listas de confianza firmadas (ETSI TS 119 612): WP (wallet providers), PID, QEAA, PUBEAA y RPREG, más la LOTL (lista de listas). Cada lista sale en XML y en `tl+jwt`.

### 1. Listas publicadas

Todas las listas con su secuencia, fecha y próxima actualización.

```bash
curl -sk "$BASE_URL/tl-publisher/tl-api/v1/lists" | jq .
```

**Qué debes ver:** HTTP 200.

Ejemplo de respuesta real:
```
[{"scheme":"PID","name":"Civica PID Provider Trusted List","tsl_type":"http://uri.etsi.org/TrstSvc/TrustedList/TSLType/EUgeneric","is_lotl":false,"published":true,"sequence_number":21,"issue_date":"2026-10-27T12:27:56","next_update":"2026-10-28T12:27:56","sha2 …
```

### 2. Lista de emisores PID (JWT)

La lista que consume la wallet, firmada (`tl+jwt`).

```bash
curl -sk "$BASE_URL/TL/PID/current.json" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InRsK2p3dCIsIng1YyI6WyJNSUlDM3pDQ0FvYWdBd0lCQWdJQ0VBQXdDZ1lJS29aSXpqMEVBd0l3UWpFTE1Ba0dBMVVFQmhNQ1EwOHhGREFTQmdOVkJBb01DME5wZG1sallTQkZWVVJKTVIwd0d3WURWUVFEREJSRGFYWnBZMkVnVkV3Z1UybG5ibWx1WnlCRFFUQWVGdzB5TmpFd01qQXhOVFF4TlRsYUZ3MHlO …
```

### 3. Lista de emisores PubEAA (XML)

La misma lista en el formato oficial XML.

```bash
curl -sk "$BASE_URL/TL/PUBEAA/current.xml" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 4. Lista de listas (LOTL)

Apunta a todas las demás listas.

```bash
curl -sk "$BASE_URL/LOTL/current.json" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InRsK2p3dCIsIng1YyI6WyJNSUlDM3pDQ0FvYWdBd0lCQWdJQ0VBQXdDZ1lJS29aSXpqMEVBd0l3UWpFTE1Ba0dBMVVFQmhNQ1EwOHhGREFTQmdOVkJBb01DME5wZG1sallTQkZWVVJKTVIwd0d3WURWUVFEREJSRGFYWnBZMkVnVkV3Z1UybG5ibWx1WnlCRFFUQWVGdzB5TmpFd01qQXhOVFF4TlRsYUZ3MHlO …
```

### 5. Anclas de confianza de una lista

Los certificados raíz en los que confía esa lista.

```bash
curl -sk "$BASE_URL/tl-publisher/tl-api/v1/lists/PID/trust-anchors" | jq .
```

**Qué debes ver:** HTTP 200.

Ejemplo de respuesta real:
```
{"scheme":"PID","sequence_number":21,"issue_date":"2026-10-27T12:27:56","next_update":"2026-10-28T12:27:56","xml_uri":"https://civica-desarrollo.avance.org.co/TL/PID/21.xml","xml_sha256":"355399e4d0923680b6203de1de4b4f61b0fa30f8e5374d683bfb684ae1ac034b","servi …
```

### 6. Publicar sin clave: debe rechazar

Publicar es una operación privilegiada: sin `X-API-Key` responde 401.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST "$BASE_URL/tl-publisher/tl-api/v1/publish/PID"
```

**Qué debes ver:** HTTP 401.

Ejemplo de respuesta real:
```
{"detail":"X-API-Key incorrecta o ausente"}
```

### 7. Publicar una lista (con clave)

Vuelve a firmar y publicar la lista PID con una nueva secuencia.

```bash
curl -sk -X POST -H "X-API-Key: $TL_API_KEY" "$BASE_URL/tl-publisher/tl-api/v1/publish/PID" | jq .
```

**Qué debes ver:** HTTP 200.

### 8. Política (CPS) de la PKI

Declaración de prácticas de la PKI del ecosistema.

```bash
curl -sk "$BASE_URL/pki/cps" | jq .
```

**Qué debes ver:** HTTP 200.

Ejemplo de respuesta real:
```
<!doctype html> <html lang="es"><head><meta charset="utf-8"> <title>CPS — PKI del ecosistema EUDI</title> <style>body{font-family:system-ui,sans-serif;max-width:50rem;margin:3rem auto;padding:0 1rem;line-height:1.6} table{border-collapse:collapse;width:100%}td …
```

## Confianza · trust-validator

Servicio oficial de EUDI que responde «¿esta cadena de certificados es de confianza para este rol?» leyendo las listas. Estas pruebas usan un certificado real: el del verifier del banco.

### 1. Salud

Estado del servicio.

```bash
curl -sk "$BASE_URL/trust-validator/actuator/health" | jq .
```

**Qué debes ver:** HTTP 200; `status` = `"UP"`.

Ejemplo de respuesta real:
```
{"groups":["liveness","readiness"],"status":"UP"}
```

### 2. 1 · Obtener la cadena de un verifier

Descarga la clave pública del verifier del banco, que trae su cadena de certificados (`x5c`). Se guarda para la prueba siguiente.

```bash
X5C_CHAIN=$(curl -sk "$BASE_URL/verifier-banco/wallet/public-keys.json" | jq -r '.keys[0].x5c')
echo "X5C_CHAIN=$X5C_CHAIN"
```

**Qué debes ver:** HTTP 200; trae `keys`.

Ejemplo de respuesta real:
```
{"keys":[{"kty":"EC","x5t#S256":"8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk","nbf":1792676385,"use":"sig","crv":"P-256","kid":"verifier","x5c":["MIIDIjCCAsegAwIBAgICEAEwCgYIKoZIzj0EAwIwQTELMAkGA1UEBhMCQ08xFDASBgNVBAoMC0NpdmljYSBFVURJMRwwGgYDVQQDDBNDaXZpY2EgUl …
```

### 3. 2 · Validar la cadena con el rol correcto

La cadena del verifier, validada como *certificado de acceso de parte usuaria*: es el rol que tiene. Responde `trusted: true` y el ancla.

```bash
curl -sk -X POST -H "Content-Type: application/json" "$BASE_URL/trust-validator/trust" \
  -d '{"chain": $X5C_CHAIN, "verificationContext": "WalletRelyingPartyAccessCertificate"}' | jq .
```

**Qué debes ver:** HTTP 200; `trusted` = `true`.

Ejemplo de respuesta real:
```
{"trusted":true,"trustAnchor":"MIIC3jCCAmOgAwIBAgICEAUwCgYIKoZIzj0EAwMwQTELMAkGA1UEBhMCQ08xFDASBgNVBAoMC0NpdmljYSBFVURJMRwwGgYDVQQDDBNDaXZpY2EgRVVESSBSb290IENBMB4XDTI2MTAyMDE1NDE1MloXDTM2MTAxNzE1NDE1MlowQTELMAkGA1UEBhMCQ08xFDASBgNVBAoMC0NpdmljYSBFVURJMRwwGgYDV …
```

### 4. 3 · Una cadena que no es un certificado: debe rechazar

Si lo que se envía no es un certificado X.509 válido, el validador no lo puede leer y responde 400.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "Content-Type: application/json" "$BASE_URL/trust-validator/trust" \
  -d '{"chain": ["AAAA"], "verificationContext": "PID"}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"description":"Request body cannot be parsed: Could not parse certificate: java.io.IOException: Empty input"}
```

### 5. Cuerpo vacío: debe rechazar

Sin `chain` ni `verificationContext` la petición no se puede leer.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "Content-Type: application/json" "$BASE_URL/trust-validator/trust" \
  -d '{}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"description":"Request body cannot be parsed: 400 BAD_REQUEST \"Failed to read HTTP message\""}
```

## Confianza · Andes SCD (firma remota)

Prestador de servicios de firma remota **simulado** (CSC v2.2) y autoridad de sellado de tiempo. La wallet firma contratos con él; el portal de contratación sella las firmas. No es un prestador cualificado: es de prueba.

### 1. Salud

Estado del servicio.

```bash
curl -sk "$BASE_URL/andes-scd/health" | jq .
```

**Qué debes ver:** HTTP 200; `ok` = `true`.

Ejemplo de respuesta real:
```
{"ok":true}
```

### 2. Información del prestador (CSC)

Qué métodos de autenticación y operaciones soporta.

```bash
curl -sk "$BASE_URL/andes-scd/csc/v2/info" | jq .
```

**Qué debes ver:** HTTP 200; trae `name`; trae `authType`.

Ejemplo de respuesta real:
```
{"specs":"2.2.0.0","name":"Andes SCD (simulador)","logo":"https://civica-desarrollo.avance.org.co/andes-scd/static/logo.svg","region":"CO","lang":"es-CO","description":"Simulador de firma remota del ecosistema Avance. Solo para pruebas.","authType":["oauth2cod …
```

### 3. Metadatos OAuth

Endpoints de autorización, token y PAR.

```bash
curl -sk "$BASE_URL/andes-scd/.well-known/oauth-authorization-server" | jq .
```

**Qué debes ver:** HTTP 200; trae `issuer`; trae `token_endpoint`.

Ejemplo de respuesta real:
```
{"issuer":"https://civica-desarrollo.avance.org.co/andes-scd","authorization_endpoint":"https://civica-desarrollo.avance.org.co/andes-scd/oauth2/authorize","token_endpoint":"https://civica-desarrollo.avance.org.co/andes-scd/oauth2/token","pushed_authorization_ …
```

### 4. Token de cliente (client credentials)

Token del cliente de la wallet (`wallet-client`). Sirve para operaciones del servicio, pero **no identifica a un firmante**.

```bash
ANDES_TOKEN=$(curl -sk -X POST "$BASE_URL/andes-scd/oauth2/token" \
  -d grant_type=client_credentials \
  -d client_id=$ANDES_CLIENT_ID \
  -d client_secret=$ANDES_CLIENT_SECRET | jq -r '.access_token')
echo "ANDES_TOKEN=$ANDES_TOKEN"
```

**Qué debes ver:** HTTP 200; trae `access_token`.

### 5. Listar credenciales de firma sin firmante: debe rechazar

Con el token de cliente no hay firmante: responde 403. Para listar las credenciales de firma de una persona hay que hacer el flujo de autorización con su identidad (lo hace la wallet).

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "Authorization: Bearer $ANDES_TOKEN" -H "Content-Type: application/json" "$BASE_URL/andes-scd/csc/v2/credentials/list" \
  -d '{}'
```

**Qué debes ver:** HTTP 403.

### 6. Sello de tiempo (RFC 3161)

Recibe una consulta de sello (`consulta.tsq`) y devuelve el sello firmado. Se crea con: `openssl ts -query -data archivo.txt -sha256 -cert -no_nonce -out consulta.tsq`.

```bash
curl -sk -X POST -H "Content-Type: application/timestamp-query" "$BASE_URL/andes-scd/tsa" \
  --data-binary @consulta.tsq | jq .
```

**Qué debes ver:** HTTP 200.

## Wallet · wallet-provider

Servicio (Spartans) que certifica que cada instalación de la wallet y su clave son legítimas: emite la Wallet Instance Attestation y la Key Attestation, con una lista de estado para poder revocarlas. Valida el paquete Android de la app.

### 1. Metadatos (RFC 9728)

Quién es el servicio y dónde están sus claves.

```bash
curl -sk "$BASE_URL/wallet-provider/.well-known/oauth-protected-resource" | jq .
```

**Qué debes ver:** HTTP 200; trae `resource`; trae `jwks_uri`.

Ejemplo de respuesta real:
```
{ "resource": "https://civica-desarrollo.avance.org.co/wallet-provider", "jwks_uri": "https://civica-desarrollo.avance.org.co/wallet-provider/jwks", "resource_name": "Spartans Wallet Provider", "resource_signing_alg_values_supported": [ "ES256" ], "client_atte …
```

### 2. Claves públicas (JWKS)

Con estas claves se comprueban las atestaciones que firma.

```bash
curl -sk "$BASE_URL/wallet-provider/jwks" | jq .
```

**Qué debes ver:** HTTP 200; trae `keys`.

Ejemplo de respuesta real:
```
{ "keys": [ { "crv": "P-256", "kty": "EC", "x": "ot7FgAYfg1mHOVLQ3TQQ-y_E4vh-c7pKXwu0cuNtq1g", "x5c": [ "MIIB7TCCAZKgAwIBAgIUGh5efSBSP8yoZUYLQLB1HPP/tu0wCgYIKoZIzj0EAwIwSTELMAkGA1UEBhMCQ08xDzANBgNVBAoMBkF2YW5jZTEpMCcGA1UEAwwgU3BhcnRhbnMgV2FsbGV0IFByb3ZpZGVyIFJ …
```

### 3. Pedir un desafío (challenge)

Primer paso de la atestación: el servicio entrega un valor aleatorio que la wallet debe incluir en su prueba de integridad.

```bash
curl -sk -X POST "$BASE_URL/wallet-provider/challenge" | jq .
```

**Qué debes ver:** HTTP 200; trae `challenge`.

Ejemplo de respuesta real:
```
{ "challenge": "0rgTLSsDWMEKLxdohl0IsqpRn3ZwjZ40fjkePM3fBWJhSsMsfva_GmD7HEsAxHJY6wECW-MuZI1XML4IBaPm_ci_dWyClstXt4NJLj8UGkrF9iqKwqya6gdhtWILlrBo9-gz1aTSoWc4d4aQg4CJKXniuXUAOrJYamIpSwui46s" }
```

### 4. Atestación sin datos válidos: debe rechazar

Sin una prueba de integridad real del dispositivo, no emite nada. Solo una app Android genuina puede completarla.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "Content-Type: application/json" "$BASE_URL/wallet-provider/wallet-instance-attestation/jwk" \
  -d '{}'
```

**Qué debes ver:** HTTP 400 o 415 o 422.

Ejemplo de respuesta real:
```
Failed to convert request body to class eu.europa.ec.eudi.walletprovider.port.input.walletinstanceattestation.WalletInstanceAttestationIssuanceRequest$Jwk
```

## Revocación · listas de estado (status lists)

Servicios que publican, firmadas, las listas de estado de las credenciales (draft-ietf-oauth-status-list): un bit por credencial, 1 = revocada. Hay una por servicio (`reg`, `uis`, `spartans`) y una propia por cada entidad simulada (`/<entidad>/estado/1`).

### 1. Lista de estado de las atestaciones de wallet

Una lista real del Wallet Provider (la atestación de cada wallet apunta a una posición de aquí). La URL exacta la trae la credencial; en la guía está cómo sacarla de la base de datos.

```bash
curl -sk -H "Accept: application/statuslist+jwt" "$BASE_URL$STATUS_LIST_RUTA" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUJ6ekNDQVhXZ0F3SUJBZ0lVQmRoNFg5SDl6QS9ldE1WSGxYSjVGWlZpL3NNd0NnWUlLb1pJemowRUF3SXdQVEVMTUFrR0ExVUVCaE1DUTA4eER6QU5CZ05WQkFvTUJrRjJZVzVqWlRFZE1Cc0dBMVVFQXd3VVUzQmhjblJoYm5NZ1UzUmhkSFZ6SUV4cGMzUXdI …
```

### 2. Lista de estado de la EPS

Lista propia de la EPS (carnés y resúmenes de historia).

```bash
curl -sk "$BASE_URL/eps/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUNBRENDQWFhZ0F3SUJBZ0lVQVJ3UDh2NXp5azZKMHpBTmNyM0pvdDRXbE1Vd0NnWUlLb1pJemowRUF3SXdTekVMTUFrR0ExVUVCaE1DUTA4eEdUQVhCZ05WQkFvTUVFVlFVeUJUWVd4MVpDQkJkbUZ1WTJVeElUQWZCZ05WQkFNTUdFVlFVeUJUWVd4MVpDQkJk …
```

### 3. Lista de estado de la notaría

Lista propia de la notaría.

```bash
curl -sk "$BASE_URL/notaria/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUIvVENDQWFLZ0F3SUJBZ0lVWFBqWG5pUUtXTGh6elcyajZPdlFNRVArU053d0NnWUlLb1pJemowRUF3SXdTVEVMTUFrR0ExVUVCaE1DUTA4eEdEQVdCZ05WQkFvTUQwNXZkR0Z5dzYxaElFRjJZVzVqWlRFZ01CNEdBMVVFQXd3WFRtOTBZWExEcldFZ1FYWmhi …
```

### 4. Lista de estado del gimnasio

Lista propia del gimnasio (tarjetas de socio).

```bash
curl -sk "$BASE_URL/gym/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUI1ekNDQVk2Z0F3SUJBZ0lVS1hUUmZQblNaQ0Jkd05wVGJHZ0RNM3RhaTQ0d0NnWUlLb1pJemowRUF3SXdQekVMTUFrR0ExVUVCaE1DUTA4eEV6QVJCZ05WQkFvTUNrTjFiV0p5WlNCSGVXMHhHekFaQmdOVkJBTU1Fa04xYldKeVpTQkhlVzBnVW05dmRDQkRR …
```

### 5. Lista de estado de la aerolínea

Lista propia de la aerolínea (tiquetes).

```bash
curl -sk "$BASE_URL/aerolinea/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUI3RENDQVpLZ0F3SUJBZ0lVUkpCTzJyZkFJOWt0TzUrZS9OT0tVcWdweXVJd0NnWUlLb1pJemowRUF3SXdRVEVMTUFrR0ExVUVCaE1DUTA4eEZEQVNCZ05WQkFvTUMxRERvWEpoYlc4Z1FXbHlNUnd3R2dZRFZRUUREQk5RdzZGeVlXMXZJRUZwY2lCU2IyOTBJ …
```

### 6. Lista de estado de la cancillería

Lista propia de la cancillería (pasaportes).

```bash
curl -sk "$BASE_URL/cancilleria/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUNlVENDQWlDZ0F3SUJBZ0lVWFY1Y05qcXpmWUNjbWpmeC9tVnlmUHZqcnRvd0NnWUlLb1pJemowRUF3SXdnWWN4Q3pBSkJnTlZCQVlUQWtOUE1UY3dOUVlEVlFRS0RDNU5hVzVwYzNSbGNtbHZJR1JsSUZKbGJHRmphVzl1WlhNZ1JYaDBaWEpwYjNKbGN5Qmta …
```

## Wallet · onboarding (verificación de identidad)

Identity proofing previo a pedir la PID: crea una sesión, verifica el contacto con un código (OTP), pide consentimiento, captura el documento y la selfie, y decide. Base de datos Postgres en el puerto 5436. En desarrollo el código OTP viene en la respuesta (`devCode`).

### 1. Salud del backend

Estado del servicio.

```bash
curl -sk "$BASE_URL/onboarding/actuator/health" | jq .
```

**Qué debes ver:** HTTP 200; `status` = `"UP"`.

Ejemplo de respuesta real:
```
{"groups":["liveness","readiness"],"status":"UP"}
```

### 2. Salud de biometría y OCR

Servicio que compara el rostro y lee el documento.

```bash
curl -sk "$BASE_URL/biometrics/health" | jq .
```

**Qué debes ver:** HTTP 200.

Ejemplo de respuesta real:
```
{"status":"UP"}
```

### 3. 1 · Crear una sesión

Abre una sesión de onboarding (dura 2 horas). Devuelve su `id`.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST "$BASE_URL/onboarding/api/v1/onboarding/sessions"
```

**Qué debes ver:** HTTP 201; trae `id`; trae `status`.

Ejemplo de respuesta real:
```
{"id":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"CREATED","expiresAt":"2026-10-27T21:33:13.081333696Z"}
```

### 4. 2 · Ver el estado de la sesión

Estado, decisión y datos capturados hasta ahora.

```bash
curl -sk "$BASE_URL/onboarding/api/v1/onboarding/sessions/$SESSION_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `status`; trae `contactVerified`.

Ejemplo de respuesta real:
```
{"id":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"CREATED","decision":null,"contactVerified":false,"consentAccepted":false,"failureReason":null,"data":{"documentNumber":null,"firstName":null,"middleName":null,"lastName":null,"secondLastName":null,"birthDa …
```

### 5. 3 · Enviar el código al contacto

Envía un código al correo o teléfono. En desarrollo no sale a ningún lado: llega en `devCode`.

```bash
DEV_CODE=$(curl -sk -X POST -H "Content-Type: application/json" "$BASE_URL/onboarding/api/v1/onboarding/sessions/$SESSION_ID/contact" \
  -d '{"channel": "EMAIL", "email": "prueba@example.com"}' | jq -r '.devCode')
echo "DEV_CODE=$DEV_CODE"
```

**Qué debes ver:** HTTP 200; trae `status`; trae `devCode`.

Ejemplo de respuesta real:
```
{"sessionId":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"CONTACT_PENDING","channel":"EMAIL","destinationMasked":"**************.com","provider":"logging","devCode":"733338"}
```

### 6. 4 · Verificar el código

Confirma que la persona controla ese contacto.

```bash
curl -sk -X POST -H "Content-Type: application/json" "$BASE_URL/onboarding/api/v1/onboarding/sessions/$SESSION_ID/contact/verify" \
  -d '{"code": "$DEV_CODE"}' | jq .
```

**Qué debes ver:** HTTP 200; `contactVerified` = `true`.

Ejemplo de respuesta real:
```
{"id":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"CONTACT_VERIFIED","decision":null,"contactVerified":true,"consentAccepted":false,"failureReason":null,"data":{"documentNumber":null,"firstName":null,"middleName":null,"lastName":null,"secondLastName":null, …
```

### 7. 5 · Aceptar el consentimiento

La persona acepta el tratamiento de sus datos.

```bash
curl -sk -X POST -H "Content-Type: application/json" "$BASE_URL/onboarding/api/v1/onboarding/sessions/$SESSION_ID/consent" \
  -d '{"accepted": true, "policyVersion": "v1"}' | jq .
```

**Qué debes ver:** HTTP 200; `consentAccepted` = `true`.

Ejemplo de respuesta real:
```
{"id":"36d89c9c-8b02-4e79-a455-a859f724c9d9","status":"DOCUMENTS_PENDING","decision":null,"contactVerified":true,"consentAccepted":true,"failureReason":null,"data":{"documentNumber":null,"firstName":null,"middleName":null,"lastName":null,"secondLastName":null, …
```

### 8. 6 · Auditoría de la sesión

Cada paso queda registrado con hora.

```bash
curl -sk "$BASE_URL/onboarding/api/v1/onboarding/sessions/$SESSION_ID/audit" | jq .
```

**Qué debes ver:** HTTP 200.

Ejemplo de respuesta real:
```
[{"type":"SESSION_CREATED","message":"Sesion de onboarding creada","createdAt":"2026-10-27T20:33:13.083213Z"},{"type":"OTP_SENT","message":"Codigo enviado por EMAIL","createdAt":"2026-10-27T20:33:13.103431Z"},{"type":"CONTACT_VERIFIED","message":"Contacto veri …
```

## Emisor · Registraduría (PID)

Emisor oficial del documento de identidad digital (PID, `urn:eudi:pid:1` en SD-JWT y `eu.europa.ec.eudi.pid.1` en mdoc). Los ciudadanos de prueba están en su base y se autentica con Keycloak. Puerto de su base de datos: 5441.

### 1. Metadatos OpenID4VCI

Credenciales que ofrece, formatos y datos de cada una. Pide `Accept: application/json` (sin él puede responder firmado).

```bash
curl -sk -H "Accept: application/json" "$BASE_URL/pid-registraduria/.well-known/openid-credential-issuer" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/pid-registraduria","authorization_servers":["https://civica-desarrollo.avance.org.co/idp-reg/realms/pid-registraduria-realm"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/pid-regis …
```

### 2. Metadatos JWT-VC

Cómo verificar las credenciales SD-JWT que firma.

```bash
curl -sk "$BASE_URL/pid-registraduria/.well-known/jwt-vc-issuer" | jq .
```

**Qué debes ver:** HTTP 200; trae `issuer`.

Ejemplo de respuesta real:
```
{"issuer":"https://civica-desarrollo.avance.org.co/pid-registraduria","jwks":{"keys":[{"kty":"EC","x5t#S256":"ML-W8M9HFduoWDKBicQQBnZZPtf4iNWteMXTn4cl2ek","nbf":1792157331,"use":"sig","crv":"P-256","kid":"issuance","x5c":["MIIB+DCCAZ6gAwIBAgIUR+uXxTPJfcsN/VluU …
```

### 3. Claves públicas del emisor

JWKS con la clave de firma.

```bash
curl -sk "$BASE_URL/pid-registraduria/public_keys.jwks" | jq .
```

**Qué debes ver:** HTTP 200; trae `keys`.

Ejemplo de respuesta real:
```
{"keys":[{"kty":"EC","x5t#S256":"ML-W8M9HFduoWDKBicQQBnZZPtf4iNWteMXTn4cl2ek","nbf":1792157331,"use":"sig","crv":"P-256","kid":"issuance","x5c":["MIIB+DCCAZ6gAwIBAgIUR+uXxTPJfcsN/VluUwnTVrW25UgwCgYIKoZIzj0EAwIwLjEsMCoGA1UEAwwjUkVHSVNUUkFEVVJJQSBJU1NVQU5DRSBJTl …
```

### 4. Metadatos del tipo de la PID

Descripción y datos del tipo `urn:eudi:pid:1`.

```bash
curl -sk "$BASE_URL/pid-registraduria/type-metadata/urn:eudi:pid:1" | jq .
```

**Qué debes ver:** HTTP 200.

Ejemplo de respuesta real:
```
{"vct":"urn:eudi:pid:1","name":"Type Metadata for Person Identification Data","display":[{"locale":"es-CO","name":"Cédula de Ciudadanía (SD-JWT VC Compact)","description":"Gobierno de Colombia","rendering":{"simple":{"logo":{"uri":"https://civica-desarrollo.av …
```

### 5. Pantalla ciudadana: solicitar la PID

La página donde el ciudadano pide su PID digital (sin login; se identifica con el onboarding).

```bash
curl -sk "$BASE_URL/pid-registraduria/registraduria/solicitar" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 6. Login del panel de administración

Formulario de acceso del administrador de la Registraduría (la clave está en `DATOS-LOCALES.md`).

```bash
curl -sk "$BASE_URL/pid-registraduria/registraduria/admin/login" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 7. Crear una oferta sin indicar qué credencial: debe rechazar

La API para crear ofertas exige saber qué credencial se ofrece.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "Content-Type: application/json" "$BASE_URL/pid-registraduria/issuer/credentialsOffer/create" \
  -d '{}'
```

**Qué debes ver:** HTTP 400; `error` = `"MissingCredentialConfigurationIds"`.

Ejemplo de respuesta real:
```
{"error":"MissingCredentialConfigurationIds"}
```

### 8. Token de administrador de Keycloak

Pide un token para operar el Keycloak de la Registraduría (sus claves están en `DATOS-LOCALES.md`).

```bash
KC_TOKEN=$(curl -sk -X POST "$BASE_URL/idp-reg/realms/master/protocol/openid-connect/token" \
  -d grant_type=password \
  -d client_id=admin-cli \
  -d username=$KC_ADMIN_USER \
  -d password=$KC_ADMIN_PASSWORD | jq -r '.access_token')
echo "KC_TOKEN=$KC_TOKEN"
```

**Qué debes ver:** HTTP 200; trae `access_token`.

### 9. Configuración OIDC del realm

Endpoints de autorización y token del realm donde viven los ciudadanos.

```bash
curl -sk "$BASE_URL/idp-reg/realms/pid-registraduria-realm/.well-known/openid-configuration" | jq .
```

**Qué debes ver:** HTTP 200; trae `issuer`; trae `token_endpoint`.

Ejemplo de respuesta real:
```
{"challenge_endpoint":"https://civica-desarrollo.avance.org.co/idp-reg/realms/pid-registraduria-realm/challenge","client_attestation_signing_alg_values_supported":["ES256","ES384","ES512"],"client_attestation_pop_signing_alg_values_supported":["ES256","ES384", …
```

## Emisor · UIS (credenciales académicas)

Universidad Industrial de Santander: emite credencial académica (`urn:eudi:academic:credential:1`), de aprendizaje (`urn:eu.europa.ec.eudi:learning:credential:1`) y **carnet estudiantil** (`urn:co:uis:carnet-estudiantil:1`). Sus datos académicos viven en Postgres (puerto 5433) y su registro de credenciales en el 5434.

### 1. Metadatos OpenID4VCI

Credenciales que ofrece la UIS: académica, aprendizaje, carnet, PID y mDL. Pide `Accept: application/json`.

```bash
curl -sk -H "Accept: application/json" "$BASE_URL/uis-issuer/.well-known/openid-credential-issuer" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/uis-issuer","authorization_servers":["https://civica-desarrollo.avance.org.co/idp-uis/realms/uis-issuer-realm"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/uis-issuer/wallet/crede …
```

### 2. Metadatos JWT-VC

Cómo verificar las credenciales que firma.

```bash
curl -sk "$BASE_URL/uis-issuer/.well-known/jwt-vc-issuer" | jq .
```

**Qué debes ver:** HTTP 200; trae `issuer`.

Ejemplo de respuesta real:
```
{"issuer":"https://civica-desarrollo.avance.org.co/uis-issuer","jwks":{"keys":[{"kty":"EC","x5t#S256":"W4hvUaBZfn1-gFgOEq3blFpwptmzeA-zGXko3v1hngY","nbf":1791989487,"use":"sig","crv":"P-256","kid":"pid-issuance","x5c":["MIICITCCAcigAwIBAgIUCx+DD8wYVgvuilzBildV …
```

### 3. Claves públicas del emisor

JWKS con la clave de firma.

```bash
curl -sk "$BASE_URL/uis-issuer/public_keys.jwks" | jq .
```

**Qué debes ver:** HTTP 200; trae `keys`.

Ejemplo de respuesta real:
```
{"keys":[{"kty":"EC","x5t#S256":"W4hvUaBZfn1-gFgOEq3blFpwptmzeA-zGXko3v1hngY","nbf":1791989487,"use":"sig","crv":"P-256","kid":"pid-issuance","x5c":["MIICITCCAcigAwIBAgIUCx+DD8wYVgvuilzBildVI0i3BlYwCgYIKoZIzj0EAwIwXDELMAkGA1UEBhMCQ08xLDAqBgNVBAoMI1VuaXZlcnNpZG …
```

### 4. Metadatos del tipo «aprendizaje»

Descripción del tipo de credencial de aprendizaje.

```bash
curl -sk "$BASE_URL/uis-issuer/type-metadata/urn:eu.europa.ec.eudi:learning:credential:1" | jq .
```

**Qué debes ver:** HTTP 200.

Ejemplo de respuesta real:
```
{"vct":"urn:eu.europa.ec.eudi:learning:credential:1","name":"Micro-Credential Attestation SD-JWT VC Type Metadata","display":[{"locale":"en","name":"Learning Credential","description":"Learning Credential"}],"claims":[{"path":["jti"],"display":[{"locale":"en", …
```

### 5. Metadatos del tipo académico (no existe)

Este tipo se anuncia en los metadatos pero no publica su descripción: 404. Se deja como caso conocido.

```bash
curl -sk -w '\nHTTP %{http_code}\n' "$BASE_URL/uis-issuer/type-metadata/urn:eudi:academic:credential:1"
```

**Qué debes ver:** HTTP 404.

Ejemplo de respuesta real:
```

```

### 6. Portal del emisor (login del estudiante)

Donde el estudiante entra con su usuario y clave para generar el QR de su carnet. Los estudiantes de prueba están en `DATOS-LOCALES.md`.

```bash
curl -sk "$BASE_URL/uis-issuer/issuer/login" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 7. Configuración OIDC del realm de la UIS

Endpoints de autorización y token del Keycloak de la UIS. Si ese Keycloak está apagado (el panel apaga uno de los dos con «Activar consola», porque comparten sesión), responde 503: es un estado válido, no un fallo.

```bash
curl -sk -w '\nHTTP %{http_code}\n' "$BASE_URL/idp-uis/realms/uis-issuer-realm/.well-known/openid-configuration"
```

**Qué debes ver:** HTTP 200 o 503.

Ejemplo de respuesta real:
```
<html><body><h1>503 Service Unavailable</h1> No server is available to handle this request. </body></html>
```

## Verificación · verifier OpenID4VP (una instancia por entidad)

Cada entidad tiene su propia instancia del verifier oficial de EUDI (`/verifier-<entidad>`). Estas pruebas usan la del banco (`{{instancia}}` = `banco`; cámbiala por `eps`, `gym`, `notaria`…). Es la API que usan los portales para pedir datos a la wallet con OpenID4VP + DCQL.

### 1. Usos previstos (intended uses)

Para qué pide datos esta instancia (lo que la wallet muestra al usuario) y su certificado de registro.

```bash
INTENDED_USE_ID=$(curl -sk "$BASE_URL/verifier-$INSTANCIA/ui/intended-uses" | jq -r '.intended_uses[0].intended_use_id')
echo "INTENDED_USE_ID=$INTENDED_USE_ID"
```

**Qué debes ver:** HTTP 200; trae `intended_uses`.

Ejemplo de respuesta real:
```
{"intended_uses":[{"intended_use_id":"1","description":"Identificarte para entrar a tu banca en l��nea o abrir una cuenta","registration_certificate":"eyJ0eXAiOiJyYy13cnArand0IiwiYWxnIjoiRVMyNTYiLCJ4NWMiOlsiTUlJRElqQ0NBc2VnQXdJQkFnSUNFQUV3Q2dZSUtvWkl6ajBFQXdJd …
```

### 2. Claves públicas del verifier

Su certificado de acceso (cadena x5c).

```bash
curl -sk "$BASE_URL/verifier-$INSTANCIA/wallet/public-keys.json" | jq .
```

**Qué debes ver:** HTTP 200; trae `keys`.

Ejemplo de respuesta real:
```
{"keys":[{"kty":"EC","x5t#S256":"8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk","nbf":1792676385,"use":"sig","crv":"P-256","kid":"verifier","x5c":["MIIDIjCCAsegAwIBAgICEAEwCgYIKoZIzj0EAwIwQTELMAkGA1UEBhMCQ08xFDASBgNVBAoMC0NpdmljYSBFVURJMRwwGgYDVQQDDBNDaXZpY2EgUl …
```

### 3. Crear una presentación (DCQL)

Pide el apellido y el nombre de la PID. Devuelve el enlace `haip-vp://…` que la wallet abre.

```bash
TRANSACTION_ID=$(curl -sk -X POST -H "Content-Type: application/json" "$BASE_URL/verifier-$INSTANCIA/ui/presentations/v2" \
  -d '{"dcql_query": {"credentials": [{"id": "pid", "format": "dc+sd-jwt", "meta": {"vct_values": ["urn:eudi:pid:1"]}, "claims": [{"path": ["family_name"]}, {"path": ["given_name"]}]}]}, "nonce": "prueba-1234567890", "profile": "haip", "intended_use_id": "$INTENDED_USE_ID", "request_uri_method": "get"}' | jq -r '.transaction_id')
echo "TRANSACTION_ID=$TRANSACTION_ID"
```

**Qué debes ver:** HTTP 200; trae `transaction_id`; trae `authorization_request_uri`.

Ejemplo de respuesta real:
```
{"transaction_id":"-DRfj2ZiVgEeGq5FjAt4n0V9uRqaR6mLfJ6Gla56PQdtAPV6emdXfOS5qRcQnMashKG7Kl3vFyYxizCf6AR6rA","client_id":"x509_hash:8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk","request_uri":"https://civica-desarrollo.avance.org.co/verifier-banco/wallet/request. …
```

### 4. Resultado antes de que la wallet responda

Mientras la wallet no presente nada, responde 400 `PresentationNotSubmitted`.

> **Paso manual:** abre el enlace de la prueba anterior con la wallet y acepta; entonces esta misma petición devuelve el `vp_token` con los datos.

```bash
curl -sk -w '\nHTTP %{http_code}\n' "$BASE_URL/verifier-$INSTANCIA/ui/presentations/$TRANSACTION_ID"
```

**Qué debes ver:** HTTP 400; `error` = `"PresentationNotSubmitted"`.

Ejemplo de respuesta real:
```
{"error":"PresentationNotSubmitted"}
```

### 5. Eventos de la transacción

Cada paso de la transacción (inicializada, solicitud leída por la wallet, respuesta, fallo…). Sirve para depurar.

```bash
curl -sk "$BASE_URL/verifier-$INSTANCIA/ui/presentations/$TRANSACTION_ID/events" | jq .
```

**Qué debes ver:** HTTP 200; trae `events`.

Ejemplo de respuesta real:
```
{"transaction_id":"-DRfj2ZiVgEeGq5FjAt4n0V9uRqaR6mLfJ6Gla56PQdtAPV6emdXfOS5qRcQnMashKG7Kl3vFyYxizCf6AR6rA","last_updated":1793133193295,"events":[{"timestamp":1793133193287,"event":"Transaction initialized","actor":"Verifier","response":{"transaction_id":"-DRf …
```

### 6. Crear una presentación sin uso previsto: debe rechazar

Sin `intended_use_id` el verifier no puede adjuntar el certificado de registro.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -X POST -H "Content-Type: application/json" "$BASE_URL/verifier-$INSTANCIA/ui/presentations/v2" \
  -d '{"dcql_query": {"credentials": [{"id": "pid", "format": "dc+sd-jwt", "meta": {"vct_values": ["urn:eudi:pid:1"]}, "claims": [{"path": ["family_name"]}]}]}, "nonce": "prueba-1234567890", "profile": "haip", "request_uri_method": "get"}'
```

**Qué debes ver:** HTTP 400; `error` = `"MissingRegistrationCertificate"`.

Ejemplo de respuesta real:
```
{"error":"MissingRegistrationCertificate"}
```

## Verificación · Banco Cívica

Banco sin contraseñas: se entra y se abre la cuenta presentando el PID desde la wallet (nombre y documento). Solo verifica; no emite.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/banco/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/banco/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Crear solicitud de verificación

Pide a la wallet los datos para **entrar** a la banca. Devuelve `id`, el enlace `haip-vp://…` y el QR (data URL).

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/banco/api/solicitud" \
  -d '{"tipo": "ingreso"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"nMDU29ZgUkvxqQICjhqgYQ","enlace":"haip-vp://?client_id=x509_hash%3A8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-banco%2Fwallet%2Frequest.jwt%2FAuVgR85Qb3Q-d6a6ZhvSWGSqUOellKjtE0y_OLnzR8 …
```

### 4. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/banco/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 5. Crear solicitud de apertura de cuenta

Pide los datos para **abrir una cuenta** (más datos que el ingreso).

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/banco/api/solicitud" \
  -d '{"tipo": "registro"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"MGRd2m4c02FMZAwPnuQKrg","enlace":"haip-vp://?client_id=x509_hash%3A8RhaBxDR1GP8N3hYg_HMApT9On5eJT8rzdYUxwGlVuk&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-banco%2Fwallet%2Frequest.jwt%2FTPSNj-T5tF_pvMjnOf9UIp9V_DFCMoLI45G1HcFUVv …
```

### 6. Tipo no válido: debe rechazar

Solo existen `ingreso` y `registro`.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/banco/api/solicitud" \
  -d '{"tipo": "otro"}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"error":"tipo: ingreso o registro"}
```

### 7. ¿Quién soy? (sesión)

Datos de la persona ya identificada en esta sesión del navegador; sin identificarse, responde 401.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/banco/api/yo"
```

**Qué debes ver:** HTTP 200 o 401.

Ejemplo de respuesta real:
```
{"error":"sin sesión"}
```

### 8. Cerrar sesión

Borra la sesión del navegador.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST "$BASE_URL/banco/api/salir"
```

**Qué debes ver:** HTTP 200 o 204.

Ejemplo de respuesta real:
```
{"ok":true}
```

## Verificación · Contratación (empleador)

Portal de empleo: verifica el **nivel de formación** con la credencial académica de la UIS, la identidad con el PID (opcional), genera el contrato en PDF y lo hace firmar desde la wallet (Firmar → Escanear), con sello de tiempo de Andes SCD.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/contratacion/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/contratacion/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Niveles de formación

Los niveles que se pueden verificar (doctorado, maestría, … bachiller).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/contratacion/api/niveles" | jq .
```

**Qué debes ver:** HTTP 200; trae `niveles`.

Ejemplo de respuesta real:
```
{"niveles":[{"clave":"DOCTORADO","nombre":"Doctorado","disponible":true},{"clave":"MAESTRIA","nombre":"Maestría","disponible":true},{"clave":"ESPECIALIZACION","nombre":"Especialización","disponible":true},{"clave":"PREGRADO","nombre":"Pregrado","disponible":tr …
```

### 4. Crear solicitud de verificación

Pide la credencial académica de nivel pregrado; la wallet solo ofrece las credenciales que cumplen el filtro.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/contratacion/api/solicitud" \
  -d '{"nivel": "PREGRADO", "paso": "formacion"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"Uc0i_EOBNYJgy3G1C_OKow","enlace":"haip-vp://?client_id=x509_hash%3Ak_nlVVaM834iPD0fJbvNblenJl-guLTvdIelrtyg35c&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-contratacion%2Fwallet%2Frequest.jwt%2FjLUsM_5ENnD7TgtO18zsFFsvBuGgcdcAIKR …
```

### 5. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/contratacion/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 6. Nivel no disponible: debe rechazar

Un nivel que no existe se rechaza.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/contratacion/api/solicitud" \
  -d '{"nivel": "NIVEL-INVENTADO", "paso": "formacion"}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"error":"nivel no disponible"}
```

### 7. Generar el contrato sin verificar antes: debe rechazar

No se puede generar el contrato si la formación y la identidad no están verificadas.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/contratacion/api/contrato" \
  -d '{"previa": "inexistente", "cargo": "Analista de datos"}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"error":"Primero hay que verificar la formación y confirmar la identidad."}
```

### 8. Estado de la firma (id desconocido)

Consulta el estado de una firma de contrato; con un id que no existe, 404.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/contratacion/api/firma/inexistente"
```

**Qué debes ver:** HTTP 404.

Ejemplo de respuesta real:
```
{"estado":"rechazada","motivo":"Solicitud desconocida."}
```

## Verificación · Discoteca Neón (mayoría de edad)

Control de edad en la puerta: pide a la wallet solo «mayor de 18» (sin nombre ni documento) y **no guarda nada**, solo contadores.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/discoteca/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/discoteca/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Crear solicitud de verificación

Pide únicamente la prueba de mayoría de edad del PID.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/discoteca/api/solicitud" \
  -d '{}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"18EnpzNaybqOnvYTL-dWFg","enlace":"haip-vp://?client_id=x509_hash%3AOPk-1ALYxHoPxGY56MA9Jjniy-UcBJAnXl5HsFOFMT4&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-discoteca%2Fwallet%2Frequest.jwt%2FjrClRovwsYYNF7FnODJHCfHxxTWPZ3S-kNavQm …
```

### 4. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/discoteca/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 5. Contadores de la noche

Cuántas entradas y rechazos van, sin ningún dato personal.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/discoteca/api/noche" | jq .
```

**Qué debes ver:** HTTP 200; trae `entradas`; trae `rechazos`.

Ejemplo de respuesta real:
```
{"entradas":0,"rechazos":0,"desde":"2026-10-27T19:23:09.125Z"}
```

## Verificación · Instituto Meridiano (inscripción y becas)

Pide en una sola presentación el PID y la credencial académica, y evalúa si la persona cumple los requisitos de cada convocatoria.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/beca/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/beca/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Convocatorias

Inscripciones y becas disponibles con sus requisitos (nivel, estado, promedio mínimo…).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/beca/api/convocatorias" | jq .
```

**Qué debes ver:** HTTP 200; trae `convocatorias`.

Ejemplo de respuesta real:
```
{"convocatorias":[{"clave":"inscripcion-maestria","tipo":"Inscripción","nombre":"Inscripción a maestría","descripcion":"Para personas graduadas de pregrado que quieren inscribirse a una maestría.","nivel":"PREGRADO","estado":"GRADUADO","promedio_min":3,"requis …
```

### 4. Crear solicitud de verificación

Pide el PID y la credencial académica para pre-evaluar la inscripción a maestría.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/beca/api/solicitud" \
  -d '{"convocatoria": "inscripcion-maestria", "paso": "preevaluacion"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"DCS9d8lY6TmDmcY6T8alQQ","enlace":"haip-vp://?client_id=x509_hash%3ASq5-sMQtrgBxqvM7xZ9vLJaHwftBsBXVYwgf6hBjAeo&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-beca%2Fwallet%2Frequest.jwt%2FheobB77C6Z1vblFjcy7Sv2_R4xt4KjDyKx2-YGan_hN …
```

### 5. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/beca/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

## Verificación · Edificio Cumbre (control de acceso)

Pide la foto, el nombre y el documento del PID para que un guardia decida el ingreso, con estilo de centro de control.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/acceso/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/acceso/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Crear solicitud de verificación

Pide foto, nombre y documento del PID.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/acceso/api/solicitud" \
  -d '{}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"jHKzpqDEVEXeINfw9vvSpQ","enlace":"haip-vp://?client_id=x509_hash%3ACxc-N5PPyhXGidwJwjh-WW4ZUq5ZR75bn1Fu364HxbQ&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-acceso%2Fwallet%2Frequest.jwt%2FoM8dyQzs73-OlW25yoEUiAzDmQsy6TCyoWu9y9kVv …
```

### 4. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/acceso/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 5. Decisión del guardia sobre una solicitud inexistente

El guardia permite o niega; con un id que no existe se rechaza.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/acceso/api/decision" \
  -d '{"id": "inexistente", "decision": "permitir"}'
```

**Qué debes ver:** HTTP 400 o 404 o 409.

Ejemplo de respuesta real:
```
{"error":"No hay un ingreso pendiente de decidir."}
```

### 6. Registro de decisiones

Quién entró y quién no (datos mínimos).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/acceso/api/registro" | jq .
```

**Qué debes ver:** HTTP 200; trae `registro`.

Ejemplo de respuesta real:
```
{"registro":[]}
```

## Verificación · Fondo de Pensiones Horizonte (prueba de supervivencia)

La persona prueba que está viva presentando el PID y su **fe de vida notarial**; el fondo cruza las dos.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/pensiones/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/pensiones/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Crear solicitud de verificación

Pide el PID y la fe de vida de la notaría.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/pensiones/api/solicitud" \
  -d '{}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"4ne0DK-vaSey0Ii8naXcSg","enlace":"haip-vp://?client_id=x509_hash%3AA_85U8lycVvw4JQa57jsqPuyLiTkVzuRALLdhqr12s8&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-pensiones%2Fwallet%2Frequest.jwt%2FZHkON7iFzdW3nnSP5s-pyXuLuKceSyV59-98zI …
```

### 4. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/pensiones/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 5. Personas acreditadas

Quién ya probó supervivencia.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/pensiones/api/acreditados" | jq .
```

**Qué debes ver:** HTTP 200; trae `acreditados`.

Ejemplo de respuesta real:
```
{"acreditados":[]}
```

## Verificación · Migración Cívica (control migratorio)

Pide el PID y el pasaporte digital (Cancillería), cruza las comprobaciones y el funcionario decide la entrada o la salida.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/migracion/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/migracion/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Movimiento no indicado: debe rechazar

Hay que decir si es `entrada` o `salida`.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/migracion/api/solicitud" \
  -d '{}'
```

**Qué debes ver:** HTTP 400; `error` = `"movimiento: entrada o salida"`.

Ejemplo de respuesta real:
```
{"error":"movimiento: entrada o salida"}
```

### 4. Crear solicitud de verificación

Pide PID + pasaporte para una **entrada** al país.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/migracion/api/solicitud" \
  -d '{"movimiento": "entrada"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"hNgDCc6BARbIN9_hmvZRZA","enlace":"haip-vp://?client_id=x509_hash%3As8ibb9E4qgSCoDgSr_3uqlmg5-vWCVxu3Uo9YdVDtYc&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-migracion%2Fwallet%2Frequest.jwt%2FLgw4o77tlSkjvUVRl1LnKzyyU9TU2z5qATqifN …
```

### 5. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/migracion/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 6. Decisión sobre una solicitud inexistente

El funcionario autoriza o niega; con un id que no existe se rechaza.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/migracion/api/decision" \
  -d '{"id": "inexistente", "decision": "permitir"}'
```

**Qué debes ver:** HTTP 400 o 404 o 409.

Ejemplo de respuesta real:
```
{"error":"No hay un control pendiente de decidir."}
```

### 7. Registro de movimientos

Entradas y salidas decididas.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/migracion/api/registro" | jq .
```

**Qué debes ver:** HTTP 200; trae `registro`.

Ejemplo de respuesta real:
```
{"registro":[]}
```

## Emisor+Verifier · EPS Salud Cívica (carné e historia clínica)

Emite el **carné de afiliado** y el **resumen de historia clínica** (QEAA, SD-JWT), cada dato divulgable por separado; verifica a farmacias y médicos solo lo necesario. Tiene portal de afiliados, de médicos y de administración (`ADMIN_CLAVE`).

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/eps/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/eps/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Crear solicitud de verificación

El afiliado se identifica con su PID para entrar a su portal.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/eps/api/solicitud" \
  -d '{"proposito": "identificacion"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"mZ3PQxeBmZId88-8M7ZR7g","enlace":"haip-vp://?client_id=x509_hash%3ADFIsB_v7uJcR3Vrc1byBwz76ehmYG2IQIfivc4tD_Hg&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-eps%2Fwallet%2Frequest.jwt%2FcYzbuN1Fhc35sXtyWjMAfY1YvF7HUUZib0BYCd0Msgqv …
```

### 4. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/eps/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 5. Propósito no válido: debe rechazar

Los propósitos válidos son identificacion, medico, carne, historia o convivencia.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/eps/api/solicitud" \
  -d '{}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"error":"proposito: identificacion, medico, carne, historia o convivencia"}
```

### 6. Administración · entrar

Entra a la administración con la clave del administrador.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/eps/api/admin/entrar" \
  -d '{"clave": "$EPS_CLAVE"}'
```

**Qué debes ver:** HTTP 200 o 204.

### 7. Administración · médicos registrados

Los médicos autorizados a consultar historias (requiere haber entrado).

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/eps/api/admin/medicos"
```

**Qué debes ver:** HTTP 200 o 401.

### 8. Administración · salir

Cierra la sesión de administración.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST "$BASE_URL/eps/api/admin/salir"
```

**Qué debes ver:** HTTP 200 o 204.

Ejemplo de respuesta real:
```
{"ok":true}
```

### 9. Metadatos del emisor (OpenID4VCI)

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.eps.carne_afiliado`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/.well-known/openid-credential-issuer/eps" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/eps","authorization_servers":["https://civica-desarrollo.avance.org.co/eps"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/eps/credencial","nonce_endpoint":"https://civica-desarroll …
```

### 10. Nonce del emisor

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

```bash
curl -sk -c "$JAR" -b "$JAR" -X POST "$BASE_URL/eps/nonce" | jq .
```

**Qué debes ver:** HTTP 200; trae `c_nonce`.

Ejemplo de respuesta real:
```
{"c_nonce":"2raaPMtVmukuugrvdXRDQYDTi4TQAJu_"}
```

### 11. Lista de estado de las credenciales (revocación)

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/eps/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUNBRENDQWFhZ0F3SUJBZ0lVQVJ3UDh2NXp5azZKMHpBTmNyM0pvdDRXbE1Vd0NnWUlLb1pJemowRUF3SXdTekVMTUFrR0ExVUVCaE1DUTA4eEdUQVhCZ05WQkFvTUVFVlFVeUJUWVd4MVpDQkJkbUZ1WTJVeElUQWZCZ05WQkFNTUdFVlFVeUJUWVd4MVpDQkJk …
```

## Emisor+Verifier · Notaría Avance (9 documentos notariales)

Verifica el PID y emite nueve documentos (PuB-EAA): declaración extraproceso, poder especial, reconocimiento de documento, **fe de vida**, autenticación de firma, copia de escritura, registro civil, convivencia y permiso de salida de menor.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Crear solicitud de verificación

La persona se identifica con su PID para hacer un trámite.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/solicitud" \
  -d '{"proposito": "identificacion"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"61CXJ0sFS1r59gjjRjyMvQ","enlace":"haip-vp://?client_id=x509_hash%3ArD4Q4wbJQjBneS3WFYY9wWWAh9z5WK_OzWr0GPq1sfA&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-notaria%2Fwallet%2Frequest.jwt%2FYSn8lHrOhFxr5BF-TMPlcqLoOlPqG2KgBEp-6VU9 …
```

### 4. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 5. Propósito no válido: debe rechazar

Hay que decir `identificacion` o `verificacion` con un tipo de documento.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/notaria/api/solicitud" \
  -d '{}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"error":"proposito: identificacion, o verificacion con un tipo de documento"}
```

### 6. Trámites (sin identificarse)

Los trámites de la persona; antes de identificarse con la wallet responde 401.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/notaria/api/tramites"
```

**Qué debes ver:** HTTP 401; `error` = `"Primero identifícate con tu wallet."`.

Ejemplo de respuesta real:
```
{"error":"Primero identifícate con tu wallet."}
```

### 7. Metadatos del emisor (OpenID4VCI)

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.notaria.fe_de_vida`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/.well-known/openid-credential-issuer/notaria" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/notaria","authorization_servers":["https://civica-desarrollo.avance.org.co/notaria"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/notaria/credencial","nonce_endpoint":"https://civi …
```

### 8. Nonce del emisor

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

```bash
curl -sk -c "$JAR" -b "$JAR" -X POST "$BASE_URL/notaria/nonce" | jq .
```

**Qué debes ver:** HTTP 200; trae `c_nonce`.

Ejemplo de respuesta real:
```
{"c_nonce":"2No2LF-T49dRvjcL8tFW0Au6AO33AtUv"}
```

### 9. Lista de estado de las credenciales (revocación)

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/notaria/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUIvVENDQWFLZ0F3SUJBZ0lVWFBqWG5pUUtXTGh6elcyajZPdlFNRVArU053d0NnWUlLb1pJemowRUF3SXdTVEVMTUFrR0ExVUVCaE1DUTA4eEdEQVdCZ05WQkFvTUQwNXZkR0Z5dzYxaElFRjJZVzVqWlRFZ01CNEdBMVVFQXd3WFRtOTBZWExEcldFZ1FYWmhi …
```

## Emisor+Verifier · Cumbre Gym (tarjeta de socio, ingreso y egreso)

Emite la **tarjeta de socio** (QEAA, con foto) y controla el ingreso y egreso de sus sedes (anti-passback y aforo). Las pantallas de la puerta piden la clave de puerta (= `ADMIN_CLAVE`).

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/gym/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok","aforo_maximo":60}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/gym/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Crear solicitud de verificación

La persona se identifica con su PID para afiliarse o ver su portal.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/gym/api/solicitud" \
  -d '{"proposito": "identificacion"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"m8o8U3NyYeRAWBinsBkiFA","enlace":"haip-vp://?client_id=x509_hash%3As7LWKrzlxlNQHWANY44P8Sgwo9TybBoUUkxb7gGwdKM&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-gym%2Fwallet%2Frequest.jwt%2FnZL5TonFegulwIMdjWDMTgYVlmV_dj2pO4irxT8haagK …
```

### 4. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/gym/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 5. Propósito no válido: debe rechazar

Hay que decir `identificacion` o `acceso`.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/gym/api/solicitud" \
  -d '{}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"error":"proposito: identificacion o acceso"}
```

### 6. Puerta · clave incorrecta: debe rechazar

Prueba de seguridad: una clave equivocada no abre la pantalla de la puerta.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/gym/api/puerta/entrar" \
  -d '{"clave": "clave-incorrecta"}'
```

**Qué debes ver:** HTTP 401 o 403 o 429.

Ejemplo de respuesta real:
```
{"error":"Contraseña incorrecta."}
```

### 7. Puerta · entrar con la clave

Habilita esta sesión para las pantallas de entrada y salida.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/gym/api/puerta/entrar" \
  -d '{"clave": "$GYM_CLAVE"}'
```

**Qué debes ver:** HTTP 200 o 204.

### 8. Puerta · aforo en vivo

Cuánta gente hay dentro en cada sede.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/gym/api/puerta/aforo"
```

**Qué debes ver:** HTTP 200 o 401.

### 9. Administración · resumen

Aforo, socios y últimos accesos (requiere entrar como administrador).

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/gym/api/admin/resumen"
```

**Qué debes ver:** HTTP 200 o 401.

### 10. Metadatos del emisor (OpenID4VCI)

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.gym.tarjeta_socio`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/.well-known/openid-credential-issuer/gym" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/gym","authorization_servers":["https://civica-desarrollo.avance.org.co/gym"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/gym/credencial","nonce_endpoint":"https://civica-desarroll …
```

### 11. Nonce del emisor

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

```bash
curl -sk -c "$JAR" -b "$JAR" -X POST "$BASE_URL/gym/nonce" | jq .
```

**Qué debes ver:** HTTP 200; trae `c_nonce`.

Ejemplo de respuesta real:
```
{"c_nonce":"Y42Ga80oWsUveCpK2wnaQZP1N0sz-A-j"}
```

### 12. Lista de estado de las credenciales (revocación)

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/gym/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUI1ekNDQVk2Z0F3SUJBZ0lVS1hUUmZQblNaQ0Jkd05wVGJHZ0RNM3RhaTQ0d0NnWUlLb1pJemowRUF3SXdQekVMTUFrR0ExVUVCaE1DUTA4eEV6QVJCZ05WQkFvTUNrTjFiV0p5WlNCSGVXMHhHekFaQmdOVkJBTU1Fa04xYldKeVpTQkhlVzBnVW05dmRDQkRR …
```

## Emisor+Verifier · Páramo Air (tiquetes)

Vende pasajes: verifica nombre, documento, nacimiento y foto del PID, **emite el tiquete** (QEAA) y lo verifica en el embarque.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/aerolinea/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/aerolinea/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Destinos

Ciudades a las que vuela.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/aerolinea/api/destinos" | jq .
```

**Qué debes ver:** HTTP 200; trae `ciudades`.

Ejemplo de respuesta real:
```
{"ciudades":[{"codigo":"BGA","nombre":"Bucaramanga","pais":"CO"},{"codigo":"BOG","nombre":"Bogotá","pais":"CO"},{"codigo":"MDE","nombre":"Medellín","pais":"CO"},{"codigo":"CLO","nombre":"Cali","pais":"CO"},{"codigo":"CTG","nombre":"Cartagena","pais":"CO"},{"co …
```

### 4. Vuelos disponibles

Vuelos de Bogotá a Medellín en la fecha indicada.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/aerolinea/api/vuelos?origen=BOG&destino=MDE&fecha=$FECHA_VUELO" | jq .
```

**Qué debes ver:** HTTP 200.

Ejemplo de respuesta real:
```
{"vuelos":[{"vuelo":"PMO 1273","origen":"BOG","destino":"MDE","fecha":"2026-11-26","salida":"06:00","llegada":"06:55","duracion_min":55,"base":149000,"internacional":false,"tarifas":{"basica":{"tarifa":149000,"tasas":46000,"total":195000,"nombre":"Básica","equ …
```

### 5. Mismo origen y destino: debe rechazar

Hay que elegir origen y destino distintos.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/aerolinea/api/vuelos?origen=BOG&destino=BOG&fecha=$FECHA_VUELO"
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"error":"Elige un origen y un destino distintos."}
```

### 6. Crear solicitud de verificación

La persona se identifica con su PID para comprar.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/aerolinea/api/solicitud" \
  -d '{"proposito": "identificacion"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"ABnK9GjHM7wO4xGm9KtbJw","enlace":"haip-vp://?client_id=x509_hash%3AjGIiQfdrGknqRjd7rhxIbJtMDr9tobmV2tXdFEuHwL4&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-aerolinea%2Fwallet%2Frequest.jwt%2F6r1DAgS4chaCvplsmHRt62h8GmphBZ23RFvqoP …
```

### 7. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/aerolinea/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 8. Metadatos del emisor (OpenID4VCI)

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.aerolinea.tiquete`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/.well-known/openid-credential-issuer/aerolinea" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/aerolinea","authorization_servers":["https://civica-desarrollo.avance.org.co/aerolinea"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/aerolinea/credencial","nonce_endpoint":"https: …
```

### 9. Nonce del emisor

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

```bash
curl -sk -c "$JAR" -b "$JAR" -X POST "$BASE_URL/aerolinea/nonce" | jq .
```

**Qué debes ver:** HTTP 200; trae `c_nonce`.

Ejemplo de respuesta real:
```
{"c_nonce":"hObRsC3UERGy83p2zy55oaghGc1Dhv3_"}
```

### 10. Lista de estado de las credenciales (revocación)

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/aerolinea/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUI3RENDQVpLZ0F3SUJBZ0lVUkpCTzJyZkFJOWt0TzUrZS9OT0tVcWdweXVJd0NnWUlLb1pJemowRUF3SXdRVEVMTUFrR0ExVUVCaE1DUTA4eEZEQVNCZ05WQkFvTUMxRERvWEpoYlc4Z1FXbHlNUnd3R2dZRFZRUUREQk5RdzZGeVlXMXZJRUZwY2lCU2IyOTBJ …
```

## Emisor+Verifier · Cancillería (pasaporte digital)

Solicitud, entrega única, reexpedición y verificación del **pasaporte digital** (PuB-EAA). Un pasaporte ya entregado solo se vuelve a emitir con reexpedición.

### 1. Salud del servicio

Comprobación rápida de que el portal está en pie.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/cancilleria/salud" | jq .
```

**Qué debes ver:** HTTP 200; `estado` = `"ok"`.

Ejemplo de respuesta real:
```
{"estado":"ok"}
```

### 2. Portal (página principal)

La página que ve la persona en el navegador.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/cancilleria/" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una página.

### 3. Crear solicitud de verificación

La persona se identifica con su PID para solicitar el pasaporte.

```bash
SOLICITUD_ID=$(curl -sk -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/cancilleria/api/solicitud" \
  -d '{"proposito": "identificacion"}' | jq -r '.id')
echo "SOLICITUD_ID=$SOLICITUD_ID"
```

**Qué debes ver:** HTTP 200; trae `id`; trae `enlace`.

Ejemplo de respuesta real:
```
{"id":"us-b_xuGCv8VD5qs1RIMbA","enlace":"haip-vp://?client_id=x509_hash%3A4iJdG-UXuLdC0swupoYzvKpQVV51W3AZO6jnlVXk74A&request_uri=https%3A%2F%2Fcivica-desarrollo.avance.org.co%2Fverifier-cancilleria%2Fwallet%2Frequest.jwt%2F2R4R4B9Fs7FZseZ2u0ZOAD3xfPizCArJlRBq …
```

### 4. Consultar el resultado de la solicitud

El navegador de la persona pregunta cada par de segundos si la wallet ya respondió. Mientras no haya respuesta dice que está esperando; cuando la wallet presenta la credencial, entrega los datos ya verificados.

> **Paso manual:** abre el enlace `haip-vp://…` de la prueba anterior con la wallet (o escanea el QR del portal) y acepta compartir los datos. Sin ese paso, la respuesta es "esperando".

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/cancilleria/api/solicitud/$SOLICITUD_ID" | jq .
```

**Qué debes ver:** HTTP 200; trae `estado`.

Ejemplo de respuesta real:
```
{"estado":"esperando"}
```

### 5. Propósito no válido: debe rechazar

Hay que decir `identificacion` o `verificacion` con un tipo.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" -X POST -H "Content-Type: application/json" "$BASE_URL/cancilleria/api/solicitud" \
  -d '{}'
```

**Qué debes ver:** HTTP 400.

Ejemplo de respuesta real:
```
{"error":"proposito: identificacion, o verificacion con un tipo de documento"}
```

### 6. Trámites (sin identificarse)

Antes de identificarse con la wallet responde 401.

```bash
curl -sk -w '\nHTTP %{http_code}\n' -c "$JAR" -b "$JAR" "$BASE_URL/cancilleria/api/tramites"
```

**Qué debes ver:** HTTP 401.

Ejemplo de respuesta real:
```
{"error":"Primero identifícate con tu wallet."}
```

### 7. Metadatos del emisor (OpenID4VCI)

Lo primero que lee la wallet de un emisor: qué credenciales ofrece (`co.cancilleria.pasaporte`), su formato y sus datos. Si esto falla, la wallet no puede ni empezar a recibir credenciales.

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/.well-known/openid-credential-issuer/cancilleria" | jq .
```

**Qué debes ver:** HTTP 200; trae `credential_issuer`; trae `credential_configurations_supported`.

Ejemplo de respuesta real:
```
{"credential_issuer":"https://civica-desarrollo.avance.org.co/cancilleria","authorization_servers":["https://civica-desarrollo.avance.org.co/cancilleria"],"credential_endpoint":"https://civica-desarrollo.avance.org.co/cancilleria/credencial","nonce_endpoint":" …
```

### 8. Nonce del emisor

La wallet pide un nonce de un solo uso para firmar su prueba de posesión de la clave al pedir la credencial.

```bash
curl -sk -c "$JAR" -b "$JAR" -X POST "$BASE_URL/cancilleria/nonce" | jq .
```

**Qué debes ver:** HTTP 200; trae `c_nonce`.

Ejemplo de respuesta real:
```
{"c_nonce":"AeivuE-ud-sJXAieoZkplSDrjzExpx9S"}
```

### 9. Lista de estado de las credenciales (revocación)

Lista de estado firmada (JWT `statuslist+jwt`) con un bit por credencial emitida: 1 = revocada. La consultan los verifiers para saber si una credencial sigue vigente (se renueva cada 10 min).

```bash
curl -sk -c "$JAR" -b "$JAR" "$BASE_URL/cancilleria/estado/1" | head -c 300; echo
```

**Qué debes ver:** HTTP 200; devuelve una lista firmada (JWT).

Ejemplo de respuesta real:
```
eyJhbGciOiJFUzI1NiIsInR5cCI6InN0YXR1c2xpc3Qrand0IiwieDVjIjpbIk1JSUNlVENDQWlDZ0F3SUJBZ0lVWFY1Y05qcXpmWUNjbWpmeC9tVnlmUHZqcnRvd0NnWUlLb1pJemowRUF3SXdnWWN4Q3pBSkJnTlZCQVlUQWtOUE1UY3dOUVlEVlFRS0RDNU5hVzVwYzNSbGNtbHZJR1JsSUZKbGJHRmphVzl1WlhNZ1JYaDBaWEpwYjNKbGN5Qmta …
```
