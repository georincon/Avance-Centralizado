# ESTADO DEL PROYECTO — Onboarding con validación de cédula

> Última actualización: 2026-10-13. Reanudar por **PRÓXIMOS PASOS**.

## OBJETIVO
App Android (Kotlin) + backend (Spring Boot) que:
1. Lee la **cédula colombiana** (PDF417 del reverso) y **compara** esos datos con los que el usuario escribió.
2. Compara la **selfie con la foto de la cédula** (1:1) como indicio de que el documento es del usuario.
3. Decide: `APPROVED` / `MANUAL_REVIEW` / `REJECTED`.

Sin parte de crédito. Solo herramientas libres. Datos de prueba: los del propio usuario (privados, nunca en git).

## ARQUITECTURA (3 procesos + Postgres)
| Pieza | Tecnología | Puerto |
|---|---|---|
| App | Android Kotlin + Compose + CameraX | — |
| Backend | Spring Boot 4 + JPA + Flyway + ZXing | 8095 |
| Visión (rostro + OCR del reverso) | Python (FastAPI) + OpenCV YuNet/SFace + RapidOCR | 8100 |
| BD | PostgreSQL 16 | 5433 |

Los tres servicios corren con `docker-compose/docker-compose.yaml`.

El teléfono llega al backend **por WiFi** a la IP del PC (se detecta al compilar, ver `detectBackendUrl()` en `android/app/build.gradle.kts`). Alternativa por USB: `tools/dev-tunnel.sh` + `-PbackendHost=127.0.0.1`.

## FLUJO
Celular → código (simulado en pantalla) → Cédula → Nombres + fecha de nacimiento → Correo → código (simulado) → Resumen (editable) → Consentimiento (Ley 1581) →
foto frente → foto reverso → selfie → Procesando → Resultado.

Backend, en orden: sesión → contacto/OTP (auto con `devCode` en dev) → consentimiento → **`declared-data`** →
frente + reverso (lee PDF417, valida campos) → selfie (calidad + face match) → decisión.

## REGLAS DE DECISIÓN (`VerificationService.decide`)
- REJECTED: documento ilegible/inválido, número de cédula distinto, fecha de nacimiento distinta, selfie sin calidad, rostro no coincide (< 0.363).
- MANUAL_REVIEW: nombres con similitud < 0.75, rostro entre 0.363 y 0.40, OCR de baja confianza.
- APPROVED: lo demás.
- Si el reverso no se lee, la sesión vuelve a `DOCUMENTS_PENDING` y el usuario repite la foto.

## RESULTADOS VERIFICADOS (2026-10-13, `tools/e2e.py`, 8 escenarios OK)
Honesto → APPROVED · cédula distinta → REJECTED · fecha distinta → REJECTED · nombres distintos → MANUAL_REVIEW ·
selfie de otra persona (coseno −0.06) → REJECTED · reverso girado 90°/180° → APPROVED · reverso ilegible → DOCUMENTS_PENDING.
Rostro del titular vs. su cédula: coseno 0.44–0.53 (umbral 0.363).

## LIMITACIONES CONOCIDAS (honestidad)
- **No hay anti-suplantación.** La "prueba de vida" es solo *calidad de selfie* (un rostro, tamaño, nitidez, luz). Una foto de pantalla o impresa podría pasar. Falta un modelo PAD (p. ej. MiniFASNet).
- **Umbrales sin calibrar.** Salen de pocas muestras (1 titular, 1 extraño). Hay que calibrar con más personas/fotos reales antes de confiar en ellos.
- **El PDF417 no prueba autenticidad**: cualquiera puede generar un PDF417 con datos inventados. Para validar de verdad hace falta consultar a la Registraduría (proveedor externo).
- Solo cédula amarilla con PDF417; la Cédula Digital (QR) no está soportada.
- Pruebas sobre fotos reales tomadas con la cámara del celular: **pendientes** (las pruebas E2E usaron imágenes de archivo).
- Sin autenticación en `/api/**`, sin TLS, sin cifrado en reposo ni borrado de imágenes (solo prototipo).

## CÓMO CORRER TODO
```bash
cd OnboardingAddi/docker-compose && docker compose up -d --build   # Postgres + biometría + backend (ver docker-compose/README.md)

# App en el celular (USB, depuración activa)
cd ../android && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
# La app apunta a la IP del PC detectada al compilar (celular y PC en la misma WiFi).
# Si cambia la IP: recompilar, o forzarla con ./gradlew assembleDebug -PbackendHost=<ip>
```
Prueba sin celular: `tools/e2e.py` (ver cabecera del script; los datos personales van por variables de entorno).

## VERSIONES
`android/app/build.gradle.kts` → `appVersion`. Actual: **0.19.0-M18** (versionCode 19).
Historial anterior (M0–M16) en el repo original; este repo arranca en git desde M16.

## DÓNDE ESTÁ CADA COSA
| Carpeta | Contenido | ¿En git? |
|---|---|---|
| `android/`, `backend/`, `biometrics/`, `docker-compose/` | código y despliegue | sí |
| `docs/` | historial M0–M16, README original, `versiones.txt`, investigación inicial (PDF) | sí |
| `deliveries/` | APK más reciente (`onboarding-<versión>.apk`, se genera con `./gradlew deliver`) | no |
| `muestras/` | capturas reales de cámara (`capturas_camara/`) y una imagen de referencia — **datos personales** | no |
| `../imagenes/` (fuera del proyecto) | cédula escaneada (`muestras/`), `Addi_imagenes/` y `Addi_imagenes_anon/` — **datos personales** | no |

`tools/e2e.py` recibe las rutas de las imágenes por variables de entorno (ver su cabecera).

## QUÉ SE ELIMINÓ Y POR QUÉ
Pantalla de contraseña (se guardaba en claro; el OTP simulado de celular/correo SÍ se conserva, por decisión del usuario),
recorte ciego de la foto (podía cortar el PDF417), mocks de OCR/biometría (aprobaban todo), stubs Truora/Twilio,
MinIO/MailHog/Adminer del compose, `allowBackup`.

## PRÓXIMOS PASOS
1. **Prueba con cámara real en el celular** (desbloquear el teléfono): validar captura de frente/reverso/selfie y ajustar guías.
2. Calibrar umbrales con más fotos (distintas luces, ángulos, personas).
3. Anti-suplantación (PAD) para la selfie.
4. Autenticación de la API, TLS, retención y borrado de imágenes, rate limiting.
5. Validación contra Registraduría vía proveedor, si se quiere autenticidad real del documento.
6. Pruebas automáticas (hoy solo existe `contextLoads`).
