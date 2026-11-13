# Informe de auditoría del ecosistema EUDI — Avance

> Análisis estático de los 9 proyectos del ecosistema en `/home/geovani/Workspace/Avance/`, contrastado con ARF 3.0.0 y los 725 HLR usando el corpus propio `Avance_II/otros/Agente_EudiW`.

**Fecha:** 2026-10-20 · **Autor:** OpenCode (`opencode/minimax-m3.1-flash-free`) · **Modo:** análisis estático (no se levantaron stacks)

## 1. Resumen ejecutivo

El ecosistema **separa correctamente los roles del ARF en proyectos independientes** (PID Provider, Attestation Provider, Wallet Provider, Verifier, Wallet Unit), cada uno con su Compose, su base de datos, su PKI y su `client_id`. Esa es la decisión estructural correcta y **no hay que deshacerla**.

El incumplimiento de fondo es **uno solo: no existe el modelo de confianza (LoTE / Trust Lists)**, y a partir de él se derivan el resto de problemas: rutas del gateway que no coinciden con las de los emisores, issuers inalcanzables, colisiones de puertos, duplicados sin depurar y panel de control roto.

> **Veredicto:** la arquitectura de roles es buena; la **cadena de confianza esinexistente**. Mientras la confianza sea un pinning manual de una CA de desarrollo metida en `res/raw/`, el sistema funciona en demo pero **no cumple el ARF** y no escala a multi-issuer.

**Prioridad:** F0 (decisiones) → F1 (gateway) → F2 (modelo de confianza) → F3 (issuers) → F4 (higiene) → F5 (verificación).

---

## 2. Método y alcance

- **Alcance:** `/home/geovani/Workspace/Avance/` (9 carpetas) + 3 dependencias que viven fuera (`Avance_II/proxy-nginx`, `Avance_II/OnboardingAddi`, `Avance_II/otros/eudi-srv-pid-issuer`).
- **Método:** lectura estática de Compose, haproxy, PKI, configuración de red y código de la app. **No se levantó ningún stack durante este análisis** (el stack `newwallet` sí estaba arriba y sano, heredado de la sesión previa).
- **Normativa:** ARF 3.0.0 y los 725 HLR, consultados con las herramientas del corpus propio:
  ```bash
  cd /home/geovani/Workspace/Avance_II/otros/Agente_EudiW
  python3 -I scripts/hlr.py --topics
  python3 -I scripts/hlr.py --topic 31 --nivel SHALL
  python3 -I scripts/buscar.py "trust list LOTL LoTE" -f hlr -n 8
  ```
- **No verificado por análisis estático:** comportamiento en ejecución de los stacks de la Registraduría, UIS y verifier; issued tokens reales; conformance funcional.

---

## 3. Inventario real (rol ARF por proyecto)

| Carpeta | Rol ARF | Compose | Puertos publicados | Estado |
|---|---|---|---|---|
| `new-wallet` | **Wallet Unit** (`co.org.newwallet.app`) + onboarding + **Wallet Provider** (`newwallet-provider`, client_id `newwallet-app`) | `newwallet` | 8195, 8097, 8197, 5439 | ✅ arriba y sano |
| `eudi-srv-pid-registraduria` | **PID Provider** (Registraduría) | `registraduria` | **443** | ⚠️ abajo |
| `eudi-generic-issuer-local-build` | **Attestation Provider** (académico UIS) | `uis-issuer` | **80 + 443** | ⚠️ abajo |
| `eudi-generic-verifier-local-build` | **Verifier / Relying Party** | `avance-verifier` | 127.0.0.1:8480 | ⚠️ abajo |
| `eudi-srv-wallet-provider` | **Wallet Provider #2** (Spartans, client_id `eudiw-abca`) | `spartans` | — | ⚠️ abajo |
| `addi-onboarding` | Onboarding (biometría/OCR) | **no tiene** | — | ⚠️ huérfano |
| `app-android-wallet-ui` | Wallet Unit #2 (`co.org.avance.wallet`) | — | — | ⚠️ 3,6 GB, fork del mismo upstream |
| `panel-control` | Panel de administración | `panel-control` | — | ⚠️ roto (ver H6) |
| `nw-nginx` | — | — | — | 🗑️ vacío (solo `.agents/`, `.claude/`) |

### Dependencias fuera de `Avance/`

| Ruta | Qué es | Nota |
|---|---|---|
| `Avance_II/proxy-nginx` | **Gateway central real**: nginx + certbot/Let's Encrypt + red `avance-proxy` + `overrides/` + `start-all.sh` | **No está en `Avance/`** → H2 |
| `Avance_II/OnboardingAddi` | Fuente original del onboarding (con su `docker-compose/`) | 3ª copia → H8 |
| `Avance_II/otros/eudi-srv-pid-issuer` | PID issuer **vanilla** (upstream) | Superconjunto contenido en el fork → H8 |
| `Avance_II/otros/Agente_EudiW` | Corpus ARF/HLR + agente + plan maestro | Herramienta de referencia, no parte del runtime |

### Lo que el gateway ya tenía resuelto (arquitectura de referencia)

`Avance_II/proxy-nginx/` implementa el patrón correcto para "proyectos independientes con un solo dominio":

- `nginx.conf` publica **80 y 443** con **Let's Encrypt** y enruta por prefijo a través de la red Docker compartida `avance-proxy`.
- `overrides/*.yaml` hacen `ports: !reset []` (quitan los puertos del host) y **conectan cada stack a la red `gateway`** — los Compose originales quedan intactos. **Este es el mecanismo que hay que restaurar.**
- `start-all.sh` levanta cada stack con `-f compose -f override`.

| Prefijo | Destino (según `nginx.conf`) |
|---|---|
| `/issuer/pid-registraduria/` | `haproxy-reg:80` |
| `/issuer/pid-uis/` | `haproxy-uis:80` |
| `/pid-issuer/` | `haproxy-pid:80` |
| `/verifier/` | `avance-verifier-api:8080` |
| `/spartans/` | `spartans-wallet-provider:8080` |
| `/onboarding/` | `onboarding-backend:8095` |
| `/wallet-provider/` | `newwallet-provider:8080` |
| `/newwallet-status/` | `status-list-nwp:5000` |
| `/addi/onboarding/` | `backend:8095` |
| `/addi/biometrics/` | `biometrics:8100` |
| (defecto) | `http://avance-control-panel:4173` ← panel |

---

## 4. Requisitos normativos aplicables (HLR verificados en el corpus)

| ID | Topic | Exigencia |
|---|---|---|
| `ISSU_07` | 10 | La Wallet Unit valida la firma de la PID con un anchor del PID Provider **publicado en una LoTE** (T31). |
| `ISSU_19` | 10 | El PID Provider **debe aceptar los anchors del Wallet Provider** publicados en la *Wallet Provider LoTE*. |
| `ISSU_10a` | 10 | El Wallet Provider **descarga periódicamente** las LoTEs aplicables y reparte los anchors a sus Wallet Units. |
| `ISSU_23` | 10 | La Wallet Unit acepta **todos** los anchors de Access CA publicados por la Comisión en la LoTE relevante, y solo esos. |
| `PPNot_05` | 31 | Wallet Units y RPs aceptan anchors de PID Provider y de listas de revocación por la notificación del Estado Miembro y publicación en la LoTE compilada por la Comisión. |
| `WPNot_04` | 31 | PID Providers y Attestation Providers aceptan anchors de Wallet Provider y de revocación de WIA/KA publicados en la *Wallet Provider LoTE*. |
| `GenNot_01..03` | 31 | Notificación a la Comisión y publicación de LoTEs firmadas, en formato legible por máquina y por persona. |
| Topic 27 | 27 | Registro y publicación **separados** de PID Providers, Wallet Providers, Attestation Providers, Access CAs y Relying Parties. |

**Implicación clave:** las listas de confianza no son un extra: son **el backbone del modelo de confianza del ARF**. Sin LoTE, todo lo demás es una demo, no conformidad.

---

## 5. Hallazgos

### H1 — No existe ninguna Trust List / LoTE en el ecosistema · **CRÍTICO**

Es el incumplimiento de mayor peso y rompe la cadena de confianza del ARF.

- La app apunta a dominios **muertos**: `https://newwallet.invalid/tl/PID/current.json`, `.../tl/PUBEAA/current.json`, `.../tl/WP/current.json`. Son los defaults de `core-logic/build.gradle.kts:35-37` (propiedades `-Pnw.tl.pid`, `-Pnw.tl.pubeaa`, `-Pnw.tl.wp`, ninguna fue fijada al compilar).
- El único config de trust-validator, `new-wallet/backend/trust-validator/registraduria.override.yaml`:
  - apunta a `https://civica.avance.org.co/tl-publisher/LOTL/current.xml` → dominio de **producción**, no el de desarrollo (`civica-desarrollo.avance.org.co`);
  - monta el keystore desde `/home/geovani/Descargas/generic/new-wallet/backend/trust-validator/civica-lotl.jks` → **ruta inexistente** (el `.jks` real está en `Avance_II/eudi-app-wallet-new_wallet/backend/trust-validator/civica-lotl.jks`);
  - por tanto el contenedor `civica-trust-validator` **no arranca** con ese override.
- `ISSUER_TRUST_SERVICEURL=` está **vacío** en `eudi-srv-pid-registraduria/docker-compose/docker-compose.yaml:163` → el PID Provider **no consulta el trust-validator** → **no valida la WIA/KA** → incumplimiento directo de `ISSU_19`.
- Lo único que "confía" hoy es `network-logic/src/dev/res/raw/nw_registraduria_ca.crt` (CA `Registraduria Dev Local CA`, SHA-256 `E3:93:70:6E:93:05:63:A0:A4:D9:8B:D8:4B:24:84:12:4E:6E:52:2D:D6:61:4A:40:74:86:C2:88:4C:18:32:C0`) — pinning manual de CA de desarrollo.

**Nota útil:** ese mismo fingerprint coincide exactamente con `eudi-srv-pid-registraduria/docker-compose/pki-dev/ca.crt`, es decir, la Registraduría sí tiene una CA de desarrollo propia y **ya confiable por la app**. El uis-issuer no (ver H7).

### H2 — El gateway central no está en el ecosistema · **CRÍTICO**

`proxy-nginx` (el que publica 80/443, renueva Let's Encrypt y enruta por prefijo) quedó en `Avance_II/`. Su `start-all.sh` invoca 7 stacks con rutas de la era Avance_II; **5 de las 7 no existen** en `Avance/`:

| `start-all.sh` espera | Realidad en `Avance/` | Estado |
|---|---|---|
| `eudi-srv-issuer-pid-registraduria/docker-compose` | `eudi-srv-pid-registraduria/docker-compose` | ❌ renombrado |
| `eudi-srv-issuer-pid-uis/docker-compose` | `eudi-generic-issuer-local-build/docker-compose` | ❌ renombrado |
| `eudi-srv-verifier-generic/docker-compose` | `eudi-generic-verifier-local-build/docker-compose` | ❌ renombrado |
| `eudi-app-wallet-new_wallet/docker-compose` | `new-wallet/docker-compose` | ❌ renombrado |
| `eudi-srv-wallet-provider/docker` | `eudi-srv-wallet-provider/docker` | ✅ |
| `otros/eudi-srv-pid-issuer/docker-compose` | solo en `Avance_II/` | ❌ fuera |
| `otros/addi/OnboardingAddi/docker-compose` | solo en `Avance_II/` | ❌ fuera |

### H3 — Los issuers son inalcanzables a través del gateway · **ALTO**

Tres rutas distintas para el mismo servicio:

| Capa | Ruta declarada |
|---|---|
| Gateway (`nginx.conf`) | `/issuer/pid-registraduria/` → `haproxy-reg:80`, **sin quitar el prefijo** |
| haproxy (`haproxy.conf`) | sirve `/pid-registraduria`, `/.well-known/{openid-credential-issuer,oauth-protected-resource,jwt-vc-issuer}/pid-registraduria`, `/idp` |
| Emisor (`ISSUER_PUBLICURL`) | `https://civica-desarrollo.avance.org.co/pid-registraduria` |
| App (APK instalado) | `https://civica-desarrollo.avance.org.co/pid-registraduria` |

El gateway no tiene regla de *strip* para `/issuer/...` en su mapa `map $uri $upstream_path` (solo `/verifier`, `/spartans`, `/onboarding`, `/wallet-provider`, `/newwallet-status`, `/addi/*`), de modo que el haproxy recibe `/issuer/pid-registraduria/...` y cae en su backend `no-match` → **404**. Lo mismo ocurre con UIS (`/issuer/pid-uis/` vs `/uis-issuer`).

**Regla que hay que fijar:** *la URL que declara `ISSUER_PUBLICURL` debe ser exactamente la que enruta el gateway.*

### H4 — Colisión de puertos 80/443 entre los dos issuers · **ALTO**

`uis-issuer` publica `443:443` **y** `80:80`; `registraduria` publica `443:443`. No pueden coexistir en el host. El patrón correcto ya existe en el diseño previo (los overrides): **cada stack sin puertos publicados + un gateway único con TLS**.

### H5 — Override desalineado deja un puerto publicado · **MEDIO**

`overrides/uis.yaml` resetea `postgres-pid-uis` (no existe; el servicio hoy se llama `postgres-uis-issuer`) → su puerto **5435 no se libera** y queda publicado en el host. Los overrides de `registraduria`, `verifier`, `spartans` y `newwallet` **sí** coinciden con los nombres de servicio reales.

### H6 — Backend colgante en el haproxy de UIS · **MEDIO**

El haproxy de `uis-issuer` enruta `/spartans` y `/.well-known/oauth-protected-resource/spartans` hacia `spartans-wallet-provider:8080`, servicio que **no existe en ese Compose** (sus servicios son `postgres-uis-issuer`, `postgres-uis-academic`, `keycloak`, `status-list`, `uis-issuer`, `haproxy`) → **503 permanente** en esas rutas.

### H7 — Certificado del uis-issuer autofirmado · **MEDIO**

`eudi-generic-issuer-local-build/docker-compose/haproxy/certs/civica-desarrollo.tls.pem` es **self-signed** (`subject = issuer = C=CO, O=Avance, CN=civica-desarrollo.avance.org.co`, SAN solo `DNS:civica-desarrollo.avance.org.co`). Ni el almacén del sistema ni la app lo confían. Se resuelve solo si el TLS termina en el gateway con Let's Encrypt; si el teléfono habla directo al issuer por IP, no valida.

### H8 — Duplicados · **MEDIO**

| Duplicado | Detalle | Acción sugerida |
|---|---|---|
| 2 Wallet Providers | `eudi-srv-wallet-provider` (`spartans`, `CLIENTID=eudiw-abca`) vs `new-wallet/backend/wallet-provider` (`newwallet-provider`, `CLIENTID=newwallet-app`). **Mismo código**: `diff -rq` de `wallet-provider-service/src` → 1 solo archivo distinto (`WalletProviderConfiguration.kt`) | **Legítimo** si se documentan como 2 WP con 2 anchors y 2 entradas de LoTE. Decidir y documentar |
| 2 Wallet Units | `new-wallet` (`co.org.newwallet.app`) vs `app-android-wallet-ui` (`co.org.avance.wallet`), mismo upstream, 3,6 GB cada uno | Podar una (la `dev` de `new-wallet` ya está instalada y funcionando en el teléfono) |
| 2 PID issuers | `eudi-srv-pid-registraduria` ⊃ `Avance_II/otros/eudi-srv-pid-issuer` (verificado: **cero archivos solo en el vanilla**) | Podar el vanilla |
| 3 copias del onboarding | `new-wallet/onboarding`, `Avance/addi-onboarding`, `Avance_II/OnboardingAddi` — **ya divergieron** en `imagenes-test/` | Quedar con 1 (la del repo) |
| Compose obsoleto | `eudi-srv-wallet-provider/docker-compose.yaml` (raíz: postgres 18.3, sin red `edge`) vs `docker/docker-compose.yaml` (el bueno, `name: spartans`) | Borrar el de la raíz |
| `panel-control` | `server.js:10` resuelve `proxyDir = path.join(root,'proxy-nginx')` → **no existe en `Avance/`**; y sus 7 entradas apuntan a carpetas de la era Avance_II (`eudi-srv-issuer-pid-uis`, `eudi-app-wallet-new_wallet`, `otros/...`) → el panel no encuentra ningún stack | Reparar al restaurar el gateway |
| `nw-nginx` | Carpeta vacía (solo `.agents/`, `.claude/`) | Eliminar o reutilizar como nombre del gateway |

### H9 — La app conoce un solo issuer y un solo Wallet Provider · **MEDIO**

`core-logic/build.gradle.kts:38,40` expone `NW_ISSUER_URL` y `NW_WALLET_PROVIDER_URL` (una URL cada uno) y `WalletCoreConfigImpl` (dev) construye **una sola** entrada `VciConfig`. `ISSU_23` da margen ("es decisión de cada Wallet Provider qué PID Providers soportan sus Wallet Units"), pero para multi-issuer hacen falta: **2 anchors de PID Provider en el Wallet Provider** y un catálogo de emisores en la app.

---

## 6. Estado del stack `newwallet` en el momento del análisis

Verificado en ejecución (lo único que estaba arriba):

| Contenedor | Imagen | Puerto | Estado |
|---|---|---|---|
| `nw-onboarding-backend` | `newwallet-onboarding-backend:latest` | 8195 → 8095 | healthy |
| `nw-onboarding-biometrics` | `newwallet-onboarding-biometrics:latest` | 127.0.0.1:8200 | healthy |
| `nw-onboarding-postgres` | `postgres:16-alpine` | 127.0.0.1:5443 | healthy |
| `newwallet-provider` | `newwallet-provider:local` | 8097 → 8080 | Up |
| `postgres-nwp` | `postgres:18.4-alpine3.24` | 5439 | healthy |
| `status-list-nwp` | `status-list-nwp:v0.9.0` | 8197 → 5000 | Up |

**Desviaciones detectadas en el stack arriba (Fase 2 y F3 las corrigen):**

1. `newwallet-provider` publica `ISSUER_PUBLICURL=http://localhost:8097` → la metadata `/.well-known/oauth-protected-resource` devuelve `"resource": "http://localhost:8097"` y `"jwks_uri": "http://localhost:8097/jwks"`, inservibles desde el teléfono. El `up.sh` original lo resolvía con `NW_PROVIDER_PUBLIC_URL=http://${NW_HOST_IP}:8097`; ese `.env` se perdió al re-clonar. **Fix:** `docker-compose/.env` con `NW_HOST_IP=192.168.0.240` y `NW_PROVIDER_PUBLIC_URL=http://192.168.0.240:8097`.
2. El APK instalado (`co.org.newwallet.app.dev` v1.0.0, instalado 2026-10-19 09:50) lleva embebido `http://192.168.0.240:8195` (backend) y `http://192.168.0.240:8097` (provider) → **coherente con el diseño IP:puerto**, pero las trust lists apuntan a `https://newwallet.invalid/...` (**muertas**) y el issuer a `https://civica-desarrollo.avance.org.co/pid-registraduria` (depende del gateway + stack de la Registraduría).
3. No hay `docker-compose/.env` (lost) → el provider quedó con los defaults `localhost`.

---

## 7. Plan de trabajo

### F0 — Decisiones del responsable (bloqueante, ~15 min)

| # | Decisión | Recomendación |
|---|---|---|
| D1 | ¿Gateway central único con Let's Encrypt y rutas por prefijo, con cada stack sin puertos publicados? | **Sí** — es el diseño previo (`proxy-nginx` + overrides) y el único que permite independence + un dominio |
| D2 | ¿Dónde vive el gateway? | `Avance/proxy-nginx/` (dentro del ecosistema, junto a `panel-control` que lo referencia) |
| D3 | ¿Rutas canónicas de issuer? | **Unificar en la raíz** (`/pid-registraduria`, `/uis-issuer`) porque es lo que ya declaran `ISSUER_PUBLICURL` y la app; el gateway deja de usar `/issuer/...` |
| D4 | ¿1 o 2 Wallet Providers? | **2** (Spartans y New Wallet) con `client_id` y anchor distintos, documentados como 2 WP en la LoTE |
| D5 | ¿1 o 2 Wallet Units Android? | **1**: `new-wallet` (la `dev` ya está instalada y es la que apunta al stack actual) |
| D6 | ¿1 o 2 PID issuers? | **1**: la Registraduría (el fork); podar el vanilla |

### F1 — Restituir el gateway en `Avance/` (1–2 h)

1. Mover `Avance_II/proxy-nginx/` → `Avance/proxy-nginx/` (conservar `certbot-renew/`, `overrides/`, `start-all.sh`).
2. Recrear la red externa: `docker network create avance-proxy`.
3. Reescribir `start-all.sh` y `overrides/*.yaml` con los **nombres reales** de carpeta y de servicio (tabla del §3).
4. Corregir `overrides/uis.yaml`: `postgres-pid-uis` → `postgres-uis-issuer`; añadir `keycloak`, `status-list`, `uis-issuer` si deben quedar en la red `gateway`.
5. Alinear el mapa de rutas con `ISSUER_PUBLICURL` (regla D3) y añadir *strip* donde corresponda.
6. Reparar `panel-control/server.js` (lista de `entries` → rutas reales; `proxyDir` → `Avance/proxy-nginx`).
7. Verificar: `docker compose -f <stack> -f <override> config` sin errores para los 6 stacks.

### F2 — Modelo de confianza (2–3 días) — **lo más pesado**

1. Publicar **LoTEs propias por rol** (PID, Wallet Provider, PuB-EAA), firmadas por una CA "de registro" del ecosistema, que simulen la LoTE de la Comisión. Servidor nuevo: `Avance/tl-publisher/` (p. ej. `static/` + firmante + endpoint `current.json` / `current.xml`).
2. **trust-validator**: fijar el `.jks` en ruta válida (copiar `civica-lotl.jks` a `Avance/new-wallet/backend/trust-validator/`), apuntar la LoTE al **dominio de desarrollo**, y alinear el override con los nombres reales de servicio.
3. **Registraduría**: `ISSUER_TRUST_SERVICEURL=http://civica-trust-validator:8080/trust` (cumple `ISSU_19`).
4. **App**: compilar con `-Pnw.tl.pid=… -Pnw.tl.pubeaa=… -Pnw.tl.wp=…` apuntando a las LoTEs reales, en vez de los defaults `newwallet.invalid`.
5. **Wallet Provider**: `ISSU_10a` → descarga periódica de LoTEs y reparto de anchors a las Wallet Units (validar si el backend Spartans ya lo soporta o hay que añadirlo).
6. Decidir explícitamente: **LoTE (norma) como mecanismo principal**, pinning de CA **solo** para el issuer de desarrollo.

### F3 — Corregir los issuers (0,5–1 día)

1. `uis-issuer`: quitar las rutas `/spartans` del haproxy o declarar el servicio; suprimir lapub de `80:80` (debe vivir detrás del gateway).
2. `uis-issuer`: cert firmado por la CA de desarrollo (o Let's Encrypt vía gateway) + CA en la app si se usa por IP.
3. Verificar coherencia de `issuer`, `jwks_uri`, `token_endpoint` y `authorization_endpoint` con las rutas reales del gateway.
4. `status-list`: un servicio por issuer, con su `API_key` y su URL pública declarada en el issuer.

### F4 — Higiene y documentación (0,5 día)

- Podar: `app-android-wallet-ui`, `Avance_II/otros/eudi-srv-pid-issuer`, `eudi-srv-wallet-provider/docker-compose.yaml` (raíz), copies redundantes del onboarding, carpeta `nw-nginx` vacía.
- Escribir un **manifiesto de roles** (una tabla): proyecto → rol ARF → anchor (CA) → URL pública → Compose → override → estado. Es el documento que falta y la razón por la que cada reorganización rompe el ecosistema.
- `.gitignore` coherente por proyecto (secretos: `*.jks`, `pki/`, `.env`, `keystore/`).

### F5 — Verificación (1–2 días)

- Levantar **un stack a la vez**: Registraduría → UIS → verifier → onboarding, validando el flujo completo por el gateway.
- Pruebas mínimas por emisor: `/.well-known/openid-configuration` del realm, `/.well-known/openid-credential-issuer`, `/.well-known/oauth-protected-resource`, `/.well-known/jwt-issuer`, `/public_keys.jwks`, `token_status_list` y **emisión real de una credencial** desde la app.
- Matriz de conformidad HLR por rol (pendiente `F1-03` del plan maestro) con evidencia `ruta:línea`.

---

## 8. Riesgos

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Dejar el pinning de CA como mecanismo permanente | El sistema no escala a multi-issuer ni es auditable | F2 con LoTE reales; pinning solo para dev |
| Levantar los dos issuers a la vez | Colisión 80/443 | F1: sin puertos publicados + gateway |
| `ISSUER_PUBLICURL` y rutas del gateway desalineadas | Issuers 404 por el gateway (H3) | Regla única D3 + prueba de metadata en F5 |
| Reorganizar carpetas sin actualizar `start-all.sh`/overrides/`panel-control` | Caída silenciosa de rutas (H2, H5, H8) | Manifiesto de roles (F4) como fuente de verdad |
| Confundir el gateway de desarrollo con el central de `190.29.100.206` | Diagnóstico erróneo de 502/404 | El central reenvía 80/443 al PC; con gateway local activo responde nuestro nginx, sin él responde 502 |

---

## 9. Glosario y equivalencias (para el agente que reciba este informe)

| Término aquí | Equivalente ARF / EUDI | En este ecosistema |
|---|---|---|
| PID Provider | PID Provider | `eudi-srv-pid-registraduria` (`pid-issuer`) |
| Attestation Provider | Attestation Provider / PuB-EAA | `eudi-generic-issuer-local-build` (`uis-issuer`) |
| Wallet Provider | Wallet Provider | `eudi-srv-wallet-provider` (Spartans) y `new-wallet/backend/wallet-provider` |
| Wallet Unit | Wallet Unit / EUDI Wallet | `new-wallet/app` (`co.org.newwallet.app`, flavor `dev`) |
| Relying Party / Verifier | Relying Party | `eudi-generic-verifier-local-build` (`avance-verifier`) |
| LoTE | List of Trusted Entities (publicada por la Comisión) | **no existe todavía** → F2 |
| tl-publisher | (equivalente propio) | **no existe todavía** → F2 |
| trust-validator | (equivalente de `eudi-srv-trust-validator`) | solo el override `new-wallet/backend/trust-validator/registraduria.override.yaml` (roto) |
| gateway | — | `Avance_II/proxy-nginx` (fuera del ecosistema) → F1 |
| LoTL | List of Trusted Lists (CADETS) | `Avance_II/proxy-nginx` no lo publica; el override apunta a `civica.avance.org.co/tl-publisher/LOTL/current.xml` |

---

## 10. FIRMA Y TRASPASO

```
Informe: Auditoría de conformidad del ecosistema EUDI de Avance
Fecha de emisión: 2026-10-20 10:08 -05 (15:08 UTC)
Autor: OpenCode — agente de análisis, modelo opencode/minimax-m3.1-flash-free
Sesión: workspace /home/geovani/Workspace/Avance/new-wallet
Alcance: 9 proyectos en /home/geovani/Workspace/Avance/ + 4 dependencias en Avance_II/
Base normativa: ARF 3.0.0 + 725 HLR, vía corpus propio Avance_II/otros/Agente_EudiW
SHA-256 del cuerpo (hasta el final del §8): 22d47139d9af7377cd2ac913497a4433e86579ea7bff4b948c372c704590c51f
Estado: ANÁLISIS ESTÁTICO — los stacks de Registraduría, UIS y verifier NO se levantaron para este informe
```

### Qué está verificado y qué es inferencia

**Verificado por lectura de archivos (reproducible):** nombres de servicio y puertos de cada Compose; mapa de rutas de `nginx.conf` y de los dos haproxy; `ISSUER_PUBLICURL` de cada emisor; rutas de las trust lists en `core-logic/build.gradle.kts`; valores de `ISSUER_TRUST_SERVICEURL` y del override del trust-validator; fingerprint de la CA de la Registraduría y coincidencia con `nw_registraduria_ca.crt`; ausencia de LoTEs (`find -name current.json` → vacío); duplicados (`diff -rq`); rutas inexistentes de `start-all.sh`; cert autofirmado del UIS; backend `/spartans` colgante; nombres de `client_id` de los dos Wallet Providers; APK instalado (versión, fecha, URLs embebidas vía `adb exec-out`); estado y health de los 6 contenedores del stack `newwallet`.

**Inferido (pendiente de prueba en ejecución):** que el gateway devuelve 404 en `/issuer/pid-registraduria/` (deducido de los mapas de ruta, no ejecutado); que `civica-trust-validator` no arranca con el override actual (ruta inexistente, deducido); que los overrides `newwallet/registraduria/verifier/spartans` sí coinciden con los nombres reales (comparación de cadenas, verificado); que el vanilla PID issuer es superconjunto del fork (`diff -rq`, verificado).

### Reglas de la casa que este informe respeta

1. **Citar o callar:** cada afirmación normativa lleva ID de HLR y Topic; no hay numeración inventada. Afirmaciones colombianas/legal: ninguna.
2. **Separar roles:** el informe no propone fusionar issuers, WPs ni apps; propone **documentar y anclar** los que se decida conservar.
3. **Los Compose originales no se tocan:** la independencia de cada proyecto se preserva con *overrides* (`ports: !reset []` + red `gateway`), nunca editando el Compose de cada stack.
4. **Un solo punto de entrada TLS:** un gateway, rutas por prefijo, una sola LoTE por rol.
5. **Regla de oro de URL:** *lo que declara `ISSUER_PUBLICURL` es exactamente lo que enruta el gateway* (H3).

### Estado de la tarea y siguiente paso

- **Hecho:** diagnóstico completo, inventario por rol, 9 hallazgos priorizados con evidencia, plan F0→F5, y corrección puntual del stack `newwallet` ya arriba (`.env` con `NW_PROVIDER_PUBLIC_URL`).
- **Pendiente de decisión humana (F0):** D1–D6 de la §7.1. **No se avanza a F1 sin esas decisiones**, porque determinan rutas y qué duplicados se podan.
- **Siguiente acción recomendada:** resolver F0 → restaurar `Avance/proxy-nginx` (F1) → corregir el `.env` del stack → publicar LoTEs propias (F2) → verificación stack por stack (F5).
- **Advertencia operativa:** el nginx central de `190.29.100.206` reenvía el dominio `civica-desarrollo.avance.org.co` a este PC por **80 y 443**. Sin gateway local activo, ese dominio responde **502**; con el gateway local, responde desde aquí. No confundir ambos escenarios al diagnosticar.
- **Estado del dominio en este momento:** sin gateway local (proyecto `nw-nginx` descartado por decisión del responsable el 2026-10-20), por lo que `civica-desarrollo.avance.org.co` **no sirve nada** hasta que se restituya `proxy-nginx` (F1).

*Fin del informe. Emitido por OpenCode (`opencode/minimax-m3.1-flash-free`) el 2026-10-20 para entrega al equipo y al siguiente agente.*
