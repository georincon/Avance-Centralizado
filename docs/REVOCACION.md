# Revocación de credenciales

Cómo se revocan las credenciales que emiten la Registraduría (`pid-issuer-reg`) y la UIS (`uis-issuer`),
qué se corrigió para que funcione y qué límites tiene. Última revisión: 2026-10-30.

## Cómo funciona

Cada credencial emitida lleva un **índice en una lista de estado** (Token Status List, servicio `status-list-*`).
Revocar es poner a 1 el bit de ese índice. Los verificadores consultan la lista pública y rechazan la credencial.

```
issuer ── POST /set (idx, uri, status=1) ──▶ status-list ──▶ lista pública (/status-list/<emisor>/token_status_list/...)
   │                                                                      ▲
   └── guarda en su base: formato, tipo, fechas, índice de estado, titular (subject)      │
                                                              verificador la consulta ────┘
```

El issuer guarda el **titular** (`subject`) de cada credencial: el `username` con el que se autenticó al pedirla
(en la Registraduría, el número de cédula; en la UIS, el usuario del estudiante).

## Qué toca la revocación y qué no

Hay tres cosas distintas, y revocar **solo toca las dos últimas**:

| Qué es | Dónde vive | ¿La revocación lo toca? |
|---|---|---|
| **Datos del titular** (ciudadanos, estudiantes, programas, historial) | `cedulas` (Registraduría), `datos_estudiantes`, `programas_academicos`, `historial_estudiantes` (UIS) | **No. Nunca.** |
| **Registro de credenciales emitidas** (qué se emitió, cuándo, índice de estado, titular) | `issued_credential` (Registraduría), `academic_credential` (UIS) | Solo **marca** la fila como revocada (`revoked_at`, `revocation_reason`). **No la borra.** |
| **Lista de estado pública** (un bit por credencial) | servicio `status-list-*` | Pone el bit en 1. Es lo que ven los verificadores. |

Las credenciales revocadas **no se purgan al vencer** (CIR 2024/2977 art. 5(6): el PID revocado debe seguir
accesible el tiempo que exija la ley). Las que vencen sin haber sido revocadas sí se purgan, como siempre.

**Borrar un ciudadano** es una función exclusiva del administrador de la Registraduría (botón *Borrar* del panel, con
confirmación SÍ/NO) y es **independiente de la revocación**: borrar no revoca las credenciales que ya se emitieron
(revócalas antes si corresponde) y revocar nunca borra al ciudadano. Lo comprueba `RegistraduriaAdminUiTest`.

## Tres formas de revocar

| Cómo | Quién | Qué revoca |
|---|---|---|
| Botón **Revocar credenciales** en el panel admin de la Registraduría (`/pid-registraduria/registraduria/admin/ciudadanos`) | Administrador | Todas las credenciales vigentes de ese ciudadano |
| `./revocar.sh registraduria <cédula>` · `./revocar.sh uis <usuario>` | Operación | Todas las credenciales vigentes de ese titular (API interna) |
| Trabajo periódico (cada 8 h, `issuer.revocationJob.cron`) | Automático | Credenciales cuya **wallet** o cuyo almacén de claves ya figura como inválido en su lista de estado |

**Confirmación antes de revocar** (la revocación no se puede deshacer):

- **Panel de la Registraduría:** al pulsar *Revocar credenciales* aparece un cuadro con la pregunta y los
  botones **NO** y **SÍ**. El foco empieza en **NO**; Esc o un clic fuera del cuadro también cancelan. Los botones no
  envían nada por sí solos: sin JavaScript no se puede revocar por error.
- **`revocar.sh`:** pide escribir `SI`; cualquier otra respuesta cancela. Sin terminal interactivo no revoca, salvo con
  `--si` (para automatizar).
- **UIS:** no tiene panel de administración; su revocación es solo por `revocar.sh` o por la API interna.
- La API interna **no pregunta**: quien la llama (script, panel u otro servicio) es quien debe confirmar con la persona.

La API interna es `POST /internal/v1/credentials/revoke` con cabecera `X-Api-Key` y cuerpo
`{"subject":"...","reason":"fraud","block":true}` (`reason` y `block` son opcionales). Responde
`{"revoked":N,"failed":N,"withoutStatus":N,"blocked":true|false}`. El HAProxy responde 404 a `/internal/` desde fuera de la
red docker; sin `issuer.revocation.api-key` configurada, responde 503.

## Motivo de la revocación

Se elige al revocar y **se le informa al titular** (CIR 2024/2977 art. 5(3)). Códigos: `user-request` (el titular lo
pidió), `data-changed` (cambiaron sus datos), `fraud` (posible fraude o suplantación), `legal` (decisión legal o
administrativa), `other` (por defecto). La revocación automática por wallet revocada usa `wallet-revoked`.

## Bloqueo de emisión por titular

Para que una persona a la que se le revocó una credencial por un motivo grave **no pueda pedir otra**, se puede
**bloquear la emisión** a ese titular (casilla "Bloquear también la emisión..." al revocar en el panel, `--bloquear` en
`revocar.sh`, o `"block": true` en la API).

- El bloqueo vive en una tabla propia (`blocked_subject`): **no modifica ningún dato del titular**. Se levanta con el botón
  *Desbloquear* del panel o `./revocar.sh desbloquear ...`; el historial se conserva (`released_at`).
- Un titular bloqueado conserva sus datos y sus credenciales actuales, pero **no se le emite nada nuevo**: lo rechazan
  la fuente de datos de la credencial (Registraduría y UIS), la oferta de onboarding (`403 SUBJECT_BLOCKED`) y la pantalla
  ciudadana de la Registraduría.
- Sin bloqueo, quien fue revocado **puede pedir una credencial nueva** (tendrá un índice nuevo; la anterior sigue revocada).

## Aviso de revocación al titular

La UE exige informar al titular de la revocación y de su motivo, por un canal seguro, **dentro de las 24 horas**
(CIR 2024/2977 art. 5(3)). Cada revocación (manual o automática) registra un **aviso** (`revocation_notice`) con el texto
en lenguaje claro, el motivo y el **plazo de 24 h**. Los avisos no se borran.

- **Entrega automática:** si `issuer.revocation.notification.webhook-url` apunta a un servicio propio (correo, SMS...), se
  le hace un `POST` JSON con el mensaje y el contacto del titular. El emisor **no envía correos por sí mismo**.
  Contacto: en la UIS se lee de `datos_estudiantes` (solo lectura); la Registraduría **no guarda datos de contacto**.
- **Sin canal o sin contacto**, el aviso queda **PENDIENTE** y hay que avisar al titular por otra vía y marcarlo como hecho:
  panel de la Registraduría → *Avisos de revocación* (con el plazo y la marca **VENCIDO**), o
  `./revocar.sh avisos <emisor>` y `./revocar.sh avisado <emisor> <id>`.

La clave se define con `REG_REVOCATION_API_KEY` / `UIS_REVOCATION_API_KEY` (hoy valen la de desarrollo por defecto:
**cámbiala fuera de pruebas**).

## Qué estaba roto y se corrigió

La revocación **no funcionaba en ningún punto** del ecosistema. Tres causas independientes:

1. **URL de revocación equivocada.** `ISSUER_STATUSLIST_SERVICE_REVOKE_URI` apuntaba a `/take` (que *asigna* un índice nuevo)
   en lugar de `/set`. Heredado del proyecto original. Corregido en ambos `docker-compose.yaml`.
2. **El prefijo del proxy rompía `/set` y `/get`.** El servicio de status list localiza la lista en disco con el `path`
   de la URI recibida, pero la URI pública lleva delante `/status-list/<emisor>/`, que en disco no existe: respondía 500.
   El issuer ahora envía la URI desde `/token_status_list/` en adelante
   (`MarkStatusAsRevokedWithExternalService.statusListServiceUri`).
3. **El borrado del registro local fallaba.** `DELETE ... WHERE identifier = ...` usaba una columna que no existe
   (`credential_identifier`). Error del proyecto original. Ya no se borra nada: el registro se marca como revocado.

Además, la credencial académica de la UIS se emitía **sin lista de estado** (no revocable). Ahora lleva estado, como el
carnet. Se puede volver al comportamiento anterior con `issuer.academic.sd_jwt_vc.revocable=false`. El tipo
`urn:eudi:academic:credential:1` se agregó a `ALLOWED_DOCTYPES` de `status-list-uis`.

## Límites que conviene conocer

- **Credenciales anteriores al 2026-10-30** no tienen titular guardado: no se pueden revocar por titular (solo las alcanza
  el trabajo periódico). En la Registraduría hay 640 así. Las credenciales académicas de UIS emitidas antes de esta versión
  tampoco tienen lista de estado.
- **Un verificador solo rechaza lo que consulta.** Si guarda en caché la lista de estado, verá la revocación cuando
  caduque la caché.
- Si el servicio de estado no responde, esa credencial queda **sin revocar** (`failed`) y su registro se conserva para
  reintentar. Nunca se da por revocada una credencial que no lo está.
- La revocación es **irreversible**: el bit no se vuelve a poner a 0.
- El registro de la credencial revocada **se conserva** con la fecha y el motivo (`api-request`, `admin-panel`,
  `wallet-revoked`). Además queda traza en el log (`Revocation requested for a subject (...)`).

## Pruebas

- Unitarias y de integración: `RevokeCredentialsOfSubjectTest`, `RevocationApiTest`, `IssuedCredentialSubjectTest` (base H2),
  `MarkStatusAsRevokedWithExternalServiceTest`, `AdminLoginRateLimitWebFilterTest`, `RegistraduriaAdminUiTest`,
  `RevocationNoticeServiceTest`, `SubjectBlockAndNoticeRepositoryTest`, `BlockedSubjectIssuanceTest`. `IssuedCredentialSubjectTest` comprueba
  que el registro revocado sigue en la base y que no se purga al vencer.
- De extremo a extremo (2026-10-30): índice asignado en el status-list real → `POST /internal/v1/credentials/revoke` →
  `revoked:1` → `/get` devuelve 1 → la **lista pública** marca el índice como revocado → el registro local queda marcado.
  También, en el panel real: revocar con motivo y bloqueo → aviso pendiente con plazo → "Ya le avisé" → desbloquear → borrar.
