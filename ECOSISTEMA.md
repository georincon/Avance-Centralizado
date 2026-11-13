# Manifiesto de roles del ecosistema EUDI de Avance

Fuente de verdad del ecosistema: qué proyecto es cada rol, con qué ancla criptográfica se
publica, por qué URL se accede y cómo se levanta. **Antes de tocar cualquier proyecto, consultar
esta tabla.**

Fecha: 2026-10-20 · Dominio: `civica-desarrollo.avance.org.co` · Proxy público: `190.29.100.206`

## 1. Roles (ARF v3.0.0)

| Rol ARF | Proyecto | Servicio | URL pública | Ancla publicada | Estado |
|---|---|---|---|---|---|
| Wallet Provider | `eudi-srv-wallet-provider` | `spartans-wallet-provider` | `/wallet-provider` | `Spartans Wallet Provider Root CA` (TL/WP) | ✅ operativo |
| PID Provider | `eudi-srv-issuer-pid-registraduria` | `pid-issuer-reg` | `/pid-registraduria` | `REGISTRADURIA ISSUANCE ROOT` (TL/PID) | ✅ operativo |
| Attestation Provider (Tránsito, mDL) | `eudi-srv-issuer-pid-transito` | `transito-issuer` | `/pid-transito` | `TRANSITO ISSUANCE ROOT` (PUB_EAA_Provider; pendiente de registrar en la TL) | ✅ operativo (sin probar con wallet) |
| Attestation Provider (UIS) | `eudi-srv-issuer-pid-uis` | `uis-issuer` | `/uis-issuer` | `UIS Issuer Root CA` (TL/PUBEAA) | ✅ operativo |
| Wallet Unit | `app-new-wallet` | app Android `co.org.newwallet.app.dev` | — | — | ⚠️ compilar con el dominio |
| Wallet Unit | `app-avance-wallet` | app Android `co.org.avance.wallet.dev` | — | — | ⚠️ compilar con el dominio |
| Onboarding (identity proofing) | `srv-onboarding` | `onboarding-backend` | `/onboarding` | — | ✅ operativo |
| Trusted List Provider | `srv-tl-publisher` | `tl-publisher` | `/tl-publisher` | — | ✅ operativo |
| Trust Validator | `eudi-srv-trust-validator` | `trust-validator` | `/trust-validator` | — | ✅ operativo |
| Panel de control | `panel-control` | `avance-control-panel` | `/` | — | ✅ operativo |
| Gateway (único) | `srv-nw-nginx` | `nw-nginx` | — | — | ✅ operativo |

> El `Trusted List Provider` y el `Trust Validator` no tienen ancla propia: la ancla del
> esquema es la raíz que firma las listas (`Civica EUDI Root CA`), que el validador carga
> desde `eudi-srv-trust-validator/claves/lotl-trust.jks`.

## 2. Topología

Un solo punto de entrada y una sola red Docker compartida.

```
                  190.29.100.206  (proxy público, nginx, cert *.avance.org.co)
                          │  reenvía 80 y 443
                          ▼
                  192.168.0.240:443  nw-nginx  (Avance/srv-nw-nginx)
                          │
                          ▼  red Docker externa 'avance-eudi'
        ┌─────────────────┬──────────────────┬───────────────────┐
        │                 │                  │                   │
 spartans-wallet-    onboarding-        haproxy-reg         tl-publisher
 provider:8080        backend:8095      (issuer + kc)        (publica las TL)
                          │
                          ├──────────────► haproxy-uis        (issuer UIS + kc en /idp-uis)
                          ├──────────────► trust-validator:8080
                          └──────────────► tl-publisher-pki:8080  (AIA / CRLDP)
```

- **Puertos publicados en el host**: solo `80` y `443` (los de `nw-nginx`), más el Postgres
  de la Registraduría en `127.0.0.1:5441` (loopback, deliberadamente local).
- **Bases de datos**: privadas, nunca en la red compartida ni enrutadas por HTTP.
- **Red `avance-eudi`**: externa. No pertenece a ningún proyecto; `docker compose down` no la borra.
- **Ningún contenedor alcanza `civica.avance.org.co`**: el dominio de producción está bloqueado
  en el `/etc/hosts` del host y en el `extra_hosts` de los servicios que salen a internet.

## 3. Composición de los proyectos

Todos los proyectos siguen la misma convención: el compose vive en
`<proyecto>/docker-compose/docker-compose.yaml` y se levanta con `<proyecto>/docker-compose/up.sh`.

| Proyecto | Qué contiene | Puerto host | Red |
|---|---|---|---|
| `srv-nw-nginx` | Proxy único + renovación de certificado | 80, 443 | `avance-eudi` |
| `srv-onboarding` | Postgres + biometría + API de onboarding | — | `avance-eudi` |
| `eudi-srv-wallet-provider` | Postgres + status-list + provider | — | `avance-eudi` |
| `eudi-srv-issuer-pid-registraduria` | Postgres + Keycloak + status-list + issuer + haproxy | — | `avance-eudi` |
| `eudi-srv-issuer-pid-transito` | Postgres (`127.0.0.1:5437`) + Keycloak + status-list + issuer mDL + haproxy | — | `avance-eudi` |
| `eudi-srv-issuer-pid-uis` | 2 Postgres + Keycloak (`/idp-uis`) + status-list + issuer + haproxy | — | `avance-eudi` |
| `eudi-srv-trust-validator` | Validador de confianza (LoTL + almacén de anclas) | — | `avance-eudi` |
| `srv-tl-publisher` | MySQL + publicador de listas + servicio de PKI (AIA/CRLDP) | — | `avance-eudi` |
| `app-new-wallet` | Solo la app Android (los servicios viven en sus propios proyectos) | — | — |
| `app-avance-wallet` | Solo la app Android | — | — |
| `panel-control` | Panel web de operación | — | `avance-eudi` |
| _(fuera del repo)_ `../eudi-srv-verifier-local-build/` | Verificador genérico (`/verifier`), retirado el 28-oct-2026. | — | — |
| _(fuera del repo)_ `../Avance_legacy/` | Instantáneas anteriores a la consolidación, movidas fuera de `Avance/` el 27-oct-2026. **No es parte del runtime.** | — | — |
| `docs/corpus-eudi/` | Corpus ARF/HLR y herramienta de consulta normativa | — | — |

## 4. Rutas del proxy único

| Prefijo | Destino |
|---|---|
| `/wallet-provider/*` | `spartans-wallet-provider:8080` (se quita el prefijo) |
| `/.well-known/oauth-protected-resource`, `/jwks` | `spartans-wallet-provider:8080` |
| `/onboarding/*` | `onboarding-backend:8095` (se quita el prefijo) |
| `/biometrics/*` | `onboarding-biometrics:8100` (se quita el prefijo) |
| `/pid-registraduria/*` | `haproxy-reg:80` (prefijo intacto) |
| `/idp-reg/*` | `haproxy-reg:80` (Keycloak de la Registraduría) |
| `/.well-known/(openid-credential-issuer\|oauth-protected-resource\|jwt-vc-issuer\|jwt-issuer\|oauth-authorization-server\|openid-configuration)/*` | según el sufijo: `haproxy-reg:80` o `haproxy-uis:80` |
| `/pid-transito/*` | `haproxy-tra:80` (prefijo intacto) |
| `/idp-tra/*` | `haproxy-tra:80` (Keycloak de Tránsito) |
| `/status-list/pid-transito/*` | `status-list-tra:5000` (se quita el prefijo) |
| `/uis-issuer/*` | `haproxy-uis:80` (prefijo intacto) |
| `/idp-uis/*` | `haproxy-uis:80` (Keycloak de la UIS) |
| `/status-list/wallet-provider/*` | `status-list-spartans:5000` (se quita el prefijo) |
| `/status-list/pid-registraduria/*` | `status-list-reg:5000` (se quita el prefijo) |
| `/status-list/uis-issuer/*` | `status-list-uis:5000` (se quita el prefijo) |
| `/tl-publisher/*`, `/TL/*`, `/LOTL/*`, `/tl-api/*` | `tl-publisher:8080` |
| `/trust/schemerules/*`, `/trust/Svctype/*` | `tl-publisher:8080` |
| `/pki/*` | `tl-publisher-pki:8080` (AIA, CRLDP, CPS, OCSP) |
| `/trust-validator/*`, `/trust`, `/validate`, `/public/*`, `/webjars/*`, `/swagger-ui/*` | `trust-validator:8080` |
| (resto) | `avance-control-panel:4173` |

> **Regla de oro:** la URL que declara `ISSUER_PUBLICURL` es exactamente la que enruta el proxy.
> En los `map` de nginx las reglas específicas van **antes** de las genéricas: en un `map` gana
> la primera expresión regular que coincide.

## 5. Decisiones tomadas

- **Un solo Wallet Provider.** Antes había dos (`spartans` con `client_id=eudiw-abca` y
  `newwallet-provider` con `newwallet-app`), con el mismo código salvo una línea y PKI separada.
  Ahora hay uno, que valida **los dos paquetes Android** (`co.org.avance.wallet.dev` y
  `co.org.newwallet.app.dev`) mediante `PLATFORMKEYATTESTATIONVALIDATION_ANDROID_APPLICATIONS_{0,1}`.
- **Un solo proxy.** Antes: `haproxy-reg` (443), `haproxy` del issuer UIS (80/443), `haproxy` del
  verifier (443) y el gateway de new-wallet (8447, roto). Ahora solo `srv-nw-nginx`.
  Los haproxy internos **siguen vivos como routers de servicio** (reescriben las rutas
  well-known), pero **no publican puertos**: el borde enruta, ellos reescriben.
- **Un solo Onboarding**, como proyecto independiente (`srv-onboarding`): es transversal, no
  pertenece a una wallet concreta, y así no se duplica al clonar una wallet.
- **Un solo Trust Validator** (`eudi-srv-trust-validator`). Se eliminaron el clon `tv-testbed`
  (que apuntaba al testbed público EUDI con otra PKI) y el override residual de
  `app-new-wallet/backend/trust-validator/` (que apuntaba a producción).
- **Dominio en lugar de IP.** Toda URL pública usa `civica-desarrollo.avance.org.co`.
- **Producción bloqueada.** `civica.avance.org.co` no se resuelve desde el host ni desde
  ningún contenedor: las fugas hacia el despliegue público fallan por conexión, no en silencio.

## 6. Restricción del borde público: cookies

El proxy público (190.29.100.206) **reescribe el `Path` de todas las cookies a `/`**, y deja el
valor original en un atributo espurio que el navegador ignora:

```
Set-Cookie: AUTH_SESSION_ID=...;Path=/idp-uis/realms/master/;HttpOnly;SameSite=Lax
   ↓  por el proxy público
Set-Cookie: AUTH_SESSION_ID=...;Path=/; secureidp-uis/realms/master/;HttpOnly;SameSite=Lax
```

Consecuencias, y son **restricciones de la infraestructura, no del código**:

1. **Dos instancias de Keycloak en el mismo dominio comparten sus cookies de sesión**
   (`AUTH_SESSION_ID`, `KC_RESTART`). Entrar en una consola invalida la de la otra.
   → El panel de control ofrece **"Activar consola"**: deja en pie un Keycloak y detiene el del otro
   emisor, y limpia las cookies del navegador para que la primera entrada sea limpia.
   → Consecuencia: con el Keycloak de un emisor detenido, ese emisor no autentica a la wallet.
   Por eso la acción es explícita y no automática: por defecto los dos emisores quedan operativos.
2. **Un solo IdP por dominio.** La alternativa (un Keycloak con los dos realms) funciona y elimina
   el aviso, pero crea un dominio de fallo compartido: si el IdP cae, caen los dos emisores.
3. **El `Path` correcto solo existe dentro de la red**: entre el contenedor del Keycloak y el
   `nw-nginx` local las cookies conservan su ruta (`/idp-reg/realms/master/`). Es el borde público
   el que la destruye.

## 7. Modelo de confianza

- Las listas las publica `srv-tl-publisher` (ETSI TS 119 612), firmadas por
  `Civica TL Signing CA`, en cinco esquemas más la LOTL: `TL/WP`, `TL/PID`, `TL/QEAA`,
  `TL/PUBEAA`, `TL/RPREG` y `LOTL`. Cada publicación es **inmutable** y se sirve por su
  número de secuencia; republicar crea la siguiente.
- `srv-tl-publisher/scripts/verificar-cadenas.py` comprueba que la cadena que cada emisor
  presenta llega hasta el ancla que la lista publica. Es la prueba que detecta el
  desajuste más peligroso: un ancla con el **mismo CN pero distinto certificado**.
- `eudi-srv-trust-validator` valida contra un almacén de anclas que se construye con las
  **mismas** que publica el publicador (`scripts/construir-jks-anclas.sh`), de modo que
  validador y listas no pueden discrepar.
- `eudi-srv-trust-validator/docker/probar-ecosistema.py` es el smoke test: pregunta al
  validador por las tres cadenas reales del ecosistema.

## 8. Fases siguientes

1. **Emisión real desde la app.** La APK tiene que compilarse con el dominio y las listas de
   desarrollo, y hay que recorrer el ciclo completo de emisión PID con la Registraduría y de
   credencial académica con la UIS.
2. **Verificador contra credencial real.** El verifier genérico `/verifier` se retiró el 28-oct-2026
   (ver `../eudi-srv-verifier-local-build/`); la prueba con credencial académica de la UIS se hace con
   los verifiers de `verifiers/` (p. ej. `/verifier-beca/`).
3. **Lista `TL/WRPAC` y `TL/WRPRC`.** Hoy el ancla del verifier está en `TL/RPREG` (tipo
   `CA/RPaccess`), que es la lista que el validador consulta para el contexto
   `WalletRelyingPartyAccessCertificate`. Si la Comisión publica la lista con otro nombre,
   hay que duplicar la entrada.
4. **Multi-emisor en la app.** `core-logic` expone una sola URL de issuer. Con la UIS ya
   operativa hacen falta dos entradas en la configuración de emisores de la app.

## 9. Verificación

```bash
# Estado de los stacks (por el dominio)
curl -sk https://civica-desarrollo.avance.org.co/api/projects \
  -H "Authorization: Bearer $(grep CONTROL_PANEL_TOKEN panel-control/docker-compose/.env | cut -d= -f2)"

# Metadata del Wallet Provider
curl -sk https://civica-desarrollo.avance.org.co/.well-known/oauth-protected-resource

# Metadata del PID Provider
curl -sk https://civica-desarrollo.avance.org.co/.well-known/openid-credential-issuer/pid-registraduria

# Metadata del issuer de la UIS
curl -sk https://civica-desarrollo.avance.org.co/uis-issuer/.well-known/openid-credential-issuer

# Listas de confianza
curl -sk https://civica-desarrollo.avance.org.co/tl-publisher/tl-api/v1/lists

# Las cadenas reales encajan con las anclas publicadas
srv-tl-publisher/scripts/verificar-cadenas.py

# El validador da por de fiar las tres cadenas
eudi-srv-trust-validator/docker/probar-ecosistema.py
```

## 10. Cómo levantar el ecosistema

Un solo comando, desde la raíz:

```bash
./up.sh          # levanta el ecosistema entero (los diez stacks, UIS incluido)
./up.sh --sin-uis  # solo los nueve del compose raíz (sin el issuer de la UIS)
./estado.sh      # qué hay en pie
./down.sh        # lo baja todo
```

El compose raíz (`docker-compose.yaml`) reúne los nueve stacks con `include`, así que cada uno
conserva su fichero y sus secretos. El décimo, **el issuer de la UIS, no está en el compose raíz**
(vive en su propio proyecto, `uis-issuer`), pero `./up.sh` también lo levanta: primero el compose
raíz y después el stack de la UIS con su propio compose. Con `--sin-uis` se queda solo con el
compose raíz, que es lo que se usa cuando se trabaja con el emisor de la Registraduría y no
interesa el de la UIS.

Lo mismo, desde el panel de control (https://civica-desarrollo.avance.org.co/): los botones
**«Levantar todo»** y **«Detener todo»** de la cabecera. Bajan y suben los diez stacks de una
vez; el proxy y el panel se quedan en pie, porque son los que sirven la orden.

Cada proyecto sigue siendo autónomo y se puede levantar por separado desde su carpeta
`docker-compose/`. Los `up.sh` de cada proyecto son idempotentes: crean la red compartida si
falta y generan la PKI que falte (sin regenerar la que ya existe, porque regenerarla invalidaría
lo publicado en las listas).

**Un detalle que no es cosm\u00e9tico:** `include` fusiona los proyectos en uno, as\u00ed que si todos
usaran la red `default` acabar\u00edan compartiendo red. Cada stack declara por eso su red con nombre
expl\u00edcito (`registraduria_default`, `spartans_default`...), y los vol\u00famenes llevan su nombre real
para que las bases de datos no arranquen vac\u00edas.

## Auditoría centralizada de wallets (`srv-auditoria`)

Servicio de **monitoreo** (no cambia roles ni listas de confianza): recibe de cada wallet su alta (usuario + teléfono),
sus ingresos/salidas y cada solicitud o presentación de credenciales, con resultado y error, y lo muestra en un dashboard
de solo lectura en `/srv-auditoria/`. Ver `srv-auditoria/README.md`.
