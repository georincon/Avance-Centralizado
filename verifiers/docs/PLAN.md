# Plan: verifiers del ecosistema EUDI de Avance

Fecha: 2026-10-21 · Dominio de este ecosistema: `civica-desarrollo.avance.org.co`
Wallet de prueba: `app-new-wallet` (v2)

## 0. Contexto y aclaración de dominios

- **Ambos dominios son de pruebas.** Todavía no hay producción.
  - `civica-desarrollo.avance.org.co` es este ecosistema (`Avance/`).
  - `civica.avance.org.co` es **otro ecosistema de pruebas, paralelo**, con sus propias
    listas de confianza y su propia PKI.
- Por eso este ecosistema **no debe consumir las listas ni las anclas del otro**. No es por
  proteger producción: es para que los resultados de cada banco de pruebas no se mezclen
  (un ancla con el mismo CN pero distinto certificado es el fallo más difícil de detectar).
  El bloqueo de `civica.avance.org.co` en `/etc/hosts` y en `extra_hosts` se mantiene con
  esa justificación.
- Nota para después: varios comentarios del repo (`ECOSISTEMA.md`, `docker-compose` del
  tl-publisher, trust-validator, wallets) llaman "producción" a `civica.avance.org.co`.
  Conviene corregir ese texto para que diga "el otro ecosistema de pruebas".

## 1. Material de partida

| Origen | Qué aporta |
|---|---|
| `Avance/eudi-srv-verifier-local-build` | Verifier propio (`/verifier`), con su CA `Avance Verifier Access CA`, `generic-verifier` (Kotlin) y el `core` oficial |
| `wallet/verifier` (de un amigo) | Patrón multi-instancia: endpoint oficial v0.12.0 + UI oficial + haproxy, `levantar.sh <nombre>`, y las apps `banco` y `discoteca` con pruebas que hacen de wallet |
| `Avance/srv-tl-publisher` | Listas PID, PUBEAA, QEAA, RPREG, WP y LOTL; back-office; `pki-servicio` que emite `POST /pki/issue/rp-access` |
| `Avance/eudi-srv-trust-validator` | Validador de confianza |
| `Avance/app-new-wallet` | Wallet v2. WRPAC = `/tl/RPREG/current.json`, acepta `x509_san_dns` y `x509_hash`, chequeo de registro (WRPRC) con interruptor |

Estado de RPREG al momento del análisis (3 anclas):
`Civica RP Access CA`, `Civica Verifier Access CA`, `Avance Verifier Access CA`.

## 2. Ajustes necesarios sobre el código del amigo

| Tema | Su código | Este ecosistema |
|---|---|---|
| Dominio | `civica.avance.org.co` (`.env.example`, README, scripts) | `civica-desarrollo.avance.org.co` |
| Red y proxy | Red `civica` y un `../nginx` propio | Red `avance-eudi` y reglas nuevas en el `map` de `srv-nw-nginx/nginx.conf` |
| API del tl-publisher | `/api/v1/verifiers`, `integracion/verifier.env` | **Por confirmar** que existen aquí; si no, adaptar o añadir |
| Perfil del certificado | Exige EKU `1.0.18013.5.1.6` | El perfil `rp-access` solo lleva `clientAuth`; la hoja de `avance-verifier` no tiene EKU |
| Cookies | Cookie de sesión del banco | El borde público reescribe el `Path` a `/`: nombre de cookie único por app |
| Subdominios | — | Usar prefijos de ruta. `*.avance.org.co` no cubre `x.civica-desarrollo.avance.org.co` |
| PID | La discoteca necesita `age_equal_or_over` | Confirmar que lo emite `eudi-srv-issuer-pid-registraduria` (hoy `/pid-registraduria` responde 502) |

Discrepancias a aclarar antes de construir:
- El README de `generic-verifier` dice que es un esqueleto sin flujo de verificación,
  pero `ECOSISTEMA.md` lo da como operativo. Comprobar qué hay desplegado de verdad.
- Cuál de las 3 anclas de RPREG usa hoy `avance-verifier`.

## 3. CAs, certificados y dominio

No hace falta ninguna CA nueva.

1. **Verifiers nuevos**: certificado de acceso desde `Civica RP Access CA`, por
   `pki-servicio` (`/pki/issue/rp-access`). Esa CA ya es ancla de RPREG.
2. **Perfil `rp-access`**: añadir el EKU `1.0.18013.5.1.6` en `srv-tl-publisher/pki/perfiles.sh`
   y en `srv-tl-publisher/pki-servicio/emision.py`. Es un cambio de hoja, no de CA.
3. **`avance-verifier`** mantiene su CA propia; se le reemite la hoja con el EKU si la
   prueba con la wallet lo exige.
4. **`x5c` sin el ancla**: el keystore lleva solo la hoja. Incluir la CA produce
   `Untrusted x5c` en la wallet.
5. **WRPRC**: chequeo de registro apagado en la wallet v2 durante las pruebas. Cada verifier
   emite su `wrprc.jws` autofirmado. La lista WRPRC queda para una fase posterior.
6. **TLS**: el certificado `*.avance.org.co` del proxy público ya cubre el dominio.
7. **Dominio**: las URLs van dentro del XML firmado de las listas. Añadir un verifier implica
   republicar (nueva secuencia). Las listas caducan a las 24 h (`next_update`).

## 4. Estructura de `Avance/verifiers/`

```
verifiers/
├── docs/PLAN.md
├── _comun/        plantilla de compose, haproxy, nginx de la UI,
│                  generar-claves, registrar, probar-presentacion
├── banco/         PID -> ingresar / abrir cuenta (KYC)
├── discoteca/     age_equal_or_over.18, sin retención
├── contratacion/  credenciales académicas de la UIS (caso Avance)
├── up.sh · down.sh · estado.sh
└── README.md
```

Cada carpeta: compose propio, `.env.example`, README y pruebas, como los demás proyectos.
`eudi-srv-verifier-local-build` no se mueve. El código del amigo se copia y adapta; su
carpeta original no se edita.

## 5. Fases

| Fase | Qué se hace | Criterio de salida |
|---|---|---|
| 0. Línea base | `./up.sh`; confirmar Registraduría (hoy 502) y UIS; estado real de `avance-verifier`; confirmar la API del tl-publisher | `estado.sh` en verde y URLs de `ENLACES.md` con 200 |
| 1. PKI y confianza | EKU en `rp-access`; aclarar el ancla de `avance-verifier` | Certificado nuevo con EKU y `verificar-cadenas.py` en verde |
| 2. Plantilla común | Portar `_comun/` con dominio, red `avance-eudi` y reglas de `nginx.conf` | Instancia vacía en `/verifier-<n>/` registrada en RPREG |
| 3. Discoteca | Revisar `age_equal_or_over` en el PID | `probar-discoteca.sh` pasa |
| 4. Banco | Cookie única y redirección en el mismo dispositivo | `probar-banco.sh` pasa |
| 5. Contratación UIS | Credenciales académicas; clase `pubeaa` | Presentación de una credencial real de la UIS |
| 6. Wallet v2 en teléfono | APK con dominio y TL de desarrollo; QR y mismo dispositivo | Los tres verifiers aceptan una presentación desde la app |
| 7. Integración | Compose raíz, `up.sh`, `estado.sh`, panel, `ENLACES.md`, `ECOSISTEMA.md` | `./up.sh` levanta todo, verifiers incluidos |

## 6. Decisiones pendientes

1. Otros tipos de verifier además de banco, discoteca y contratación (por ejemplo notaría o
   EPS, de `wallet/notaria` y `wallet/eps`). Sin definirlo, el plan arranca con esos tres.
2. Endpoint oficial por instancia (recomendado para los ejemplos: ya funciona y está probado)
   o `generic-verifier` en Kotlin (línea aparte).
