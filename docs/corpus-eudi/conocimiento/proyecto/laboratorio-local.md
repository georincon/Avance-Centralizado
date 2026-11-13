# Laboratorio local del equipo (inventario de código propio y adaptado)

> Categoría: **EVIDENCIA-LOCAL**. Rutas fuera de este proyecto (solo lectura para el agente; no se copian ni se modifican desde aquí).
> Inventariado 2026-10-13 por listado de directorios y último commit; **no se ha revisado el código** (pendiente F1-05 del PLAN-MAESTRO).

| Ruta (bajo `/home/geovani/Descargas/generic/`) | Qué es | Último commit local | Relación con la arquitectura |
|---|---|---|---|
| `eudi-app-android-wallet-ui/` | Wallet Android (EUDI Wallet Prototype) adaptado ("avance credenciales movimiento") | 73dee798 · 2026-09-29 | C01 (app) sobre `eudi-lib-android-wallet-core` (versión 0.30.2 según la bitácora); usa `etsi-119602-consultation` y RQES UI |
| `eudi-generic-issuer-local-build/` | Emisor genérico (build local con Docker; botones sesión/volver) | 717cf40 · 2026-09-29 | C10/C21 (emisor + Keycloak); fixture de demostración a retirar (hallazgo bloqueante nº 9) |
| `eudi-srv-pid-issuer/` | Emisor PID de referencia (Kotlin) | 84819be4 · 2026-10-09 | C10 candidato; dos emisores EUDI examinados en la bitácora (genérico y PID) |
| `eudi-generic-verifier-local-build/` | Verificador genérico (UI, core, `UPSTREAM.md`) | — | C11 (verificador); hallazgos 1, 5, 6, 7 de §9.2 |
| `bitacora/new_proyect/` | Proyecto Kotlin/Ktor "Identidad Digital Soberana SSI": `did-core`, `did-resolver`, `vdr-service`, `did-tools`, `credentials-core`, `wallet-core` (simulado), `wallet-service` (prototipo del Wallet Backend), `credential-service`, `wallet-sim`; docker/nginx/mTLS; 130 pruebas y E2E 121 PASS (29-sep) | 0cd58ab · 2026-10-09 | O01/O02 (DID/VDR opcional) y banco de pruebas de D01; **no es** el D01 definitivo (Java/Spring Boot propuesto) |
| `bitacora/` (documentos) | Arquitectura v1.0, requerimiento original, informe de keystore Android, ERSo 001-008, imagen de arquitectura | — | Copiados/convertidos en `conocimiento/proyecto/` |
| `addi/` | `investigacion_lectura_cedula.pdf`, proyectos de onboarding | — | Posible insumo para la alta con documento (no revisado) |
| `postgres-uis/`, `veterinaria/`, `lissi/` | Soporte (PostgreSQL), caso de uso, capturas de Lissi | — | `lissi` son solo capturas de pantalla (bitácora 29-sep) |

## Estado según las bitácoras (sin re-verificar)
- ERSo 004-008 (VDR, did:web, DID institucional, consumidor, ciclo de vida): implementadas en laboratorio; 53 pruebas y 56 comprobaciones E2E (28-sep).
- ERSo 001-003 (cartera/custodia, suite de credenciales, DID del titular): implementadas con **custodio simulado**; 130 pruebas y 121 comprobaciones E2E (29-sep). Sin hardware seguro real, sin prueba cruzada con emisor/wallet EUDI.
- Desviaciones declaradas por el equipo: clave del emisor por DID (EUDI usa x5c); Base58/Multikey propios sin vectores oficiales; DID de ciudadano publicado vía Wallet Backend (riesgo de correlación); sin WIA, DPoP, JWE, mdoc de presentación ni revocación.
- **Tensión a resolver:** el laboratorio sigue la visión DID/VDR de las ERSo; la arquitectura v1.0 mueve el camino base a `CO-EUDI-PILOTO-1` (sin DID). Ver D-004.
