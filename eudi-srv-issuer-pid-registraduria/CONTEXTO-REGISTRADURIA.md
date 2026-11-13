# Contexto: emulador de la Registraduría sobre este issuer

Este documento resume todo lo hablado y analizado en la sesión de `eudi-srv-pid-issuer`
antes de copiar el proyecto aquí, para que quien siga trabajando en
`eudi-srv-pid-registraduria` tenga el contexto completo sin tener que repetir el análisis.

## 1. De dónde viene este proyecto

- Es una copia de `/home/geovani/Descargas/generic/eudi-srv-pid-issuer` (el EUDI Wallet
  PID Issuer de referencia: Kotlin + Spring Boot WebFlux, emite PID/mDL/Learning Credential
  en mso_mdoc y SD-JWT VC vía OpenID4VCI).
- La copia se hizo el 2026-10-15 **sin** `.git` (el original tenía un `.git` de 686 MB,
  inflado por un lío de remotes mal configurados), y sin `build/`, `.gradle/`, `.kotlin/`,
  `.claude/`. O sea: este proyecto no tiene historial git todavía — hay que inicializarlo
  cuando se vaya a versionar por separado, **con cuidado de no apuntar el remote al repo
  equivocado** (ver sección 8).
- El proyecto original (`eudi-srv-pid-issuer`) sigue funcionando normal para su propósito
  genérico UE, corriendo en Docker local con dominio `civica-desarrollo.avance.org.co`
  (imagen `eudi-srv-pid-issuer:local`, contenedores `pid-issuer`, `keycloak-pid`,
  `postgres-pid`, `status-list-pid`, `haproxy-pid`). No se debe tocar ese proyecto para
  el trabajo de Registraduría — esto es un fork independiente.

## 2. El objetivo nuevo: emular la Registraduría Nacional

La idea es reorientar esta copia para que actúe como un **emulador de la Registraduría
Nacional del Estado Civil** (Colombia), con dos caras:

1. **Cara ciudadana — sin login real:** el usuario solo escribe su número de cédula
   (no usuario+contraseña) → el sistema emite un **PID-Registraduría** con los datos que
   la Registraduría tiene en su base para esa cédula.
2. **Cara administrativa — con login admin/password:** un panel donde se cargan
   manualmente los registros de cédulas (todos los campos del documento físico) más una
   **fotografía de referencia subida aparte** (no la foto impresa en la cédula — una foto
   nueva, pensada para comparación facial).

### Por qué: el flujo con la wallet

La wallet del usuario (proyecto `addi/OnboardingAddi`, con sus propios contenedores
`onboarding-backend-1`, `onboarding-postgres-1`, `onboarding-biometrics-1`) hace un
*onboarding* que:
- Escanea la cédula física (OCR) y extrae sus datos.
- Ya hace comparación facial selfie-vs-foto-de-cédula **por su cuenta** (biometría —
  visto en el contenedor `onboarding-biometrics-1`). **La Registraduría no necesita hacer
  biometría**, solo validar los datos alfanuméricos.
- Planea guardar esos datos en la wallet y pedirle a la Registraduría que **valide**
  si coinciden con su base de datos oficial, para luego emitir el `PID-Registraduría`.

Referencia de cómo se ve hoy esa validación en la app de onboarding (captura de pantalla
revisada en la sesión): muestra "Validación exitosa" con comparación campo a campo
(número de cédula ✓, fecha de nacimiento ✓, nombres 100%, apellidos 100%) y comparación
facial con un puntaje (ej. 51% similitud, mínimo requerido 36%). Eso es justo el tipo de
respuesta que un endpoint de validación de la Registraduría debería poder dar — menos la
parte facial, que no nos toca.

## 3. Datos de una cédula colombiana (de las muestras revisadas)

Se revisaron dos imágenes de muestra: `addi/Onboarding/muestras/cedula-000.png` (frente)
y `cedula-001.png` (reverso) — **contienen datos reales de una persona** (el propio
usuario), úsense solo como referencia de formato, no se deben compartir ni exponer.

Campos identificados y su mapeo al modelo `Pid` que ya usa este issuer
(`src/main/kotlin/.../adapter/out/attestation/pid/Pid.kt`):

| Campo de la cédula | Cara | Mapea a |
|---|---|---|
| Número de cédula | frente | `documentNumber` (en `PidMetaData`) |
| Apellidos | frente | `familyName` |
| Nombres | frente | `givenName` |
| Fecha de nacimiento | reverso | `birthDate` |
| Lugar de nacimiento (ciudad, depto) | reverso | `placeOfBirth` |
| Sexo | reverso | `sex` (ISO/IEC 5218) |
| Fecha y lugar de expedición | reverso | `issuanceDate` / `issuingJurisdiction` |
| Estatura | reverso | **no existe en el `Pid` estándar** |
| Grupo sanguíneo y RH | reverso | **no existe en el `Pid` estándar** |
| Fotografía de referencia (subida aparte, no la de la cédula) | nueva | `portrait` |
| Firma, huella índice derecho, código de barras | reverso | no se contemplan para el MVP (demasiado trabajo para el valor que aportan a un emulador de pruebas) |

## 4. Arquitectura reutilizable (hallazgos clave del código actual)

El issuer ya está diseñado para que la fuente de datos del PID sea **intercambiable**:

- `GetPidDataFromKeyCloak.kt` implementa la interfaz `GetAttestationAttributes<PidAttributes>`
  y hoy obtiene los datos consultando el API admin de Keycloak por `username`
  (`authorizationContext.username`).
- **Plan:** escribir un `GetPidDataFromRegistraduria` que implemente la misma interfaz,
  pero lea de una tabla Postgres nueva indexada por número de cédula. El resto del
  pipeline de emisión (SD-JWT VC, mso_mdoc, QR, metadata, endpoints OpenID4VCI) no
  cambia nada — es el mismo mecanismo de extensión que ya usa el proyecto hermano
  `eudi-generic-issuer-local-build` para su "Learning Credential" académico.
- `IssuerUi.kt` + `generate-credentials-offer-form.html`: hoy el formulario web **solo
  selecciona qué tipos de credencial incluir** en la oferta; siempre arma un
  `authorizationCodeGrantOffer()` (flujo completo OAuth2 vía Keycloak con redirección de
  navegador). **No existe hoy** un formulario que pida "nombre de usuario" para una
  emisión directa, ni un flujo *pre-authorized_code* funcional (el DTO `PreAuthorizedCodeTO`
  existe en `port/input/VCI.kt`, pero no está cableado — `CreateCredentialsOffer` nunca lo
  genera).
- `ConfigureSecurity.kt` define las rutas públicas (`permitAll`) vs protegidas — aquí
  habrá que agregar las rutas del panel admin como protegidas con su propio login
  (separado de Keycloak).

## 5. El punto más delicado: "sin login" para el ciudadano

Dos caminos posibles (**sin decidir todavía** — ver sección 6):

- **Opción A (recomendada):** configurar el *authentication flow* de Keycloak para
  quitar el paso de contraseña (solo "Username Form"). El panel admin, al crear/editar
  una cédula, sincroniza un usuario "fantasma" en Keycloak (username = número de cédula,
  sin password real) vía el mismo API admin que ya usa `GetPidDataFromKeyCloak`, pero en
  modo escritura. Reutiliza el 100% del flujo OAuth2/OpenID4VCI actual, riesgo bajo, poco
  código nuevo.
  - *Advertencia de seguridad a aceptar conscientemente:* cualquiera que conozca un
    número de cédula válido podría pedir ese PID — no hay verificación real de identidad
    en este paso. Es aceptable porque es un emulador de pruebas y la verificación real
    (biometría) ya la hace la wallet aparte.
- **Opción B (más "correcta" pero mucho más trabajo):** implementar de verdad el flujo
  *pre-authorized_code* de OpenID4VCI (sin tocar Keycloak para nada), con un formulario
  propio. Requiere construir emisión de tokens propia — infraestructura nueva considerable.

## 6. Decisiones pendientes (todavía sin responder por el usuario)

Antes de escribir código hay que confirmar:

1. **Flujo sin login:** ¿Opción A (Keycloak sin contraseña) u Opción B (pre-authorized_code real)?
2. **Campos extra sin equivalente en el PID estándar** (estatura, grupo sanguíneo/RH):
   ¿se quedan solo en la base de datos de la Registraduría (recomendado, más compatible
   con verificadores estándar), o se crea un tipo de credencial custom "CedulaCiudadania"
   (como el patrón de Learning Credential) que incluya todo?
3. **Dónde vive la tabla de cédulas:** ¿tabla nueva en el Postgres actual (`pid_issuer`,
   recomendado, un contenedor menos) o un Postgres separado dedicado (como el patrón
   `postgres-uis` del proyecto académico)?

## 7. Vistas / diseño

Reutilizar el estilo visual de `/home/geovani/Descargas/generic/eudi-generic-issuer-local-build`
(Bootstrap + `src/main/resources/public/css/main.css` + plantillas Thymeleaf en
`src/main/resources/templates/`) para:
- Pantalla ciudadana: "ingresa tu número de cédula".
- Panel admin: login, listado de cédulas, alta/edición de registro con subida de foto.

## 8. Ojo con git / remotes (lección aprendida en la sesión anterior)

En `eudi-srv-pid-issuer` hubo un incidente serio: el `origin` apuntaba por error al repo
de una app Android totalmente distinta (`eudi-app-android-wallet-ui`), con historial sin
relación — a punto de sobrescribirlo. Se corrigió apuntando a
`https://github.com/georincon/eudi-srv-pid-issuer.git` (el repo correcto). **Al inicializar
git en este proyecto nuevo, verificar con cuidado a qué repo apunta cualquier remote antes
de hacer push** — idealmente crear un repo nuevo dedicado (ej. `eudi-srv-pid-registraduria`)
en vez de reutilizar uno existente a ciegas.

## 9. Próximos pasos sugeridos (orden de trabajo)

1. Responder las 3 decisiones pendientes (sección 6).
2. Diseñar el esquema de la tabla `cedulas` (migración SQL, carpeta `postgresql/schema`).
3. Implementar `GetPidDataFromRegistraduria` (adapter `GetAttestationAttributes<PidAttributes>`).
4. Implementar el flujo de login simplificado según la opción elegida.
5. Panel admin: login admin/password + CRUD de cédulas + subida de foto de referencia.
6. Adaptar las vistas (basadas en `generic-issuer-local-build`).
7. Endpoint de validación de datos para que lo consuma el backend de onboarding
   (autenticado con API key, como ya se hace con `status-list`).
8. Pruebas end-to-end: alta de cédula desde el panel → emisión de PID-Registraduría →
   validación contra el onboarding.
