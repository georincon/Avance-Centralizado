> **README original (M16).** Vigente: `../README.md`.

# Onboarding tipo Addi — backend Kotlin + Spring Boot + PostgreSQL

Aplicación de onboarding digital 100% libre (sin licencias de pago) para:
leer y almacenar los datos de la **cédula de ciudadanía colombiana** y realizar
**comparación biométrica** entre la foto de la cédula y una selfie.

La arquitectura está pensada con **proveedores intercambiables** (patrón adapter):
hoy corre con implementaciones libres (mock / OCR PDF417 / motor propio), y en
producción se cambia a un proveedor comercial (**Truora**, Verifik, Twilio) sin
tocar el resto del código.

---

## 1. Contexto: cómo lo hace Addi (investigación)

Addi (fintech BNPL colombiana) resuelve la validación de identidad con la API de
**Truora**, con 3 pilares:

| Pilar | Función |
|---|---|
| Validación documental | OCR del frente/reverso, detección de falsificación, contra **Registraduría** |
| TruFace | Prueba de vida con video del rostro |
| Face match | Compara la biometría del video contra la foto de la cédula |

Resultados reportados: fraude −80 %, conversión +60 %, validación en ~30 s.

Flujo funcional reconstruido (Addi + Verifik SmartEnroll, que documenta el mismo patrón):

```
Instalar app → Registro (celular/correo) → OTP por SMS/correo → Consentimiento
→ Foto cédula FRENTE → Foto cédula REVERSO → OCR/validación
→ Selfie + liveness → Comparación facial → Decisión
```

Alternativas del mercado evaluadas: Truora, Verifik.co, SofId, Didit, Docublock,
Facephi, Olimpia IT, AccuraScan.

### Datos de la cédula que se extraen
Número/NUIP, nombres y apellidos, fecha de nacimiento, sexo, grupo sanguíneo,
lugar/fecha de expedición, y del reverso: código **PDF417** (offsets de bytes
documentados de la Registraduría) o **QR** en la Cédula Digital nueva.

---

## 2. Stack

- **Kotlin** + **Spring Boot 4.1** (Web, Data JPA, Security, Validation, Mail)
- **PostgreSQL 16** + **Flyway** (migraciones)
- **ZXing** (Apache 2.0) para leer PDF417
- **Docker Compose** (todo libre): Postgres, MailHog (correo), MinIO (objetos), Adminer
- Cliente (Fase 2): **Android nativo en Kotlin**

## 3. Estructura

```
OnboardingAddi/
├── backend/                     API Spring Boot
│   └── src/main/kotlin/com/onboarding/
│       ├── domain/              entidades JPA + estados
│       ├── repository/          Spring Data
│       ├── provider/            interfaces + impl (mock/pdf417/http/truora)
│       ├── service/            orquestación (onboarding, OTP, verificación)
│       ├── storage/             almacenamiento de imágenes (local/S3)
│       ├── config/              seguridad
│       └── web/                 controlador REST + DTOs + manejo de errores
│   └── src/main/resources/db/migration/   V1__init.sql
└── docker-compose.yml           infra local
```

## 4. Cómo ejecutar

```bash
# 1) Infraestructura (Postgres en :5433, MailHog en :8025, MinIO en :9001)
docker compose up -d

# 2) Backend (expone :8090)
cd backend && ./gradlew bootRun

# 3) Pruebas
cd backend && ./gradlew test
```

Health: http://localhost:8090/actuator/health · MailHog: http://localhost:8025

## 5. Flujo de la API (Fase 1)

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/v1/onboarding/sessions` | Crea la sesión |
| POST | `/api/v1/onboarding/sessions/{id}/contact` | Envía OTP (SMS/correo) |
| POST | `/api/v1/onboarding/sessions/{id}/contact/verify` | Verifica el OTP |
| POST | `/api/v1/onboarding/sessions/{id}/consent` | Acepta tratamiento de datos (Ley 1581) |
| POST | `/api/v1/onboarding/sessions/{id}/documents?side=FRONT\|BACK` | Sube cada cara de la cédula |
| POST | `/api/v1/onboarding/sessions/{id}/selfie` | Sube la selfie → liveness + face match |
| GET | `/api/v1/onboarding/sessions/{id}` | Estado y datos extraídos |
| GET | `/api/v1/onboarding/sessions/{id}/audit` | Bitácora de auditoría |

Estados: `CREATED → CONTACT_PENDING → CONTACT_VERIFIED → DOCUMENTS_PENDING →
DOCUMENTS_CAPTURED → BIOMETRIC_CAPTURED → APPROVED | REJECTED | MANUAL_REVIEW | EXPIRED`.

Ejemplo rápido:

```bash
BASE=http://localhost:8090/api/v1/onboarding/sessions
SID=$(curl -s -X POST $BASE | jq -r .id)
curl -s -X POST $BASE/$SID/contact -H 'Content-Type: application/json' \
  -d '{"channel":"EMAIL","email":"juan@example.com"}'
# el OTP aparece en el log con OTP_PROVIDER=logging, o en MailHog con OTP_PROVIDER=email
```

## 6. Proveedores (cómo cambiar de libre a comercial)

Se eligen por variables de entorno (ver `application.yml`):

| Variable | Valores | Por defecto | Libre / Comercial |
|---|---|---|---|
| `OTP_PROVIDER` | logging, email, twilio | logging | logging/email (libre), twilio (comercial) |
| `OCR_PROVIDER` | mock, pdf417, truora | mock | mock/pdf417 (libre), truora |
| `BIOMETRIC_PROVIDER` | mock, http, truora | mock | mock/http (libre), truora |
| `STORAGE_PROVIDER` | local, minio | local | ambos libres |

Los adaptadores `*Truora*` y `*Twilio*` son stubs documentados: solo hay que
implementar la llamada HTTP y aportar la credencial.

## 7. Cumplimiento y seguridad (línea base)

- **Ley 1581 de 2012** (protección de datos) y Decreto 1377: consentimiento explícito (implementado).
- **SFC Circular 029**, SARLAFT/UIAF: validación contra Registraduría y listas restrictivas (pendiente fase producción).
- **Pendiente antes de producción**: auth por API Key/JWT en `/api/**`, TLS, cifrado en reposo de PII, retención y borrado de imágenes, umbrales biométricos NIST/ISO 30107-3, rate limiting.

## 8. Hoja de ruta

- [x] **Fase 1 — Backend + API**: entidades, máquina de estados, OTP, carga de documentos/selfie, OCR (mock + PDF417), biometría (mock + HTTP), storage, auditoría.
- [ ] **Fase 2 — Cliente Android Kotlin**: captura guiada de cédula (frente/reverso) y selfie, permisos de cámara, OTP con auto-relleno.
  - [x] M0: proyecto Android Kotlin + Compose compilando (APK entregado).
  - [x] M1: navegación del flujo (14 pantallas) conectada, sin lógica.
  - [ ] M2…M8: ver `ESTADO.md` (contacto/OTP, consentimiento, captura, revisión, selfie, resumen, OCR/backend).
- [ ] **Fase 3 — Endurecimiento**: motor biométrico self-hosted (InsightFace/ArcFace + MiniFASNet), MinIO, auth, paneles de revisión manual.
