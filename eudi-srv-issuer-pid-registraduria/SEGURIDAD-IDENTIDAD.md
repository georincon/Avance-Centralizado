# Quién puede pedir qué PID (modelo de seguridad)

**Principio:** la cédula no es un secreto. La identidad se prueba **una vez, fuera de Keycloak**, y se convierte en un
**ticket** (`issuer_state`) de un solo uso, ligado a una única cédula. Keycloak solo autentica con un ticket válido:
no hay usuarios con contraseña ni formulario de login en los clientes de wallet.

| Vía | Quién prueba la identidad | Cómo se crea el ticket |
|---|---|---|
| Wallet propia | Onboarding aprobado + el issuer vuelve a comprobar los datos con la tabla `cedulas` y compara la **selfie de la sesión con la foto de referencia del registro** (no con la de la tarjeta fotografiada, que podría ser falsa) | `POST /onboarding/offers`: la cédula sale de la sesión aprobada. La selfie no sale del onboarding: el issuer le manda la foto del registro a `/api/v1/internal/sessions/{id}/compare-selfie` (`X-Api-Key`, `ONBOARDING_INTERNAL_API_KEY`). Si no coincide: 403 `FACE_MISMATCH`; si no se puede comprobar: 502 (no se emite). Vigencia 10 min. |
| Wallet de terceros (`/registraduria/solicitar`) | Autoservicio, sin administrador: datos escritos homologados con la tabla `cedulas` + selfie comparada con la foto de referencia (servicio de biometría del onboarding) | Si ambos pasan, ticket de 5 min en el QR. Tope de 5 fallos/hora por cédula, además del límite por IP. La selfie no se guarda. |

Keycloak (`pid-registraduria-realm`): flujo `issuer-state-browser` con un único paso obligatorio (autenticador por ticket),
asignado a `newwallet-app`, `wallet-dev`, `eudiw` y `eudiw-abca`; auto-registro apagado. Sin ticket, o con uno vencido o usado: 403.
`docker-compose/keycloak/apply-issuer-state-binding.sh` lo deja así (idempotente; `up.sh` lo ejecuta).
`docker-compose/keycloak/sincronizar-ciudadanos.py` crea/actualiza los usuarios de Keycloak desde la tabla `cedulas` (sin contraseña).

Límites conocidos: la comparación facial NO es prueba de vida (una foto de la persona podría pasar) y su umbral no está calibrado; el tope por cédula permite que un tercero bloquee temporalmente a un titular; tickets y códigos de activación viven en memoria (un reinicio del issuer los invalida; se generan de nuevo);
el QR de `/solicitar` lo puede usar quien lo vea durante sus 5 minutos de vigencia.
`/transito/solicitar` (emisor de tránsito) NO tiene este control todavía.

# UIS (credenciales académicas)

Mismo principio: **nada de contraseñas**; el `username` en `uis-issuer-realm` es el número de identificación del estudiante y
Keycloak solo autentica con un ticket (`issuer_state`) de un solo uso (`apply-issuer-state-binding.sh`, `sincronizar-estudiantes.py`).

| Estudiante | Cómo prueba su identidad | Cómo se crea el ticket |
|---|---|---|
| Con PID (CC) | Presenta su PID desde la wallet; el verifier `verifier-uis` valida firma, emisor, estado y titularidad | `POST /uis-issuer/issuer/api/ingreso` (la wallet) o QR en `/issuer/login` (otra wallet) → programas → `POST /issuer/api/ofertas` |
| Sin PID (CE, PAS, PPT, PEP) | Código de activación del registro de la UIS (panel de estudiantes) | Portal `/issuer/login` con número de identificación + código |

Panel de estudiantes: `/uis-issuer/uis/admin/estudiantes` (alta y edición de `datos_estudiantes` y `historial_estudiantes`; guardar
crea/actualiza el usuario de Keycloak). Migración de datos: `docker-compose/postgresql/migrations/uis_academic-03-usuario-igual-cedula.sql`.
Pendiente: borrar las claves en texto plano (`uis_academic-02-…`) ahora que ya no se usan.

# Tránsito (mDL)

Igual que la Registraduría: `/transito/solicitar` pide datos + selfie (comparada con `licencias.foto_referencia`) y entrega un QR con ticket
de 5 min; el onboarding también compara su selfie con la foto de la licencia (403 `FACE_MISMATCH`). Keycloak (`pid-transito-realm`) solo
autentica con ticket (`apply-issuer-state-binding.sh`, `sincronizar-licencias.py`), sin auto-registro.

# Claves y etiquetas de imagen

- Los tres Keycloak usan etiquetas de imagen propias (`keycloak-abca-reg`, `-uis`, `-tra`): antes compartían una y reconstruir uno cambiaba
  la extensión del ticket de los otros. Cada pila trae su copia de `ext/issuer-state-authenticator` (mantenerlas iguales).
- La clave interna del onboarding (`ONBOARDING_INTERNAL_API_KEY`) vive en el `.env` de la raíz (no versionado, `/.env` en `.gitignore`);
  `up.sh` ya no usa el valor por defecto de desarrollo.

# Acceso desde Internet y administración

El ecosistema es accesible desde Internet por el dominio. Las superficies de administración solo desde la red local, la salida del sitio y
las IP de `srv-nw-nginx/ips-admin-permitidas.conf` (ver el README de `srv-nw-nginx`). El panel de control ya no detiene los Keycloak de otros
emisores al «Activar consola»: con el login por ticket los tres deben estar siempre en pie.
