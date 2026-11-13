# Fallo de emisión de la PID en la app V2 — diagnóstico y arreglo

**Fecha:** 2026-10-21 · **Síntoma:** en `co.org.newwallet.app.dev.v2` (app nueva), pulsar
"Continuar con la emisión" en la pantalla *Solicitud de emisión* devolvía

> No pudimos crear tu solicitud: Algo salió mal al emitir tu(s) documento(s)

**Estado:** corregido y verificado end‑to‑end el 2026‑10‑21 08:54 (MSO mDoc + SD‑JWT VC
de la Registraduría emitidos, con los datos reales) y el 2026‑10‑21 09:43 (credenciales
académicas del UIS: *Ingeniería de Sistemas* y *Especialización en Desarrollo de
Software*).

---

## 1. Por qué la app no decía nada útil

`WalletCoreDocumentsController.kt` colapsaba **todos** los fallos de emisión al mismo
mensaje genérico: en `IssueEvent.Failure` se leía el `event.cause` para clasificar el
motivo de confianza, pero **se descartaba** antes de construir el mensaje. Un rechazo de
autenticación del emisor, un error de red y un problema de confianza eran indistinguibles
en el móvil.

Ahora el `cause` se registra y la pantalla distingue la fase (crear la solicitud vs.
emitir) y el motivo de confianza (certificado de acceso vs. de registro).

## 2. Los cuatro bloqueos, en orden de aparición

### 2.1 El `client_id` no coincidía con el `sub` de la WIA  →  HTTP 401

El Wallet Provider que hay en pie es el **Spartans**, cuya WIA se firma con
`sub = eudiw-abca`. La app, en cambio, se autenticaba en Keycloak como
`client_id = newwallet-app` (`NW_CLIENT_ID`).

El autenticador `abca-draft07` de Keycloak **busca el cliente OAuth por el `sub` de la
WIA** (`getClientByClientId`), de modo que `eudiw-abca` no existía como cliente y la
petición moría en el PAR:

```
type="PUSHED_AUTHORIZATION_REQUEST_ERROR" error="client_not_found"
type="PUSHED_AUTHORIZATION_REQUEST_ERROR" error="invalid_request" detail="Authentication failed."
```

**Arreglo:** cliente `eudiw-abca` en el realm `pid-registraduria-realm`
(`clientAuthenticatorType: abca-draft07`, mismos redirectUris y scopes que
`newwallet-app`) y la app compilada con `-Pnw.client.id=eudiw-abca`.

### 2.2 Keycloak no validaba la cadena de la WIA  →  confianza no comprobada

El cliente tenía `trustValidator.serviceUrl` **vacío**: el validador de confianza quedaba
en modo *ignored*, es decir, **la WIA se aceptaba sin validar su cadena** (incumplimiento
de `ISSU_19` / `WPNot_04`). Y aunque se configurase, `keycloak-reg` estaba solo en la red
`registraduria_default`, mientras que el `trust-validator` está en `avance-eudi`: no se
resolvía el nombre.

**Arreglo:** `trustValidator.serviceUrl = http://civica-trust-validator:8080/trust` y
`keycloak-reg` unido a la red `avance-eudi`. Verificado: sin esa red el PAR vuelve a dar
401; con ella, 201 — el validador **sí** se consulta y la cadena del WP se verifica contra
la Wallet Provider LoTE (`trusted: true`).

### 2.3 La introspección del token fallaba el TLS  →  401 en el credentialEndpoint

`ISSUER_AUTHORIZATIONSERVER_INTROSPECTION` apuntaba a `https://keycloak:8443`, cuyo
certificado firma la *Registraduria Dev Local CA*, **que no está en el truststore del
emisor** (`/issuer-keys.jks`). El handshake fallaba, el emisor no podía validar el token y
el `credentialEndpoint` respondía 401 sin cuerpo.

**Arreglo:** la introspección va por `http://keycloak:8080` (salto entre contenedores de
la misma red, como el del trust‑validator).

### 2.4 El `htu` del DPoP se comparaba contra la URL interna  →  401 en el credentialEndpoint

El emisor valida el `htu` del DPoP contra la URI de la petición. La app manda su **URL
pública** (`https://…/pid-registraduria/wallet/credentialEndpoint`); el emisor la
reconstruía como `http://…` porque:

- `haproxy` tenía `option forwarded … for`, que **sobrescribe** el `X-Forwarded-Proto` de
  nginx con el suyo (http, porque nginx le habla en claro), y
- el emisor no tenía `server.forward-headers-strategy`, así que Spring ignoraba los
  headers de proxy.

Traza del propio emisor que lo dejó claro:

```
uri=http://civica-desarrollo.avance.org.co/pid-registraduria/wallet/credentialEndpoint
proto=http  fwd=null
```

**Arreglo:** tres cosas juntas, y las tres hacen falta:

1. `nginx` propaga el esquema original del cliente (`$http_x_forwarded_proto`, con
   `$scheme` como reserva) y anuncia **https** en las rutas del emisor, cuya URL pública lo
   es.
2. `haproxy` **no** sobrescribe los `X-Forwarded-*` en el backend del emisor y los
   reenvía tal cual (`option forwarded` se los come).
3. El emisor arranca con `SERVER_FORWARD_HEADERS_STRATEGY=native`, para que Spring
   reconstruya la URI con el esquema y el host públicos.

Con eso el emisor ya calcula la URL pública y el `htu` coincide:

```
uri=https://civica-desarrollo.avance.org.co/pid-registraduria/wallet/credentialEndpoint
proto=https  fwd=https
```

## 3. Estado de la confianza (lo que sí está bien)

Las listas de confianza **estaban al día**: las cinco LoTEs publicadas por
`srv-tl-publisher` son de 2026‑10‑20 19:49Z (seq 4‑6, v6) y sus 14 anclas están **bit a
bit** en el keystore `anclas-confianza.jks` del `trust-validator` (19 entradas, regenerado
el mismo día). Ambos salen del mismo `srv-tl-publisher/pki/publico/ca/`, así que no pueden
discrepar.

| Tramo | Estado |
|---|---|
| Wallet → Wallet Provider (WIA) | ✅ la WIA del Spartans encadena a `Spartans Wallet Provider Root CA`, publicada en la TL/WP |
| WP → trust‑validator | ✅ `{"trusted": true}`, ancla `CN=Spartans Wallet Provider Root CA` |
| Wallet Provider → Keycloak (ABCA) | ✅ tras registrar `eudiw-abca` y conectar la red |
| Keycloak → trust‑validator | ✅ valida la cadena de la WIA |
| Issuer → trust‑validator | ✅ (`ISSUER_TRUST_SERVICEURL`, el issuer sí está en `avance-eudi`) |
| Wallet → Issuer (metadata firmada) | ⚠️ la app **no** la valida: `INFORM` + `ignoreSignedMetadata()` |

## 3 bis. El issuer de la UIS: los mismos bloqueos, y uno propio

El UIS fallaba igual, con **cuatro** bloqueos, uno de ellos exclusivo de este issuer:

| # | Bloqueo | Síntoma | Arreglo |
|---|---|---|---|
| 1 | `client_id=wallet-dev` (cliente `client-secret`, sin mapper) frente a la WIA de `eudiw-abca` | el token salía sin el claim `client_status` → **500** `Unexpected client_status claim type 'null'` | la app usa `BuildConfig.NW_CLIENT_ID` también para el UIS |
| 2 | el issuer UIS no estaba en la red `avance-eudi` | no alcanzaba el `trust-validator` | `uis-issuer` unido a `avance-eudi` |
| 3 | `trustValidator.serviceUrl` vacío en el cliente `eudiw-abca` del realm `uis-issuer-realm` | la WIA se aceptaba sin validar | configurado |
| 4 | el `htu` del DPoP contra la URL interna | **401** `JWT htu claim value rejected` | cabecera `Forwarded:` (ver 2.4) |

### La confianza del UIS, mitad y mitad

| Elemento | Estado |
|---|---|
| Firma de la credencial del UIS | ✅ `UIS Issuer PID Document Signer` → `UIS Issuer PID Root CA` (ancla en la TL/PID) — `trusted: true` |
| Firma de la credencial académica | ✅ `UIS Issuer Document Signer` → `UIS Issuer Root CA` (ancla en la TL/PUBEAA) — `trusted: true` |
| **Metadata firmada** del UIS | ❌ la firma un cert **autofirmado del upstream** (`C=GR, CN=pid-issuer`), que no encadena con ninguna ancla |
| **Certificado de registro** del UIS | ❌ `CN=test` autofirmado, y con los payloads del upstream (`registry.example.eu`, `issuer.eudiw.dev/token_status_list`) |

Es decir: el UIS **sí tiene PKI propia** (y está publicada en las LoTEs) para firmar las
credenciales, pero firma la metadata y el certificado de registro con los certificados de
ejemplo del upstream. Es el mismo hueco que la Registraduría, y también lo es de la PKI del
emisor, no de la app.

### El `htu` del DPoP: la cabecera que sí funciona

Con `server.forward-headers-strategy=framework` (el valor de los dos `application.properties`),
Spring reconstruye la URI de la petición con la cabecera **`Forwarded:` (RFC 7239)**, no con
`X-Forwarded-Proto`. Comprobado: con solo `X-Forwarded-Proto` el emisor calculaba `http://` y
con `Forwarded: proto=https;host=…` calculaba `https://`. Por eso nginx envía **las dos** y
haproxy las reenvía sin tocarlas.

## 4. Lo que queda pendiente (no bloquea la emisión)

1. **La app no valida la confianza del emisor.** En `WalletCoreConfigImpl` (dev y demo)
   `configureIssuerTrust` sigue con `policy { default(INFORM) }` e
   `ignoreSignedMetadata()`. La LoTE se descarga y su resultado se descarta. Subir a
   `ENFORCE` + `requireSignedMetadata()` **no es posible todavía**, porque:
   - la metadata de la Registraduría la firma un cert **autofirmado** (`CN=pid-issuer`)
     que no encadena con ninguna ancla de la TL/PID, y
   - el cert de registro (`CN=REGISTRADURIA PID ISSUANCE`) necesita el intermedio
     `REGISTRADURIA ISSUANCE INTERMEDIATE`, que no se publica en ningún sitio.
   Ese es el hueco de la PKI de la Registraduría, no de la app.
2. **La metadata de Keycloak publica el PAR y el token en `http://`.** Con el backchannel
   dinámico (que es lo correcto: el endpoint publicado tiene que ser el que valida el
   `htu`) el esquema que ve Keycloak es el del salto interno. La WIA viaja en claro en el
   tramo WAN entre el proxy público y esta máquina: es una propiedad del despliegue de
   desarrollo, no de la app.
3. **`NW_TL_WRPRC = null`.** No hay lista de certificados de REGISTRO de partes usuarias,
   así que la wallet no puede validar su propio registro.
4. **`wp-new-wallet-provider-root-ca-e2728070` no se publica** en la TL/WP: está en el
   keystore del validador pero no en `anclas.json`.
5. **El usuario `91296304` (realm de la Registraduría) y `georincon` (realm del UIS) no
   tenían ninguna credencial** en Keycloak (el canje por `issuer_state` los autoidentifica,
   así que la app nunca hizo login con contraseña). Se les ha puesto la contraseña `asdfgh`
   y el login manual ya funciona en los dos realms.

## 5. Cambios aplicados

### App (`app-new-wallet`)

| Fichero | Cambio |
|---|---|
| `core-logic/.../WalletCoreDocumentsController.kt` | se registra el `cause` real del fallo de emisión; se añade `LogController` |
| `core-logic/.../LogicCoreModule.kt` | se inyecta el `LogController` en el controlador |
| `core-logic/.../ThrowableExtensions.kt` | nuevo `Throwable.describeCause()`: aplana la cadena de excepciones en una línea |
| `core-logic/.../WalletCoreConfigImpl.kt` (dev y demo) | `configureIssuerRegistrationPolicy` sigue el mismo interruptor que la app (evita el falso rechazo por `NotEvaluated`) |
| `core-logic/.../WalletCoreConfigImpl.kt` (dev) | el issuer del UIS usa `BuildConfig.NW_CLIENT_ID` en vez del `wallet-dev` hardcodeado |
| `onboarding-feature/.../PidIssuanceRequestScreen.kt` | distingue fase y motivo de confianza; deja de tragarse la causa |

La app se compila con `-Pnw.client.id=eudiw-abca`.

### Infraestructura

| Fichero | Cambio |
|---|---|
| `eudi-srv-issuer-pid-registraduria/docker-compose/keycloak/realms/pid-registraduria-realm-realm.json` | cliente `eudiw-abca` (abca‑draft07) con `trustValidator.serviceUrl` |
| `eudi-srv-issuer-pid-registraduria/docker-compose/docker-compose.yaml` | `keycloak` en la red `avance-eudi`; `SERVER_FORWARD_HEADERS_STRATEGY=native`; introspección por `http://keycloak:8080` |
| `eudi-srv-issuer-pid-registraduria/docker-compose/haproxy/haproxy.conf` | no sobrescribe los `X-Forwarded-*`; reenvía `Forwarded`; TLS hacia Keycloak (`keycloak:8443`) |
| `eudi-srv-issuer-pid-registraduria/docker-compose/keycloak/conf/keycloak.conf` | se documenta por qué el backchannel es dinámico |
| `eudi-srv-issuer-pid-uis/docker-compose/docker-compose.yaml` | `uis-issuer` en la red `avance-eudi`; `SERVER_FORWARD_HEADERS_STRATEGY=native`; introspección por `http://keycloak-uis:8080` |
| `eudi-srv-issuer-pid-uis/docker-compose/haproxy/haproxy.conf` | no sobrescribe los `X-Forwarded-*`; reenvía `Forwarded` |
| `srv-nw-nginx/nginx.conf` | propaga el esquema original del cliente y la cabecera `Forwarded`; https para las rutas de los emisores |
