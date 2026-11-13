# Onboarding

Servicio de **onboarding de usuario** (*identity proofing*): captura del documento, OCR, selfie
con liveness, verificación de contacto (OTP) y consentimiento.

Vive en su propio proyecto, y no dentro de una wallet, porque es **transversal**: cualquier
wallet del ecosistema lo usa para dar de alta al usuario antes de pedir credenciales. Así no se
duplica al clonar o re-clonar una wallet, y puede evolucionar y versionarse por separado.

> Antes estaba duplicado en tres sitios (`new-wallet/onboarding`, `Avance/addi-onboarding`,
> `Avance_II/OnboardingAddi`), sin versionar en git. Ahora hay una sola copia.

## Acceso

- Público: `https://civica-desarrollo.avance.org.co/onboarding` (a través del proxy único
  `Avance/nw-nginx`).
- API: `POST /api/v1/onboarding/sessions` y `GET /api/v1/onboarding/sessions/{id}`.
- Biometría (rostro/OCR): `https://civica-desarrollo.avance.org.co/biometrics` (interno).

## Composición

| Servicio | Función | Puerto host |
|---|---|---|
| `onboarding-backend` | API Spring Boot | — (vía proxy) |
| `onboarding-biometrics` | Visión: rostro + OCR (Python/OpenCV) | — (vía proxy) |
| `onboarding-postgres` | Base de datos | — (privada, nunca enrutada) |

## Uso

```bash
./up.sh      # crea la red compartida si falta y levanta el stack
./down.sh    # detiene
```

Variables opcionales (`.env`):

| Variable | Por defecto | Para qué |
|---|---|---|
| `ONBOARDING_DB_PASSWORD` | `onboarding` | Contraseña del Postgres |
| `OTP_EXPOSE_CODE` | `true` | Solo desarrollo: devuelve el código OTP en la respuesta |
| `OTP_HASH_PEPPER` | `dev-pepper-change-me` | Pepper del hash del OTP (cámbialo en cualquier entorno real) |

## «Perdí mi celular» (revocación sin la wallet)

Permite pedir la revocación de las credenciales sin usar la wallet perdida (CIR 2024/2979 art. 6(3)(b)(c)):
medio independiente = el contacto verificado en el onboarding.

- Página: `https://civica-desarrollo.avance.org.co/onboarding/perdi-mi-celular.html` (se sirve desde `webroot/`, montado en `/webroot`).
- `POST /api/v1/lost-device/start` `{documentNumber}` → siempre 202 con mensaje genérico (sin enumeración); emite un OTP al contacto de la sesión APROBADA más reciente.
- `POST /api/v1/lost-device/confirm` `{documentNumber, code}` → verifica el OTP y llama al emisor de la Registraduría
  (`/internal/v1/credentials/revoke`, `wallet:true`). Cualquier fallo de OTP responde 401 «Codigo invalido o vencido».
- Límites: 3 códigos por documento/hora y 10 por IP/hora (en memoria; se reinician con el servicio) + los 5 intentos del OTP. 429 al superarlos.
- Config: `ONBOARDING_LOSTDEVICE_ISSUER_URL` (endpoint completo) y `ONBOARDING_LOSTDEVICE_ISSUER_APIKEY` (en el compose viene de
  `REG_REVOCATION_API_KEY`, con la clave de desarrollo por defecto). Sin ambas, los endpoints responden 503. El backend alcanza al emisor por la red `avance-eudi`.
- Auditoría (`audit_event`): `LOST_DEVICE_CODE_SENT`, `LOST_DEVICE_CONFIRM_FAILED`, `LOST_DEVICE_REVOKED`, etc., sin código, sin contacto y con la cédula enmascarada.

**Aviso honesto:** el OTP del onboarding es **simulado en desarrollo** (`OTP_PROVIDER=logging`, y con `OTP_EXPOSE_CODE=true` el código viaja en la
respuesta). Mientras no se conecte un proveedor real de SMS/correo, **cualquiera que vea el código puede revocar** las credenciales de una cédula
con sesión aprobada. La seguridad real depende de ese proveedor; además, con `OTP_EXPOSE_CODE=false` el flujo exige leer el log del backend.
La revocación es irreversible. La IP del cliente se toma de la última entrada de `X-Forwarded-For` (la añade nw-nginx).
