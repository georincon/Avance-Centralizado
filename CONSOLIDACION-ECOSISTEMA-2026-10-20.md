# Consolidación del ecosistema EUDI de Avance

**Fecha:** 2026-10-20
**Alcance:** `/home/geovani/Workspace/Avance/`
**Dominio único:** `https://civica-desarrollo.avance.org.co`
**Documento complementario:** [`ECOSISTEMA.md`](./ECOSISTEMA.md) (manifiesto de roles, fuente de verdad)

> **NOTA (2026-10-20, segunda sesión).** Este documento describe la **primera** consolidación.
> Después se hizo una segunda pasada que lo cambió todo:
>
> - `srv-tl-publisher` estaba en `Avance_II/` y sus contenedores corrían con *bind mounts* a una
>   ruta inexistente: al reiniciar, las listas de confianza se sirvieron **vacías** (500 en
>   `/TL/*`). El proyecto se movió a `Avance/srv-tl-publisher` y se recrearon los contenedores.
> - Se eliminaron el clon del trust-validator (`tv-testbed`) y el override residual de
>   `app-new-wallet/backend/trust-validator/`, que apuntaba a **producción**.
> - `ISSUER_TRUST_SERVICEURL` estaba vacío: el issuer PID no validaba nada. Ahora apunta al
>   validador.
> - El issuer de la UIS y el verifier, que no estaban levantados, se integran al proxy único.
> - **Las anclas publicadas estaban desincronizadas de la PKI viva**: el ancla del Wallet
>   Provider tenía el mismo CN pero era el certificado del despliegue anterior, así que la
>   cadena que presenta la wallet no encajaba con lo publicado. Corregido y republicado.
> - El camino de la LoTL del validador (DSS) **no extrae anclas en esta build**: se comprobó que
>   tampoco funciona contra la LOTL oficial del testbed EUDI. El validador decide la confianza
>   contra un almacén de anclas que se construye con las mismas que publica el publicador.
> - Se unificó la estructura: todos los compose en `<proyecto>/docker-compose/`, con `up.sh` y
>   `down.sh` idempotentes por proyecto. `Avance_II/` quedó en `Avance/_legacy/` (desde el 27-oct-2026 está fuera del repo, en `~/Workspace/Avance_legacy/`).
>
> El detalle de todo esto está en [`ECOSISTEMA.md`](./ECOSISTEMA.md) y en el informe de la
> segunda sesión que acompaña a este documento.

---

## 1. Resumen ejecutivo

El ecosistema tenía **cuatro fuentes de duplicación** y **un punto de entrada roto**. Se resolvió:

| Antes | Ahora |
|---|---|
| 2 Wallet Providers (código clon, PKI separada, 2 entradas en la TL) | **1** Wallet Provider (`spartans`) que valida las 2 Wallet Units |
| 4 proxies/haproxy compitiendo por 80/443 | **1** proxy (`srv-nw-nginx`) |
| 3 copias del onboarding, sin versionar en git | **1** proyecto independiente (`srv-onboarding`) |
| URLs públicas con IP `192.168.0.240` | **Todo** por `civica-desarrollo.avance.org.co` |
| 9 puertos publicados en el host | **Solo 80 y 443** (los del proxy) |

**Estado: operativo y verificado end-to-end desde internet.**

---

## 2. Hallazgo de partida: el dominio ya funcionaba

El symptom inicial era que `civica-desarrollo.avance.org.co` devolvía **502 en todas las rutas**.
Se comprobó que **no era un problema de configuración del proxy**:

```
Prueba: contenedor nginx con TLS escuchando en 192.168.0.240:443
  → GET https://civica-desarrollo.avance.org.co/  →  200 "PROBE-LOCAL-443-OK"

Prueba: servidor HTTP plano en 192.168.0.240:80
  → GET http://civica-desarrollo.avance.org.co/   →  200 "PROBE-PLAIN-80"

Prueba: desafío ACME en el 80 local
  → GET http://civica-desarrollo.avance.org.co/.well-known/acme-challenge/x
                                                  →  200 "ACME-PROBE-OK"
```

**Conclusión:** el proxy público en `190.29.100.206` (nginx, certificado `*.avance.org.co` de
DigiCert) ya reenvía **80 y 443** a este equipo (que tiene esa IP pública por NAT en el router).
Lo único que faltaba era **algo escuchando en el 443 local**. Todo el trabajo de "usar el dominio
en vez de la IP" consistió en eso, no en tocar la infraestructura pública.

> Consecuencia práctica: `192.168.0.240` **deja de aparecer** en cualquier URL pública. El mismo
> APK y las mismas URLs sirven dentro y fuera de la LAN.

---

## 3. Decisiones tomadas y su fundamento normativo

### 3.1 Un solo Wallet Provider

**Fundamento:** ARF v3.0.0 §3.3 — el *Wallet Provider* es una **entidad** (organización), con un
único anchoring de confianza. El *Wallet Unit* (la app) es otra cosa, y puede haber varios
Wallet Units de un mismo Wallet Provider.

**Antes:** dos instancias del mismo código (`diff -rq` de `wallet-provider-service/src` → **un solo
archivo distinto**, `WalletProviderConfiguration.kt`), con PKI, base de datos, lista de estado y
`client_id` propios (`eudiw-abca` y `newwallet-app`), y **dos entradas** en la `TL/WP` de producción
("Spartans Wallet Provider" y "New Wallet Provider").

**Ahora:** una sola instancia que acepta **las dos Wallet Units**:

```yaml
- PLATFORMKEYATTESTATIONVALIDATION_ANDROID_APPLICATIONS_0_PACKAGENAME=co.org.avance.wallet.dev
- PLATFORMKEYATTESTATIONVALIDATION_ANDROID_APPLICATIONS_0_SIGNINGCERTIFICATEDIGESTS_0=xUMzSllGJMaZkE7gIcekICf1Rs6jjvcvM9wzmiQwmbk=
- PLATFORMKEYATTESTATIONVALIDATION_ANDROID_APPLICATIONS_1_PACKAGENAME=co.org.newwallet.app.dev
- PLATFORMKEYATTESTATIONVALIDATION_ANDROID_APPLICATIONS_1_SIGNINGCERTIFICATEDIGESTS_0=AdMZodoCPiGYIScQeMtugvDUZSGcxrht-KxtASkKi1k=
```

La config lo soportaba de serie (`NonEmptyList<ApplicationConfiguration>`); solo había que
declarar la segunda app.

### 3.2 Un solo proxy

**Fundamento:** un único punto de entrada TLS y rutas por prefijo (ARF, capítulo de seguridad).

**Antes:** `haproxy-reg` (443), `haproxy` del issuer UIS (80+443), `haproxy` del verifier (443) y
el gateway de new-wallet (8447, **roto**: declaraba la red `newwallet-provider_default`, que ya
no existía).

**Ahora:** `srv-nw-nginx` publica 80/443 y enruta por prefijo. Los haproxy internos que siguen
vivos (el de la Registraduría) **dejan de publicar puertos** y quedan accesibles solo por la red
compartida.

### 3.3 Una sola red Docker compartida

**¿Es viable?** **Sí**, y es el patrón correcto. La red se declara `external: true` en cada
Compose:

```yaml
networks:
  avance-eudi:
    name: avance-eudi
    external: true
```

Propiedades que da:

- **No pertenece a ningún proyecto**: `docker compose down` no la borra.
- **Ningún proyecto depende del ciclo de vida de otro**: cada uno se levanta y baja solo.
- **El proxy alcanza todo por nombre de servicio** (`spartans-wallet-provider:8080`), sin depender
  de puertos publicados ni de IPs.
- **Las bases de datos quedan privadas**: se verificó que `postgres-spartans`, `postgres-reg` y
  `onboarding-postgres` **no** están en la red compartida.

Es la solución directa al problema de las redes "de proyecto" (`newwallet_default`,
`uis-issuer_default`), que rompían en cascada cuando un stack caía.

### 3.4 El onboarding como proyecto independiente

**Pregunta planteada:** ¿dentro de `new-wallet` o en un proyecto aparte?

**Respuesta: proyecto aparte**, por tres razones:

1. **Es transversal.** Es *identity proofing* (documento, OCR, selfie con liveness, OTP,
   consentimiento): lo necesita cualquier wallet del ecosistema para dar de alta al usuario. No
   pertenece a una wallet concreta.
2. **Estaba duplicado tres veces** — `new-wallet/onboarding`, `Avance/addi-onboarding` y
   `Avance_II/OnboardingAddi` — con `diff -rq` **sin diferencias** (solo divergían en
   `imagenes-test/`).
3. **No estaba versionado**: los 46 archivos de `onboarding/` en `new-wallet` eran *untracked* en
   git (solo se ignoraba `imagenes-test/`). Es decir, el código podía perderse.

Ahora vive en `Avance/srv-onboarding`, con su `.gitignore` y su README.

### 3.5 Dominio en lugar de IP

Se eliminó `192.168.0.240` de:

- `ISSUER_PUBLICURL` y `ISSUER_AUTHORIZATIONSERVER_PUBLICURL` del issuer.
- `STATUS_LIST_PUBLIC_URL` de los dos servicios de lista de estado.
- `WalletProviderConfiguration.kt` (valor por defecto).
- `WALLETLINK`, `WALLETSOLUTIONCERTIFICATIONINFORMATION`, `KEYATTESTATION_CERTIFICATION`.
- Los parámetros de build de la app (`-PbackendUrl`, `-Pnw.walletprovider.url`, `-Pnw.issuer.url`).

---

## 4. Cambios aplicados, proyecto por proyecto

### 4.1 `Avance/srv-nw-nginx` (nuevo)

Punto de entrada único. Nginx + `certbot-renew` (renovación automática y reload).

```
Teléfono ─ https://civica-desarrollo.avance.org.co
   │
   ▼  190.29.100.206 — proxy público, nginx, cert *.avance.org.co (termina TLS)
   ▼  reenvía 80 y 443
   ▼  192.168.0.240:443 — nw-nginx (termina el salto local y enruta)
   │
   ├── /wallet-provider/*                    → spartans-wallet-provider:8080
   ├── /.well-known/oauth-protected-resource  → spartans-wallet-provider:8080  (RFC 9728, en la raíz)
   ├── /jwks                                  → spartans-wallet-provider:8080
   ├── /onboarding/*                          → onboarding-backend:8095
   ├── /biometrics/*                          → onboarding-biometrics:8100
   ├── /pid-registraduria/*                   → haproxy-reg:80
   ├── /idp/*                                 → haproxy-reg:80
   ├── /.well-known/{openid-credential-issuer,oauth-protected-resource,
   │                 jwt-vc-issuer,jwt-issuer,oauth-authorization-server,
   │                 openid-configuration}/* → haproxy-reg:80
   ├── /status-list/wallet-provider/*         → status-list-spartans:5000
   ├── /status-list/pid-registraduria/*      → status-list-reg:5000
   └── (resto)                                → avance-control-panel:4173
```

**Regla de oro aplicada:** *la URL que declara `ISSUER_PUBLICURL` es exactamente la que enruta el
proxy.* (El haproxy de la Registraduría recibe `/pid-registraduria/...` con el prefijo **intacto**,
porque su propio backend hace el `regsub` hacia `pid-issuer:8080`.)

**Certificados:** el borde público aporta al cliente el `*.avance.org.co` válido. El salto local
usa un certificado propio de desarrollo (`certs/live/civica-desarrollo.avance.org.co/`), que el
borde no verifica. Se genera con `./scripts/generar-certificados.sh`.

### 4.2 `Avance/eudi-srv-wallet-provider` (provider único)

- `docker/docker-compose.yaml`: red externa `avance-eudi`; **eliminado** el bloque `edge` que
  declaraba `uis-issuer_default` (red inexistente → `docker compose up` fallaba).
- **Añadido** el servicio `status-list-spartans` (traído del clon) con su propio `status-list`,
  claves y URL pública. Antes el provider pedía `http://status-list:5000` en la red del issuer viejo.
- `scripts/generar-pki-spartans.sh`: ahora genera también las claves de la lista de estado
  (`docker/status-list/certs/{key.pem,cert.crt,cert.der}`).
- `WalletProviderConfiguration.kt:278`: URL por defecto → `https://civica-desarrollo.avance.org.co/wallet-provider`.
- `.gitignore`: añadidos `docker/spartans/` y `docker/status-list/certs/key.pem` (la clave privada
  no debe versionarse; el certificado público sí, es lo que va a la lista de confianza).
- **Eliminado** `docker-compose.yaml` de la raíz (postgres 18.3 + adminer en 5432/9000: chocaba con
  otros proyectos y estaba duplicado).
- `docker-compose/` se conserva como punto de entrada, pero sin overlays: ahora incluye el compose real.

### 4.3 `Avance/srv-onboarding` (nuevo, extraído)

- `docker-compose.yaml` propio, red `avance-eudi`, sin puertos publicados en el host.
- Postgres, biometría y API, con sus healthchecks.
- `up.sh` / `down.sh` y README.

### 4.4 `Avance/app-new-wallet`

- **Eliminado** `backend/wallet-provider/` — el clon (91 archivos versionados).
- **Eliminado** `backend/gateway/` — el proxy roto (referenciaba una red inexistente).
- **Eliminado** `onboarding/` — la copia sin versionar (ahora en `srv-onboarding`).
- `docker-compose/onboarding.yaml` eliminado; el compose raíz ya solo mantiene la red compartida.
- `docker-compose/up.sh`: reescrito, compila la app con el **dominio** e imprime el comando.
- `docker-compose/check-ports.sh`: reescrito (verifica los stacks reales, no los eliminados).
- `core-logic` y `onboarding-feature`: **verificado** que `BuildConfig` embebe el dominio
  (`NW_WALLET_PROVIDER_URL`, `NW_ISSUER_URL`, `NW_TL_WP`, `BACKEND_BASE_URL`).

### 4.5 `Avance/eudi-srv-issuer-pid-registraduria`

- `docker-compose/docker-compose.yaml`: red `avance-eudi`; `haproxy` **deja de publicar 443**;
  `status-list` se une a la red compartida.
- `docker-compose/haproxy/haproxy.conf`: añadido `bind 0.0.0.0:80` para el flujo interno desde el
  proxy (antes solo escuchaba en 443 con TLS y el proxy no podía alcanzarlo).
- `docker-compose/status-list/config/config_service.py`: `service_url` pasa a tomar
  `STATUS_LIST_PUBLIC_URL` del entorno (tenía `https://192.168.0.240:9543/` fijo).

### 4.6 `Avance/panel-control`

- `server.js`: `entries` rehechas con las rutas reales (apuntaban a carpetas de la era `Avance_II`:
  `eudi-srv-issuer-pid-uis`, `eudi-app-wallet-new_wallet`, `otros/...` → **no encontraba ningún
  stack**). `proxyDir` → `srv-nw-nginx`; `service` → `nw-nginx`.
- `docker-compose.yaml`: red `avance-proxy` → `avance-eudi`.
- Verificado: la API `/api/projects` lista los 8 stacks con su estado real.

### 4.7 `Avance/` (raíz)

- `up.sh` / `down.sh`: arranque y parada de **todo** el ecosistema, en orden de dependencias
  (PKI → servicios → proxy al final), con la red compartida creada si falta.
- `ECOSISTEMA.md`: manifiesto de roles (rol ARF → proyecto → ancla → URL → estado).

---

## 5. Verificación ejecutada

### 5.1 Estado del stack

```
avance-control-panel       Up          (4173/tcp)
nw-nginx                    Up          0.0.0.0:80->80, 0.0.0.0:443->443
nw-certbot-renew            Up
spartans-wallet-provider   Up          (8080/tcp)
postgres-spartans           Up healthy  (5432/tcp)
status-list-spartans        Up          (5000/tcp)
onboarding-backend          Up healthy  (8095/tcp)
onboarding-biometrics       Up healthy  (8100/tcp)
onboarding-postgres         Up healthy  (5432/tcp)
haproxy-reg                 Up
pid-issuer-reg              Up          (8080/tcp)
keycloak-reg                Up healthy
status-list-reg             Up
postgres-reg                Up healthy  127.0.0.1:5441->5432
```

**Puertos publicados en el host: solo 80/443** (más el Postgres de la Registraduría en
`127.0.0.1:5441`, deliberadamente local).

### 5.2 Suite HTTP por el dominio público

| Ruta | Código | Servicio |
|---|---|---|
| `/` | 200 | Panel de control |
| `/.well-known/oauth-protected-resource` | 200 | Metadata RFC 9728 del provider |
| `/jwks` | 200 | JWKS del provider |
| `/wallet-provider/.well-known/oauth-protected-resource` | 200 | Alias con prefijo |
| `/onboarding/api/v1/onboarding/sessions` (POST) | 200 | Crea sesión de onboarding |
| `/biometrics/health` | 200 | Servicio de visión |
| `/.well-known/openid-credential-issuer/pid-registraduria` | 200 | Metadata OID4VCI |
| `/.well-known/oauth-protected-resource/pid-registraduria` | 200 | RFC 9728 del issuer |
| `/.well-known/jwt-issuer/pid-registraduria` | 200 | Metadata JWT |
| `/.well-known/jwt-vc-issuer/pid-registraduria` | 200 | Metadata JWT-VC |
| `/.well-known/openid-configuration/idp-reg/realms/...` | 200 | Keycloak OIDC |
| `/pid-registraduria/public_keys.jwks` | 200 | JWKS del issuer |

### 5.3 Prueba funcional de extremo a extremo

**Emisión real de WUA** (no solo un 200):

```
POST /wallet-provider/wallet-instance-attestation/jwk
  → WUA firmado con ES256
      iss          = https://civica-desarrollo.avance.org.co/wallet-provider
      sub          = eudiw-abca
      cnf.jwk      = presente
      client_status.status.status_list.uri =
        https://civica-desarrollo.avance.org.co/status-list/wallet-provider/
        token_status_list/FC/oauth-client-attestation+jwt/<uuid>

GET <esa URL>  (Accept: application/statuslist+jwt)
  → HTTP 200, application/statuslist+jwt, 1305 bytes
```

Es decir: el provider emite la attestation, **toma** el índice en su lista de estado, y la wallet
puede **consultar esa lista por el dominio**. Cadena cerrada.

### 5.4 Validación desde fuera de la red local

`webfetch` (petición desde internet, no desde la LAN) devolvió la metadata del provider y la
interfaz del panel de control. El dominio sirve correctamente fuera de la red.

### 5.5 Arranque limpio

`./up.sh` desde cero (con todos los contenedores detenidos) levanta los 6 stacks, genera las PKI
que falten y deja el sistema operativo. `docker compose config` valida en los 7 proyectos.

---

## 6. Lo que quedó fuera y por qué

| Elemento | Motivo |
|---|---|
| `Avance_II/` | Copias de `eudi-srv-wallet-provider`, `app-new-wallet`, `eudi-srv-issuer-pid-registraduria`, `OnboardingAddi` y el `proxy-nginx` original. Ya innecesarios: el blueprint del proxy se reconstruyó en `Avance/srv-nw-nginx`. **Poda pendiente** (cada uno tiene git, es reversible). |
| `app-eudi-wallet-ui` | La wallet duplicada de Avance (`co.org.avance.wallet`, 3,6 GB). El provider único ya valida su paquete, así que conviven sin conflicto, pero es candidata a poda. |
| `eudi-srv-issuer-pid-uis`, `eudi-srv-verifier-local-build` | Stacks legítimos del ecosistema, no levantados en esta fase. |
| `eudi-srv-trust-validator` | Repo nuevo, propio de la fase de confianza. Aún no integrado a la red compartida ni al proxy. |

---

## 7. Fase siguiente: modelo de confianza

El `tl-publisher` de producción ya publica `TL/WP`, `TL/PID`, `TL/PUBEAA` y `LOTL` en
`https://civica.avance.org.co/tl-publisher/`. La `TL/WP` actual contiene **cuatro** entradas, dos
de ellas de providers ya eliminados:

| Entrada en `TL/WP` | Estado | Acción |
|---|---|---|
| `Wallet Provider de prueba Root CA` | obsoleta | **Retirar** |
| `Spartans Wallet Provider Root CA` | ✅ es el provider único | **Mantener** (publicar la raíz actual) |
| `Avance Status List Root CA` | es la lista de estado | Revisar: publicar `docker/status-list/certs/cert.crt` |
| `New Wallet Provider Root CA` | provider eliminado | **Retirar** |

Pasos:

1. Publicar `eudi-srv-wallet-provider/docker/secretos/pki/raiz.crt` (`CN=Spartans Wallet Provider
   Root CA`) en la `TL/WP` de desarrollo.
2. Publicar la raíz del status-list (`docker/status-list/certs/cert.crt`) en la lista correspondiente.
3. Retirar de `TL/WP` las entradas de los providers eliminados.
4. `trust-validator`: hoy `new-wallet/backend/trust-validator/registraduria.override.yaml` apunta a
   `/home/geovani/Descargas/generic/new-wallet/...` (ruta que **no existe**) y usa la LOTL de
   producción. Fijar la ruta del `.jks` dentro de `Avance/` y apuntar al LoTE de desarrollo.
5. App: `-Pnw.tl.wp=...` debe apuntar a la TL de desarrollo durante las pruebas, y a la de
   producción en la fase de despliegue.

> Decisión de diseño a confirmar: **LoTE (norma ETSI TS 119 602) como mecanismo principal** y
> *pinning* de CA solo para el issuer de desarrollo. El pinning permanente no escala a multi-issuer
> ni es auditable.

---

## 8. Guía rápida

```bash
cd /home/geovani/Workspace/Avance

./up.sh            # levanta todo el ecosistema
./down.sh          # lo detiene (conserva la red compartida y los volúmenes)
./up.sh --rebuild  # reconstruye las imágenes

# Estado
docker ps
curl -sk https://civica-desarrollo.avance.org.co/api/projects \
  -H "Authorization: Bearer $(grep CONTROL_PANEL_TOKEN panel-control/.env | cut -d= -f2)"

# Recargar el proxy tras editar nginx.conf
docker exec nw-nginx nginx -s reload
```

**Compilar la app con el dominio:**

```bash
cd app-new-wallet
./gradlew :app:assembleDevDebug \
  -PbackendUrl=https://civica-desarrollo.avance.org.co/onboarding \
  -Pnw.walletprovider.url=https://civica-desarrollo.avance.org.co/wallet-provider \
  -Pnw.issuer.url=https://civica-desarrollo.avance.org.co/pid-registraduria \
  -Pnw.tl.wp=https://civica.avance.org.co/tl-publisher/TL/WP/current.json \
  -Pnw.tl.pid=https://civica.avance.org.co/tl-publisher/TL/PID/current.json \
  -Pnw.tl.pubeaa=https://civica.avance.org.co/tl-publisher/TL/PUBEAA/current.json
```

---

*Documento generado durante la consolidación del ecosistema. Manifiesto de roles y mapa de rutas
detallado en [`ECOSISTEMA.md`](./ECOSISTEMA.md). Auditoría previa en
[`INFORME-ECOSISTEMA-EUDI-2026-10-20.md`](./INFORME-ECOSISTEMA-EUDI-2026-10-20.md).*
