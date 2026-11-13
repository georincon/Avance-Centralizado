> **Documento histórico (hitos M0–M16).** Se conserva como referencia; el estado vigente está en `../ESTADO.md`.
> Los datos personales del titular se reemplazaron por marcadores (`<TITULAR>`, `<CEDULA>`, `<FECHA_NAC>`).

# ESTADO DEL PROYECTO — Onboarding tipo Addi

> Documento de continuidad. Última sesión: **domingo 2026-10-11 (noche)**.
> Al reanudar, empezar por la sección **PRÓXIMO PASO**.

---

## OBJETIVO
App de onboarding (misma filosofía que Addi, **sin la parte de crédito/préstamo**) para
capturar y almacenar los datos de la **cédula de ciudadanía colombiana** y hacer
**comparación biométrica** (foto cédula vs selfie). Se construye primero la **app Android
en Kotlin** paso a paso; el **backend Spring Boot** ya existe (Fase 1 completa y verificada).

Directivas del usuario:
- Dejar FUERA el cálculo de crédito/preaprobado.
- Sin WhatsApp ni SMS.
- Solo herramientas libres/gratuitas para pruebas.
- El agente propone los pasos de forma entendible.
- **Versionar cada entrega** (ver sección Versionamiento).
- PII real se mantiene en privado; las capturas de referencia están anonimizadas.

Datos de prueba autorizados: los propios del usuario (el titular). Cédula escaneada y selfie
en `Addi_imagenes/` (PII real, privado) y material de prueba en `muestras/`.

---

## DÓNDE QUEDAMOS
- **M0 COMPLETO**: proyecto Android Kotlin + Compose creado y compilando.
- **M1 COMPLETO**: navegación del flujo (pantallas) conectada.
- **M2 COMPLETO**: captura de datos (celular, OTP, cédula, nombres, correo, OTP correo,
  contraseña), OTP **simulado** (sin red) y **persistencia en almacenamiento interno**
  (`filesDir/onboarding.json`).
- **M3 COMPLETO**: paso de **consentimiento Ley 1581** (casilla obligatoria).
- **M4 COMPLETO**: **captura de cédula frente/reverso** con la cámara del sistema;
  guarda imagen a resolución completa en `filesDir/images/` (FileProvider) + miniatura
  y "Volver a tomar".
- **M5 COMPLETO (rehace M4/M6)**: cámara DENTRO de la app (**CameraX**) con vista previa
  y **guía superpuesta** (marco de cédula con recuadros de foto y huella para frente/reverso;
  **óvalo** para la selfie). Incluye la **captura de selfie** (antes en blanco). Solicita
  permiso de cámara en la app. La "cámara del sistema" (FileProvider) quedó reemplazada.
- Backend Fase 1 COMPLETO y verificado (ver README.md).
- **M6 COMPLETO**: overlay del **modelo de cédula** (analizado desde `modelo.png`:
  2 tarjetas, frente 466×271 / reverso 465×269) como **línea gris transparente**
  (`res/drawable/guide_front.png`, `guide_back.png`). Corregido: "Volver a tomar"
  ya no dispara la captura (vuelve a vista previa). Herramientas en `tools/`.
- **M7 COMPLETO**: captura de cédula a **pantalla completa y en horizontal** (la app gira
  sola); el **modelo se estira** para cubrir toda la pantalla; obturador circular flotante.
  Selfie en vertical con óvalo. "Repetir" vuelve a vista previa (no dispara la foto).
- **M8–M11 (correcciones de cámara) COMPLETO**:
  - Líneas del modelo **blancas, delgadas y casi transparentes** (alfa máx 60/255; se
    erosiona el trazo para adelgazarlo ~a la mitad). Marco al **≤88% del ancho**, centrado,
    **sin deformar**; en horizontal deja **margen mínimo arriba/abajo** (`BoxWithConstraints`).
  - **Cámara horizontal** para la cédula (selfie vertical). **No entra directo**: pantalla de
    instrucciones con botón "Tomar foto"/"Tomar selfie" antes de cada cámara.
- **M12 COMPLETO**: el **resumen** permite **revisar y editar** datos (celular, cédula,
  nombres, apellidos, correo) antes de validar.
- **M13 COMPLETO (integración con backend por red local)**: la app habla con el backend
  (`http://192.168.1.7:8095`). Al llegar a **Procesando** ejecuta el flujo real (crear
  sesión → contacto/OTP → consentimiento → subir frente/reverso → selfie → consultar
  resultado). **Resultado** muestra decisión + datos del OCR + biometría.
  - **OCR real**: el backend decodifica el **PDF417 del reverso** (ZXing) y devuelve
    número, nombres, apellidos, nacimiento, etc. **Probado y verificado** con la cédula
    real (número <CEDULA>, <TITULAR>, <FECHA_NAC>) → APPROVED.
  - Biometría: aún **simulada** (provider mock, SUCCESS 0.91/0.95).
  - Backend: nuevo `onboarding.otp.expose-code` (dev) para que la app complete el OTP.
- APKs entregados hasta `0.14.0-M13`; solo el último queda en el teléfono.

### Verificado funcionando
- `./gradlew deliver` → BUILD SUCCESSFUL (M2, M3, M4).
- M0/M1 abren y navegan en el Redmi 10.
- M2–M4 compilan; **falta probarlos en dispositivo**.

### Estado del teléfono
- Conectado por USB (MTP). `adb` NO funciona: la **Depuración USB está desactivada**.
- Transferencia por **MTP** (montaje GVFS). **Requiere que el teléfono esté DESBLOQUEADO**;
  si está bloqueado, las operaciones MTP se cuelgan.
- Carpeta destino: **`Almacenamiento interno/Download/onboarding/`**.
  - Ahí queda **solo el último APK** (`0.13.0-M12.apk`); los anteriores se borran del teléfono.
  - Copiar con `gio copy` (el `cp` de GVFS falla ~128 KB). Si MTP se cuelga: desbloquear el
    teléfono y remontar con `gio mount "mtp://Xiaomi_Redmi_10_2022_85483ad00603/"`.

---

## VERSIONAMIENTO (convención)
- Archivo de versión: `android/app/build.gradle.kts` → `val appVersion = "X.Y.Z-Mn"`.
  - `X.Y.Z` = semver simple; `-Mn` = hito. `versionCode` se incrementa en 1 por entrega.
- Cada entrega: `./gradlew deliver` copia el APK a `deliveries/onboarding-<version>.apk`.
- Registro histórico (última arriba): `deliveries/versiones.txt`.
- La app muestra su versión en pantalla.
- **Última versión entregada: `0.15.0-M14`** (siguiente: `0.16.0-M15`).
- Entregas actuales en `deliveries/`:
  - `onboarding-0.1.0-M0.apk` … `onboarding-0.15.0-M14.apk`  ← última
- En el teléfono (`Download/onboarding/`) solo queda la última: `0.15.0-M14.apk`.

---

## PRÓXIMO PASO  →  M15
Pulir: limpiar estado al reiniciar onboarding, evitar mostrar imágenes antiguas, y añadir un botón "Nueva validación" en Resultado. También evaluar (si quieres) pasar a biometría real (motor self-hosted) sin romper PDF417.

---

## BACKEND — CÓMO CORRERLO PARA LA APP (red local)
> El puerto **8090 lo ocupa otro proyecto** (`veterinaria-frontend`). Usamos **8095**.
```bash
cd <proyecto>
docker start onboarding-postgres onboarding-mailhog      # Postgres :5433, MailHog :8025
cd backend
setsid env SERVER_PORT=8095 OCR_PROVIDER=pdf417 OTP_EXPOSE_CODE=true \
  ./gradlew bootRun --no-daemon < /dev/null > /tmp/opencode/onb-backend.log 2>&1 &
# Verificar:  curl http://192.168.1.7:8095/actuator/health   -> {"status":"UP"}
```
La IP del PC es **192.168.1.7** (WiFi `wlp2s0`). La app apunta a esa IP en
`android/app/build.gradle.kts` → `buildConfigField("String","BACKEND_BASE_URL", ...)`.
El teléfono y el PC deben estar en la **misma red WiFi**.

---

## CÓMO COMPILAR / ENTREGAR / INSTALAR

```bash
# Compilar + generar entrega versionada
cd <proyecto>/android
./gradlew deliver          # -> deliveries/onboarding-<version>.apk

# Pasar el APK al teléfono (teléfono DESBLOQUEADO y conectado por MTP)
M="/run/user/1000/gvfs/mtp:host=Xiaomi_Redmi_10_2022_85483ad00603/Almacenamiento interno compartido"
gio mkdir "$M/Download/onboarding"
gio copy deliveries/onboarding-<version>.apk "$M/Download/onboarding/"

# En el teléfono: Descargas/onboarding/ -> tocar el APK -> Instalar
```

Si MTP no responde: desbloquear el teléfono. (Alternativa futura: activar
Opciones de desarrollador + Depuración USB para instalar con `adb install -r`.)

### Backend (ya funciona, por si se necesita)
```bash
cd <proyecto>
docker compose up -d                 # Postgres :5433, MailHog :8025, MinIO :9001
cd backend && ./gradlew bootRun      # API en :8090
```

---

## ESTRUCTURA ANDROID (actual)
```
android/
├── settings.gradle.kts, build.gradle.kts, gradle.properties
├── gradlew + gradle/wrapper (Gradle 9.7.1)
├── local.properties            (sdk.dir=~/Android/Sdk)
└── app/
    ├── build.gradle.kts        (version, deps, tarea `deliver`)
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/values/themes.xml
        └── java/com/onboarding/app/
            ├── MainActivity.kt               (crea OnboardingState + NavHost)
            ├── data/
            │   ├── OnboardingData.kt          (modelo de datos capturados)
            │   └── OnboardingStore.kt         (persistencia JSON en filesDir)
            ├── navigation/OnboardingFlow.kt   (enum Step + NavHost, 15 pantallas)
            ├── ui/
            │   ├── OnboardingScaffold.kt      (StepScreen: título/subtítulo/botón/atrás/versión)
            │   ├── OnboardingState.kt         (estado + persistencia + OTP simulado)
            │   └── screens/
            │       ├── OnboardingScreens.kt   (Phone, Otp, Cedula, Names, Email,
            │       │                            EmailOtp, Password, Consent, Summary)
            │       └── DocumentCaptureScreen.kt (captura cédula frente/reverso + miniatura)
        └── res/xml/file_paths.xml            (FileProvider para imágenes internas)
```

### Flujo de pantallas (orden) — enum `Step`
1. `phone` Ingresa tu número de celular  ✅ captura
2. `otp` Ingresa tu código (celular)  ✅ captura + OTP simulado
3. `profile_intro` Tu perfil es único
4. `cedula` Ingresa tu cédula  ✅ captura
5. `names` Cuéntanos sobre ti  ✅ captura (nombres/apellidos)
6. `email` Ingresa tu correo electrónico  ✅ captura
7. `email_otp` Ingresa tu código (correo)  ✅ captura + OTP simulado
8. `summary` Antes de crear tu usuario  ✅ datos EDITABLES
9. `password` Crea tu contraseña  ✅ captura
10. `consent` Autoriza el tratamiento de tus datos (Ley 1581)  ✅ casilla
11. `doc_front` Instrucciones + botón "Tomar foto"  →  `doc_front_camera` (cámara HORIZONTAL, marco guía)
12. `doc_back` Instrucciones + botón "Tomar foto"  →  `doc_back_camera` (cámara HORIZONTAL, marco guía)
13. `selfie` Instrucciones + botón "Tomar selfie"  →  `selfie_camera` (cámara frontal VERTICAL, óvalo)
14. `processing` Ya casi está
15. `result` Resultado

---

## DECISIONES TÉCNICAS CLAVE (no repetir errores)
- **AGP 9.4.1 trae Kotlin integrado**: NO aplicar `org.jetbrains.kotlin.android`
  (rompe el build). Solo `com.android.application` + `org.jetbrains.kotlin.plugin.compose` 2.4.0.
- `buildFeatures { compose = true; buildConfig = true }`.
- compileSdk 37, minSdk 24, targetSdk 37, buildToolsVersion 36.0.0, namespace `com.onboarding.app`.
- **Cámara**: se usa **CameraX 1.4.2** dentro de la app (`PreviewView` + `ImageCapture`)
  para poder dibujar la guía superpuesta (marco de cédula / óvalo). Requiere permiso
  `CAMERA`. Se descartó la cámara por Intent del sistema (no permite superponer guías).
- Captura guarda en `filesDir/images/`; también hay `FileProvider` (`res/xml/file_paths.xml`,
  authority `com.onboarding.app.fileprovider`) por si se necesita compartir imágenes.
- Sin `material-icons`: el botón atrás usa el carácter "\u2190" (evita dependencia extra).
- Compose BOM 2026.09.00; navigation-compose 2.10.1; core-ktx 1.19.0; lifecycle 2.11.0;
  activity-compose 1.13.0.
- SDK en `~/Android/Sdk` (platforms `android-37.0` con symlink `android-37`;
  build-tools 36.0.0; `cmdline-tools` está vacío).
- Al reanudar, `java`/Gradle wrapper ya funcionan; usar `--no-daemon` si molesta el daemon.

---

## ARCHIVOS Y RECURSOS
- `deliveries/` → APKs versionados + `versiones.txt` (registro).
- `android/` → proyecto Android.
- `backend/` → API Spring Boot (Fase 1 completa).
- `docker-compose.yml` → infra local.
- `README.md` → doc del backend y arquitectura (adapters, API, cumplimiento).
- `Addi_imagenes/` → capturas reales + cédula escaneada + selfie (PII, PRIVADO).
- `Addi_imagenes_anon/` → 26 capturas anonimizadas (referencia de UI).
- `muestras/cedula-000.png` (frente), `cedula-001.png` (reverso) → material de prueba.
- Herramientas rootless (en /tmp, pueden borrarse): `/tmp/opencode/tess` (Tesseract 5.3.4 spa+eng),
  `/tmp/opencode/pylibs` (opencv 4.10 + numpy), `/tmp/opencode/models` (cascadas Haar),
  `/tmp/opencode/anonymize.py`, `/tmp/opencode/ocr_anon/`.

## CUMPLIMIENTO
- Ley 1581/2012 (consentimiento) implementado en backend; SFC Circular 029 y
  SARLAFT/UIAF quedan para producción. Para KYC real en Colombia se requiere proveedor
  pago + Registraduría; el stack libre es para prototipo/pruebas.
