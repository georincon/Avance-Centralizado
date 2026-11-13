# Mapa de enlaces del ecosistema EUDI de Avance

**Todos los enlaces verificados el 2026-10-20.** Cada ruta lleva su código HTTP real, comprobado
contra el dominio.

- **Desarrollo:** `https://civica-desarrollo.avance.org.co` (proxy único `srv-nw-nginx`)
- **Producción:** `https://civica.avance.org.co` (proxy público `190.29.100.206`) — **desbloqueado**,
  se puede consultar en el navegador para comparar con el desarrollo

Todo lo que hay en `civica-desarrollo.avance.org.co` se enruta por el **proxy único**, que publica
los puertos `80` y `443` y reparte por prefijo de ruta. Ningún otro servicio publica puertos.

---

## Credenciales

| Dónde | Usuario | Contraseña |
|---|---|---|
| Panel admin de la Registraduría | `admin` | `RegAdmin-2026` |
| Panel de estudiantes de la UIS (`/uis-issuer/uis/admin/estudiantes`) | `admin` | `UisAdmin-2026` |
| Keycloak de la Registraduría (`/idp-reg/admin`) | `admin` | `password` |
| Panel admin de Tránsito | `admin` | `TraAdmin-2026` |
| Keycloak de Tránsito (`/idp-tra/admin`) | `admin` | `password` |
| Keycloak de la UIS (`/idp-uis/admin`) | `admin` | `password` |
| API del panel de control | — | `CONTROL_PANEL_TOKEN` → `panel-control/docker-compose/.env` |
| API de publicación del publicador | — | `TL_PUBLISHER_API_KEY` → `srv-tl-publisher/docker-compose/.env` |
| Publicador de listas (navegación) | — | sin login |
| Validador de confianza (navegación) | — | sin login |
| Verificador (navegación) | — | sin login |
| Onboarding y biometría | — | sin login |

Las credenciales del Keycloak son las de bootstrap (`KC_BOOTSTRAP_ADMIN_*`). En un despliegue real
serían otras y habría que rotarlas.

---

## 1. `srv-nw-nginx` — proxy único

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/` | Raíz del dominio → cae en el panel de control |

Es el único punto de entrada. Todo lo demás son prefijos de ruta.

## 2. `panel-control` — panel de operación

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/` | Panel web: subir, bajar y reiniciar stacks; ver su estado real |
| 401 | `/api/projects` | API de estado (JSON) — **exige token Bearer** |

Con token:
```bash
curl -sk https://civica-desarrollo.avance.org.co/api/projects \
  -H "Authorization: Bearer $(grep CONTROL_PANEL_TOKEN panel-control/docker-compose/.env | cut -d= -f2)"
```

Acciones: `POST /api/projects/{id}/action` con `{"action":"up|down|restart"}`.
Ids: `issuer-registraduria`, `issuer-transito`, `issuer-uis`, `wallet-provider`, `onboarding`, `verifier`,
`trust-validator`, `tl-publisher`, `proxy-nginx`.

## 3. `srv-tl-publisher` — publicador de listas de confianza

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/tl-publisher/tl-api/v1/lists` | **Listado de listas publicadas** (JSON, con secuencia y sha256) |
| 200 | `/tl-publisher/tl-api/v1/lists/{esquema}/trust-anchors` | Anclas de confianza de una lista |
| 400 | `/tl-publisher/tl-api/v1/anchors/lookup?scheme=…` | Consulta de una ancla concreta (exige parámetros) |
| 200 | `/TL/WP/current.xml` | Lista de Wallet Providers (XML, ETSI TS 119 612) |
| 200 | `/TL/WP/current.json` | La misma, en formato `tl+jwt` (la que consume la app) |
| 200 | `/TL/PID/current.xml` · `/current.json` | Lista de PID Providers |
| 200 | `/TL/QEAA/current.xml` · `/current.json` | Lista de QEAA (TTSP) |
| 200 | `/TL/PUBEAA/current.xml` · `/current.json` | Lista de Public Body EAA (UIS, notaría) |
| 200 | `/TL/RPREG/current.xml` · `/current.json` | Lista de partes usuarias (verifier) |
| 200 | `/LOTL/current.xml` | **Lista de listas** (la que descarga el validador) |
| 200 | `/LOTL/current.json` | La misma en `tl+jwt` |
| 200 | `/trust/schemerules/CIVICA` | Reglas de comunidad del esquema |
| 200 | `/trust/Svctype/{WP\|PID\|QEAA\|PUBEAA\|RPREG}` | Tipos de servicio |
| 200 | `/pki/cps` | CPS del esquema |
| 200 | `/pki/ca/{nombre}.crt` | CAs públicas (lo que prometen los certificados) |
| 200 | `/pki/crl/{nombre}.crl` | CRLs |
| 200 | `/pki/ocsp` | OCSP |

La interfaz de administración pide la **clave del operador** (`TL_OPERATOR_KEY`, en
`srv-tl-publisher/docker-compose/.env`) en un formulario. Sin ella responde `401`.

Publicar (operación privilegiada, no es de navegación):
```
POST /tl-publisher/tl-api/v1/publish/{esquema}
X-API-Key: <TL_PUBLISHER_API_KEY>
```
Sin la clave: `401`.

**Secuencias actuales:** `TL/WP=6`, `TL/PID=4`, `TL/QEAA=4`, `TL/PUBEAA=4`, `TL/RPREG=4`, `LOTL=4`.
No es un problema: cada lista se republica por separado y la LOTL apunta a las URLs `current`.

## 4. `eudi-srv-trust-validator` — validador de confianza

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/validate` | **UI del validador**: se pega una cadena y dice si es de fiar |
| 200 | `/swagger-ui` | OpenAPI interactivo |
| 200 | `/public/openapi.json` | Esquema OpenAPI |
| 200 | `/trust-validator/actuator/health` | Estado del servicio |
| 400 | `POST /trust` | API de validación (JSON: `chain` + `verificationContext`) |

Sin login. Contextos disponibles: `WalletProviderAttestation`, `WalletOrKeyStorageStatus`, `PID`,
`PIDStatus`, `QEAA`, `QEAAStatus`, `PubEAA`, `PubEAAStatus`,
`WalletRelyingPartyAccessCertificate`.

Ejemplo:
```bash
curl -sk -X POST https://civica-desarrollo.avance.org.co/trust \
  -H 'Content-Type: application/json' \
  -d '{"chain":["<base64 DER>","<base64 DER>"],"verificationContext":"PID"}'
```

## 5. `eudi-srv-wallet-provider` — Spartans (Wallet Provider único)

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/wallet-provider/.well-known/oauth-protected-resource` | Metadata RFC 9728 |
| 200 | `/.well-known/oauth-protected-resource` | La misma, en la raíz del dominio |
| 200 | `/jwks` | JWKS del provider |
| 200 | `/wallet-provider/swagger` | OpenAPI |

Sin login. Acepta los tres paquetes Android registrados:
`co.org.avance.wallet.dev`, `co.org.newwallet.app.dev`, `co.org.newwallet.app.dev.v2`, `co.org.newwallet.app.dev.auditoria` (wallet-auditoria).

## 6. `eudi-srv-issuer-pid-registraduria` — issuer de la PID

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/pid-registraduria/registraduria/solicitar` | **Pantalla para wallets de terceros**: autoservicio; cédula + nombres, apellidos y fecha de nacimiento (se homologan con la Registraduría) + selfie (se compara con la foto de referencia) |
| 200 | `/pid-registraduria/registraduria/admin/login` | **Login del panel admin** (formulario) |
| 302 | `/pid-registraduria/registraduria/admin/ciudadanos` | Listado de ciudadanos (redirige al login) |
| 302 | `/pid-registraduria/registraduria/admin/ciudadanos/nuevo` | Alta manual de ciudadano (redirige al login) |
| — | `/pid-registraduria/registraduria/admin/ciudadanos/{documentNumber}/editar` | Edición de un ciudadano |
| — | `/pid-registraduria/registraduria/admin/ciudadanos/{documentNumber}/foto` | Foto de referencia |
| — | `/pid-registraduria/registraduria/admin/ciudadanos/{documentNumber}/borrar` | Borrado |
| — | `/pid-registraduria/registraduria/admin/logout` | Cerrar sesión |
| 200 | `/pid-registraduria/.well-known/openid-credential-issuer` | Metadata OID4VCI |
| 200 | `/pid-registraduria/.well-known/oauth-protected-resource` | Metadata RFC 9728 |
| 200 | `/pid-registraduria/.well-known/jwt-vc-issuer` | Metadata JWT-VC |
| 200 | `/pid-registraduria/public_keys.jwks` | JWKS del issuer |
| 200 | `/pid-registraduria/type-metadata/urn:eudi:pid:1` | Type metadata de la PID |
| 200 | `/pid-registraduria/signing-certificates/sd-jwt-vc/urn:eudi:pid:1` | Certificados de firma SD-JWT |

**Credenciales del panel admin** (`admin` / `RegAdmin-2026`): login por formulario, la sesión
necesita el rol `ADMIN`.

**Lo que exige un token Bearer de Keycloak** (no navegable sin token):
`/pid-registraduria/swagger`, `/pid-registraduria/.well-known/openid-configuration`.

## 7. `eudi-srv-issuer-pid-uis` — issuer de credenciales académicas (PuB-EAA)

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/uis-issuer/.well-known/openid-credential-issuer` | Metadata OID4VCI |
| 200 | `/uis-issuer/.well-known/oauth-protected-resource` | Metadata RFC 9728 |
| 200 | `/uis-issuer/.well-known/jwt-vc-issuer` | Metadata JWT-VC |
| 200 | `/uis-issuer/public_keys.jwks` | JWKS del issuer |
| 200 | `/uis-issuer/type-metadata/urn:eudi:pid:1` | Type metadata de la PID |
| 200 | `/uis-issuer/type-metadata/urn:eu.europa.ec.eudi:learning:credential:1` | Type metadata de la credencial de aprendizaje |
| 404 | `/uis-issuer/type-metadata/urn:eudi:academic:credential:1` | **No existe**: el issuer UIS no publica type metadata de la credencial académica (sí la anuncia en la metadata OID4VCI) |

Sin login. **El issuer UIS no tiene todavía pantalla de solicitación** (hay que construirla).

Ofrece: `urn:eudi:academic:credential:1` (dc+sd-jwt), `urn:eu.europa.ec.eudi:learning:credential:1`
y los tipos PID de mdoc.

## 8. `eudi-srv-verifier-local-build` — retirado

Retirado el 28-oct-2026: ya no forma parte del ecosistema; está en `../eudi-srv-verifier-local-build/` (fuera del repo). Los verifiers de los casos de uso (`/verifier-<nombre>`) no dependen de él.
Los enlaces `/verifier/*` ya no existen. Los verifiers activos están en `/verifier-<nombre>/` (banco, eps, gym, …).

## 9. `srv-onboarding` — onboarding (identity proofing)

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/onboarding/actuator/health` | Estado del backend |
| 201 | `POST /onboarding/api/v1/onboarding/sessions` | Crear sesión de onboarding |
| 405 | `GET /onboarding/api/v1/onboarding/sessions` | Es un `POST`: con `GET` responde `405` (correcto) |
| 200 | `/onboarding/perdi-mi-celular.html` | «Perdí mi celular»: documento → código al contacto verificado → revoca las credenciales y la wallet (OTP simulado en pruebas) |
| 202 | `POST /onboarding/api/v1/lost-device/start` · `/confirm` | La misma función por API: `start` siempre responde igual exista o no la cédula |
| 200 | `/biometrics/health` | Servicio de biometría / OCR |

Sin login.

## 6b. `eudi-srv-issuer-pid-transito` — issuer del mDL (licencia de conducción)

Verificados el 2026-10-30 (todos 200).

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/pid-transito/transito/solicitar` | Pantalla para pedir el mDL: autoservicio; cédula + nombres, apellidos y fecha de nacimiento (se homologan con la licencia) + selfie (se compara con la foto de la licencia) |
| 200 | `/pid-transito/transito/admin/login` | Login del panel admin de licencias |
| 200 | `/pid-transito/.well-known/openid-credential-issuer` | Metadata OID4VCI (`org.iso.18013.5.1.mDL`) |
| 200 | `/idp-tra/realms/pid-transito-realm/` | Realm de Keycloak de Tránsito |

## 10. Keycloak — dos instancias

| Código | Enlace | Qué es |
|---|---|---|
| 302 | `/idp-reg/admin/master/console/` | Consola admin de la Registraduría (login por formulario) |
| 200 | `/idp-reg/realms/pid-registraduria-realm/` | Realm de la Registraduría |
| 200 | `/idp-reg/realms/master/` | Realm master de la Registraduría |
| 200 | `/idp-reg/realms/pid-registraduria-realm/.well-known/openid-configuration` | Config OIDC del realm |
| 302 | `/idp-uis/admin/master/console/` | Consola admin de la UIS (login por formulario) |
| 200 | `/idp-uis/realms/uis-issuer-realm/` | Realm de la UIS |
| 200 | `/idp-uis/realms/master/` | Realm master de la UIS |

**Credenciales de ambos** (`admin` / `password`).

Clientes OIDC registrados: `eudiw-abca` (confidencial, el que usa la app), `eudiw` y
`wallet-dev` (públicos, para pruebas desde el navegador), `uis-issuer-srv` (el resource server
de la UIS).

---

## Rutas de servicio (no navegables)

Se listan para saber qué responde y por qué.

| Ruta | Servicio | Método |
|---|---|---|
| `/wallet-provider/wallet-instance-attestation/jwk` | Emisión de WUA | `POST` |
| `/wallet-provider/key-attestation` | Emisión de Key Attestation | `POST` |
| `/wallet-provider/certification` | Certificación de la wallet | `GET` |
| `/pid-registraduria/wallet/credentialEndpoint` | Endpoint de credenciales OID4VCI | `POST` |
| `/pid-registraduria/wallet/deferredEndpoint` | Endpoint diferido | `POST` |
| `/pid-registraduria/wallet/notificationEndpoint` | Notificación de credencial | `POST` |
| `/pid-registraduria/wallet/nonceEndpoint` | Nonce | `POST` |
| `/pid-registraduria/issuer/credentialsOffer/create` | Crear oferta de credencial | `POST` |
| `/pid-registraduria/onboarding/offers` | Puente onboarding → emisión | `POST` |
| `/uis-issuer/wallet/credentialEndpoint` | Endpoint de credenciales del issuer UIS | `POST` |
| `/onboarding/api/v1/onboarding/sessions/{id}` | Estado de una sesión | `GET` |
| `/onboarding/api/v1/onboarding/sessions/{id}/contact` · `/contact/verify` · `/consent` | Contacto, OTP y consentimiento | `POST` |
| `/biometrics/api/verify/face-match` · `/liveness` · `/ocr/id-back` | Biometría y OCR | `POST` |

---

## Comprobaciones de confianza

Dos scripts, ambos sin login y con la salida como prueba:

```bash
# ¿La cadena que presenta cada emisor llega hasta el ancla que publica la lista?
srv-tl-publisher/scripts/verificar-cadenas.py

# ¿El validador da por de fiar esas mismas cadenas?
eudi-srv-trust-validator/docker/probar-ecosistema.py
```

Resultado actual: **3/3 cadenas encajan** (Wallet Provider → `Spartans Wallet Provider Root CA`,
Issuer PID → `REGISTRADURIA ISSUANCE ROOT`, Verifier → `Avance Verifier Access CA`) y el validador
responde `trusted: true` en los tres.

---

## Nota sobre el despliegue público

`https://civica.avance.org.co` sirve el **TLP back-office** (el publicador del despliegue real), no
el publicador local. Por eso ahí `/tl-publisher/tl-api/v1/lists` devuelve `Not Found`: la API de
listado es propia del publicador local. Las listas sí están en
`https://civica.avance.org.co/tl-publisher/TL/*/current.json`.

---

## Portales de ejemplo con verifier (`verifiers/`, actualizado el 2026-10-22)

Cada portal tiene su verifier propio (`/verifier-<nombre>/`, instancia en `verifiers/_comun/instancias/<nombre>`) y se levanta con
`verifiers/<nombre>/levantar.sh` o desde el panel de control. Las CA propias (EPS, notaría) se copian a `srv-tl-publisher/pki/publico/ca/`
para que el validador las tome como anclas; ver `verifiers/docs/CATALOGO-VERIFIERS.md` y `FASE-6-notaria-actos.md`.

| Código | Enlace | Qué es |
|---|---|---|
| 200 | `/discoteca/` | Discoteca: Mayoría de edad con el PID, sin guardar nada |
| 200 | `/banco/` | Banco Cívica: Ingreso y apertura de cuenta presentando el PID |
| 200 | `/contratacion/` | Contratación: Nivel de formación académica por nivel; identidad aparte y opcional |
| 200 | `/eps/` | EPS Salud Avance: Emite carné e historia clínica (QEAA); portal de afiliados, médico y administración |
| 200 | `/gym/` | Cumbre Gym: emite la tarjeta de socio (QEAA); portal del socio. Control de ingreso y egreso en `/gym/acceso` (clave de puerta) y administración en `/gym/admin` (aforo en vivo, socios, accesos) |
| 200 | `/notaria/` | Notaría Avance / Instituto: Emite 9 documentos notariales (PuB-EAA) y verifica el PID; estilo SIGNO! |
| 200 | `/aerolinea/` | Páramo Air: venta de pasajes; verifica nombre, documento, fecha de nacimiento y foto del PID, emite el tiquete (QEAA) y lo verifica en el embarque |
| 200 | `/acceso/` | Edificio Cumbre: Control de acceso: foto, nombre y documento del PID para que un guardia decida el ingreso; estilo de centro de control |
| 200 | `/pensiones/` | Fondo de Pensiones Horizonte: prueba de supervivencia; acepta la fe de vida notarial y la cruza con el PID |
| 200 | `/cancilleria/` | Cancillería (ficticia): solicitud, entrega única, reexpedición y verificación del pasaporte digital (PuB-EAA) |
| 200 | `/migracion/` | Migración Cívica: control migratorio; pide PID + pasaporte (Cancillería), cruza las comprobaciones y el funcionario decide entrada o salida |
| 200 | `/beca/` | Instituto Meridiano: Inscripción y becas: PID + credencial académica en una sola presentación |
| 200 | `/verifier-eps/` | Verifier de la EPS: Interfaz oficial del verifier de esa instancia |
| 200 | `/verifier-gym/` | Verifier del gimnasio: Idem |
| 200 | `/verifier-notaria/` | Verifier de la notaría: Idem |
| 200 | `/verifier-aerolinea/` | Verifier de la aerolínea: Idem |
| 200 | `/verifier-acceso/` | Verifier de control de acceso: Idem |
| 200 | `/verifier-beca/` | Verifier de inscripción y becas: Idem |
| 200 | `/verifier-pensiones/` | Verifier del fondo de pensiones: Idem |
| 200 | `/verifier-cancilleria/` | Verifier de la Cancillería: Idem |
| 200 | `/verifier-migracion/` | Verifier de Migración: Idem |
